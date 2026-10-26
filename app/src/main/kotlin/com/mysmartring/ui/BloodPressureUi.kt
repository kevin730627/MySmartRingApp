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

/** 血壓卡片與詳細頁共用的虛擬 Metric 名稱，實際資料是收縮壓與舒張壓兩個 Metric */
internal const val BLOOD_PRESSURE = "BLOOD_PRESSURE"

private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

/** 戒指的血壓是由心率等訊號估算的，不是獨立量測，每個血壓畫面都要帶這句 */
private const val DISCLAIMER = "戒指估算值，非醫療用途"

@Composable
internal fun BloodPressureLatestCard(viewModel: DashboardViewModel, onClick: () -> Unit) {
    val latest by remember { viewModel.latestBloodPressure() }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text("血壓", style = MaterialTheme.typography.titleMedium)
            val sample = latest
            if (sample == null) {
                Text("尚無資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${sample.systolic}/${sample.diastolic}", style = MaterialTheme.typography.displayMedium)
                    Text("mmHg", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${DATE_TIME.format(Instant.ofEpochMilli(sample.measuredAtMillis))}・$DISCLAIMER",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun BloodPressureTrendCard(viewModel: DashboardViewModel, range: CardRange, onClick: () -> Unit) {
    val systolic by remember(range) { viewModel.lastDays(Metric.BLOOD_PRESSURE_SYSTOLIC, range.days) }.collectAsStateWithLifecycle(emptyList())
    val diastolic by remember(range) { viewModel.lastDays(Metric.BLOOD_PRESSURE_DIASTOLIC, range.days) }.collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("血壓・${if (range == CardRange.DAY) "今日" else "近 ${range.days} 天"}趨勢", style = MaterialTheme.typography.titleMedium)
            BloodPressureSection(systolic, diastolic, viewModel.rangeStart(range.days), range.days)
        }
    }
}

/** 兩條折線（上為收縮壓、下為舒張壓）加統計與免責說明 */
@Composable
internal fun BloodPressureSection(systolic: List<ReadingEntity>, diastolic: List<ReadingEntity>, startMillis: Long, days: Int) {
    if (systolic.isEmpty() || diastolic.isEmpty()) {
        Text("這段期間沒有資料", style = MaterialTheme.typography.bodyMedium)
        return
    }
    TrendChart(systolic, startMillis, days, second = diastolic)
    Text("上方線為收縮壓，下方線為舒張壓", style = MaterialTheme.typography.labelSmall)
    Text(
        "收縮壓 平均 ${systolic.map { it.value }.average().toInt()}　最高 ${systolic.maxOf { it.value }}　最低 ${systolic.minOf { it.value }}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "舒張壓 平均 ${diastolic.map { it.value }.average().toInt()}　最高 ${diastolic.maxOf { it.value }}　最低 ${diastolic.minOf { it.value }}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
}
