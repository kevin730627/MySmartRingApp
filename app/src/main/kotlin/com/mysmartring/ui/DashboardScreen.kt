package com.mysmartring.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.mysmartring.data.ReadingEntity
import com.mysmartring.ring.Metric
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

/** 儀表板上顯示的 Metric 設定；之後改由 dashboard_card 資料表決定 */
private class MetricSpec(val metric: Metric, val label: String, val unit: String)

private val METRICS = listOf(
    MetricSpec(Metric.HEART_RATE, "心率", "bpm"),
    MetricSpec(Metric.BLOOD_OXYGEN, "血氧", "%"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    // 開啟 app 且有權限時自動同步一次
    LaunchedEffect(granted) { if (granted) viewModel.sync() }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("MySmartRing") }) }) { padding ->
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
                item { StatusCard(granted, syncState, onGrant = { requestPermissions.launch(PERMISSIONS) }, onPair = viewModel::pair) }
                items(METRICS) { spec ->
                    LatestCard(viewModel, spec)
                    TrendCard(viewModel, spec)
                }
                item { SleepCard(viewModel) }
            }
        }
    }
}

@Composable
private fun StatusCard(granted: Boolean, state: SyncUiState, onGrant: () -> Unit, onPair: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !granted -> {
                    Text("需要藍牙權限才能連線到戒指")
                    Button(onClick = onGrant) { Text("授權") }
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
private fun LatestCard(viewModel: DashboardViewModel, spec: MetricSpec) {
    val latest by remember(spec) { viewModel.latest(spec.metric) }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth()) {
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
private fun TrendCard(viewModel: DashboardViewModel, spec: MetricSpec) {
    val readings by remember(spec) { viewModel.today(spec.metric) }.collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${spec.label}・今日趨勢", style = MaterialTheme.typography.titleMedium)
            if (readings.isEmpty()) {
                Text("今天還沒有資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                TrendChart(readings)
                val values = readings.map { it.value }
                Text(
                    "平均 ${values.average().toInt()}　最高 ${values.max()}　最低 ${values.min()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrendChart(readings: List<ReadingEntity>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(readings) {
        val zone = ZoneId.systemDefault()
        // 橫軸為當天的小時數（含小數）
        val xs = readings.map {
            val t = Instant.ofEpochMilli(it.measuredAtMillis).atZone(zone)
            t.hour + t.minute / 60.0
        }
        producer.runTransaction { lineSeries { series(xs, readings.map { it.value }) } }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, value, _ -> "%02d:00".format(value.toInt()) },
            ),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(180.dp),
    )
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
