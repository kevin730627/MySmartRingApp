package com.mysmartring.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mysmartring.AppContainer
import com.mysmartring.ble.SyncRunner
import com.mysmartring.data.PairedRingEntity
import com.mysmartring.data.ReadingEntity
import com.mysmartring.ring.Metric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object NotPaired : SyncUiState
    data class Working(val message: String) : SyncUiState
    data class Done(val finishedAt: Instant) : SyncUiState
    data class Failed(val message: String) : SyncUiState
}

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState

    private var job: Job? = null
    private val readings get() = container.database.readingDao()

    fun latest(metric: Metric): Flow<ReadingEntity?> = readings.latest(metric.name)

    /** 今天（手機時區）的 Reading，資料庫有變動就會重新送出 */
    fun today(metric: Metric): Flow<List<ReadingEntity>> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone)
        return readings.observeBetween(metric.name, start.toInstant().toEpochMilli(), start.plusDays(1).toInstant().toEpochMilli())
    }

    /** 已配對就同步，否則提示要先配對 */
    fun sync() = launchExclusive {
        val ring = container.database.pairedRingDao().get()
        if (ring == null) _syncState.value = SyncUiState.NotPaired else syncWith(ring)
    }

    fun pair() = launchExclusive {
        _syncState.value = SyncUiState.Working("掃描中，請讓戒指靠近手機…")
        val found = withTimeout(SCAN_TIMEOUT_MS) { container.transport.scan().first() }
        val ring = PairedRingEntity(macAddress = found.macAddress, name = found.name, pairedAtMillis = System.currentTimeMillis())
        container.database.pairedRingDao().save(ring)
        syncWith(ring)
    }

    private suspend fun syncWith(ring: PairedRingEntity) {
        _syncState.value = SyncUiState.Working("連線 ${ring.name}…")
        val startedAt = Instant.now()
        container.transport.connect(ring.macAddress).use { connection ->
            _syncState.value = SyncUiState.Working("同步中…")
            SyncRunner(connection).run { frames ->
                container.repository.ingest(startedAt, ZoneId.systemDefault(), frames)
            }
        }
        _syncState.value = SyncUiState.Done(Instant.now())
    }

    /** 同時只跑一個連線動作，執行中再觸發會被忽略 */
    private fun launchExclusive(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: TimeoutCancellationException) {
                _syncState.value = SyncUiState.Failed("找不到戒指，請確認戒指在附近且已從 FixFate 解除綁定")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _syncState.value = SyncUiState.Failed("${e::class.java.simpleName}：${e.message}")
            }
        }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(container) }
        }
    }
}
