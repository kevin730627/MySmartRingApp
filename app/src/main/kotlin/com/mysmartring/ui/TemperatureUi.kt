package com.mysmartring.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.CardRange
import com.mysmartring.data.ReadingEntity
import com.mysmartring.ring.Metric
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 體溫以 0.1 °C 為單位存成整數，圖表與文字都要除以這個倍率 */
private const val SCALE = 10

private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

/** 戒指的體溫欄位是從數值推論的，尚未用體溫計對照，每個體溫畫面都要帶這句 */
private const val DISCLAIMER = "戒指量測的指溫，非醫療用途"

/** 368 → "36.8" */
internal fun formatTemperature(tenths: Int): String = "%.1f".format(tenths / SCALE.toDouble())

@Composable
internal fun TemperatureLatestCard(viewModel: DashboardViewModel, onClick: () -> Unit) {
    val latest by remember { viewModel.latest(Metric.TEMPERATURE) }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text("體溫", style = MaterialTheme.typography.titleMedium)
            val reading = latest
            if (reading == null) {
                Text("尚無資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatTemperature(reading.value), style = MaterialTheme.typography.displayMedium)
                    Text("°C", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${DATE_TIME.format(Instant.ofEpochMilli(reading.measuredAtMillis))}・$DISCLAIMER",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun TemperatureTrendCard(viewModel: DashboardViewModel, range: CardRange, onClick: () -> Unit) {
    val readings by remember(range) { viewModel.lastDays(Metric.TEMPERATURE, range.days) }.collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("體溫・${if (range == CardRange.DAY) "今日" else "近 ${range.days} 天"}趨勢", style = MaterialTheme.typography.titleMedium)
            TemperatureSection(readings, viewModel.rangeStart(range.days), range.days)
        }
    }
}

/** 折線圖加平均、最高、最低與說明 */
@Composable
internal fun TemperatureSection(readings: List<ReadingEntity>, startMillis: Long, days: Int) {
    if (readings.isEmpty()) {
        Text("這段期間沒有資料", style = MaterialTheme.typography.bodyMedium)
        return
    }
    TrendChart(readings, startMillis, days, valueScale = SCALE)
    val values = readings.map { it.value }
    Text(
        "${readings.size} 筆　平均 ${formatTemperature(values.average().toInt())}　最高 ${formatTemperature(values.max())}　最低 ${formatTemperature(values.min())}（°C）",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(DISCLAIMER, style = MaterialTheme.typography.labelSmall)
}
