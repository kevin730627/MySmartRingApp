package com.mysmartring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.ring.Metric

private class RangeOption(val label: String, val days: Int)

private val RANGES = listOf(RangeOption("日", 1), RangeOption("週", 7), RangeOption("月", 30))

/** [key] 是 Metric 名稱，或 "SLEEP" */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(key: String, viewModel: DashboardViewModel, onBack: () -> Unit) {
    val title = when (key) {
        Metric.HEART_RATE.name -> "心率"
        Metric.BLOOD_OXYGEN.name -> "血氧"
        else -> "睡眠"
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            if (key == "SLEEP") SleepList(viewModel) else MetricDetail(Metric.valueOf(key), title, viewModel)
        }
    }
}

@Composable
private fun MetricDetail(metric: Metric, label: String, viewModel: DashboardViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val range = RANGES[selected]
    val readings by remember(metric, range) { viewModel.lastDays(metric, range.days) }.collectAsStateWithLifecycle(emptyList())
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RANGES.forEachIndexed { i, r -> FilterChip(selected = i == selected, onClick = { selected = i }, label = { Text(r.label) }) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (readings.isEmpty()) {
                Text("這段期間沒有資料")
            } else {
                TrendChart(readings, viewModel.rangeStart(range.days), range.days)
                val values = readings.map { it.value }
                Text("$label・${readings.size} 筆　平均 ${values.average().toInt()}　最高 ${values.max()}　最低 ${values.min()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SleepList(viewModel: DashboardViewModel) {
    val nights by remember { viewModel.nights() }.collectAsStateWithLifecycle(emptyList())
    if (nights.isEmpty()) {
        Text("尚無資料", Modifier.padding(top = 16.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        items(nights, key = { it.wakeDate }) { night ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Summary(night)
                    Hypnogram(night)
                    SleepVitals(viewModel, night)
                }
            }
        }
    }
}
