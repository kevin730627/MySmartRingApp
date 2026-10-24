package com.mysmartring

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mysmartring.ble.SyncRunner
import com.mysmartring.data.PairedRingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId

/** 只在 debug 版本存在的暫時畫面：授權、配對、同步，並顯示結果 */
class DebugActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private lateinit var output: TextView
    private val container get() = (application as RingApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 13f; setPadding(24, 24, 24, 24) }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("授權") { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 1) })
            addView(button("配對") { launch { pair() } })
            addView(button("同步") { launch { sync() } })
            addView(button("統計") { launch { stats() } })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 避開 edge-to-edge 下的標題列
            setPadding(0, 300, 0, 0)
            addView(buttons)
            addView(ScrollView(this@DebugActivity).apply { addView(output) }, ViewGroup.LayoutParams(-1, -1))
        })
        log("權限：${if (hasPermissions()) "已授權" else "尚未授權，請按「授權」"}")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun button(text: String, onClick: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { onClick() } }

    private fun hasPermissions() =
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        Log.i(TAG, message)
        output.append(message + "\n")
    }

    private fun launch(block: suspend () -> Unit) {
        if (!hasPermissions()) return log("請先授權藍牙權限")
        job?.cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "失敗", e)
                log("失敗：${e::class.java.simpleName}：${e.message}")
            }
        }
    }

    private suspend fun pair() {
        log("掃描中（15 秒），請讓戒指靠近手機…")
        val found = withTimeout(15_000) { container.transport.scan().first() }
        log("找到 ${found.name}（${found.macAddress}，訊號 ${found.rssi}）")
        container.database.pairedRingDao().save(PairedRingEntity(macAddress = found.macAddress, name = found.name, pairedAtMillis = System.currentTimeMillis()))
        log("已記住這枚戒指")
    }

    private suspend fun sync() {
        val ring = container.database.pairedRingDao().get() ?: return log("尚未配對")
        log("連線 ${ring.name}…")
        val startedAt = Instant.now()
        container.transport.connect(ring.macAddress).use { connection ->
            log("已連線，開始同步")
            val report = SyncRunner(connection).run { frames ->
                container.repository.ingest(startedAt, ZoneId.systemDefault(), frames)
                log("已寫入 ${frames.size} 個 Raw Frame，接著清除戒指上的資料")
            }
            log("收到：${report.fetched}")
            log("已清除：${report.cleared}")
        }
        stats()
    }

    private suspend fun stats() {
        val db = container.database
        log("— Raw Frame ${db.rawFrameDao().count()}、Reading ${db.readingDao().count()}、睡眠 ${db.sleepDao().sessions().size}")
        db.syncDao().all().lastOrNull()?.let { log("— 最近一次 Sync：${it.status} ${it.errors}") }
    }

    private companion object {
        const val TAG = "MySmartRing"
    }
}
