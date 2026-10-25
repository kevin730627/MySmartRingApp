package com.mysmartring.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.CardConfig
import com.mysmartring.data.CardRange
import com.mysmartring.data.CardType
import com.mysmartring.ring.Metric
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

internal class MetricSpec(val metric: Metric, val label: String, val unit: String)

private val METRICS = listOf(
    MetricSpec(Metric.HEART_RATE, "心率", "bpm"),
    MetricSpec(Metric.BLOOD_OXYGEN, "血氧", "%"),
)

internal fun metricSpec(name: String): MetricSpec? = METRICS.firstOrNull { it.metric.name == name }

/** 設定頁列出 Card 時用的名稱，例如「心率・趨勢（週）」 */
internal fun cardTitle(card: CardConfig): String = when (card.type) {
    CardType.SLEEP -> "睡眠"
    CardType.LATEST -> "${metricSpec(card.metric)?.label ?: card.metric}・最新值"
    CardType.TREND -> "${metricSpec(card.metric)?.label ?: card.metric}・趨勢（${card.range.label}）"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenDetail: (String) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    // 開啟 app 且有權限時自動同步一次
    LaunchedEffect(granted) { if (granted) viewModel.sync() }
    // 從藍牙設定回來時，若剛才因為藍牙未開啟而沒同步，就再試一次
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (granted && syncState is SyncUiState.BluetoothOff) viewModel.sync() }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("MySmartRing") }, actions = { TextButton(onClick = onOpenSettings) { Text("設定") } }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = syncState is SyncUiState.Working,
            onRefresh = { if (granted) viewModel.sync() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StatusCard(
                        granted, syncState,
                        onGrant = { requestPermissions.launch(PERMISSIONS) },
                        onPair = viewModel::pair,
                        onOpenBluetooth = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    )
                }
                itemsIndexed(cards.orEmpty()) { _, card ->
                    val spec = metricSpec(card.metric)
                    when {
                        card.type == CardType.SLEEP -> SleepCard(viewModel, onClick = { onOpenDetail("SLEEP") })
                        spec == null -> Unit
                        card.type == CardType.LATEST -> LatestCard(viewModel, spec, onClick = { onOpenDetail(card.metric) })
                        else -> TrendCard(viewModel, spec, card.range, onClick = { onOpenDetail(card.metric) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(granted: Boolean, state: SyncUiState, onGrant: () -> Unit, onPair: () -> Unit, onOpenBluetooth: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !granted -> {
                    Text("需要藍牙權限才能連線到戒指")
                    Button(onClick = onGrant) { Text("授權") }
                }
                state is SyncUiState.BluetoothOff -> {
                    Text("藍牙未開啟，請先開啟藍牙才能同步。開啟後回到本畫面會自動同步。")
                    Button(onClick = onOpenBluetooth) { Text("開啟藍牙") }
                }
                state is SyncUiState.NotPaired -> {
                    Text("尚未配對戒指。請先從 FixFate 解除綁定，並讓戒指靠近手機。")
                    Button(onClick = onPair) { Text("配對戒指") }
                }
                state is SyncUiState.Working -> Text(state.message)
                state is SyncUiState.Done -> Text("已同步 ${TIME.format(state.finishedAt)}，下拉可重新同步")
                state is SyncUiState.Failed -> {
                    Text("同步失敗：${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = onPair) { Text("重新配對") }
                }
                else -> Text("下拉以同步")
            }
        }
    }
}

@Composable
private fun LatestCard(viewModel: DashboardViewModel, spec: MetricSpec, onClick: () -> Unit) {
    val latest by remember(spec) { viewModel.latest(spec.metric) }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(spec.label, style = MaterialTheme.typography.titleMedium)
            val reading = latest
            if (reading == null) {
                Text("尚無資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${reading.value}", style = MaterialTheme.typography.displayMedium)
                    Text(spec.unit, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    DATE_TIME.format(Instant.ofEpochMilli(reading.measuredAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(viewModel: DashboardViewModel, spec: MetricSpec, range: CardRange, onClick: () -> Unit) {
    val readings by remember(spec, range) { viewModel.lastDays(spec.metric, range.days) }.collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${spec.label}・${if (range == CardRange.DAY) "今日" else "近 ${range.days} 天"}趨勢", style = MaterialTheme.typography.titleMedium)
            if (readings.isEmpty()) {
                Text("這段期間沒有資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                TrendChart(readings, viewModel.rangeStart(range.days), range.days)
                val values = readings.map { it.value }
                Text(
                    "平均 ${values.average().toInt()}　最高 ${values.max()}　最低 ${values.min()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
