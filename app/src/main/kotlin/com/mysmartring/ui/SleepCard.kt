package com.mysmartring.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.ReadingEntity
import com.mysmartring.data.SleepNight
import com.mysmartring.ring.Metric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 階段圖由上到下的橫帶；「清醒時段」包含戒指回報的清醒片段，以及兩段睡眠之間的空檔 */
private enum class Lane(val label: String, val stage: String, val color: Color) {
    AWAKE("清醒時段", "AWAKE", Color(0xFFE57373)),
    REM("REM", "REM", Color(0xFF26A69A)),
    LIGHT("淺睡", "LIGHT", Color(0xFFFFA726)),
    DEEP("深睡", "DEEP", Color(0xFF3F51B5)),
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun SleepCard(viewModel: DashboardViewModel, onClick: () -> Unit) {
    val night by remember { viewModel.latestNight() }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("睡眠", style = MaterialTheme.typography.titleMedium)
            val n = night
            if (n == null) {
                Text("尚無資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                Summary(n)
                Hypnogram(n)
                SleepVitals(viewModel, n)
            }
        }
    }
}

@Composable
internal fun Summary(night: SleepNight) {
    val date = LocalDate.parse(night.wakeDate)
    Text("${date.monthValue}/${date.dayOfMonth} 醒來・共 ${night.sessions.size} 段", style = MaterialTheme.typography.bodySmall)
    Text(duration(night.sleepSeconds), style = MaterialTheme.typography.displaySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (lane in listOf(Lane.DEEP, Lane.LIGHT, Lane.REM)) {
            Stat(lane.label, minutes(night.stageSeconds(lane.stage)), lane.color)
        }
        Stat(Lane.AWAKE.label, minutes(night.awakeSeconds), Lane.AWAKE.color)
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun Hypnogram(night: SleepNight) {
    val lineColor = MaterialTheme.colorScheme.outline
    val totalMillis = (night.endAtMillis - night.startAtMillis).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val laneHeight = size.height / Lane.entries.size
        val barHeight = laneHeight * 0.6f
        val minBarWidth = 3.dp.toPx()
        fun x(millis: Long) = (millis - night.startAtMillis).toFloat() / totalMillis * size.width
        fun y(lane: Lane) = lane.ordinal * laneHeight + (laneHeight - barHeight) / 2

        // 依時間順序收集所有橫帶，相鄰兩條之間畫細線連接
        class Bar(val lane: Lane, val start: Long, val end: Long)
        val bars = buildList {
            night.sessions.forEachIndexed { i, s ->
                s.segments.forEach { seg ->
                    // 不認得的階段不畫
                    val lane = Lane.entries.firstOrNull { it.stage == seg.stage } ?: return@forEach
                    add(Bar(lane, seg.startAtMillis, seg.startAtMillis + seg.durationSeconds * 1000L))
                }
                night.awakeGaps.firstOrNull { it.startAtMillis == s.session.endAtMillis }?.let {
                    add(Bar(Lane.AWAKE, it.startAtMillis, it.endAtMillis))
                }
            }
        }
        bars.zipWithNext { a, b ->
            val cx = x(a.end).coerceAtMost(x(b.start))
            if (a.lane != b.lane) {
                val top = minOf(y(a.lane), y(b.lane)) + barHeight / 2
                val bottom = maxOf(y(a.lane), y(b.lane)) + barHeight / 2
                drawLine(lineColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx())
            }
        }
        bars.forEach {
            val left = x(it.start)
            val width = (x(it.end) - left).coerceAtLeast(minBarWidth)
            drawRoundRect(it.lane.color, Offset(left, y(it.lane)), Size(width, barHeight), CornerRadius(barHeight / 4))
        }
        // 淡淡的外框，標出時間軸範圍
        drawRect(lineColor.copy(alpha = 0.3f), style = Stroke(width = 1.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(CLOCK.format(Instant.ofEpochMilli(night.startAtMillis)), style = MaterialTheme.typography.bodySmall)
        Text(CLOCK.format(Instant.ofEpochMilli(night.endAtMillis)), style = MaterialTheme.typography.bodySmall)
    }
}

/** 睡眠期間（第一段入睡到最後一段醒來）的心率與血氧，橫軸與階段圖對齊 */
@Composable
internal fun SleepVitals(viewModel: DashboardViewModel, night: SleepNight) {
    val heart by remember(night.startAtMillis, night.endAtMillis) {
        viewModel.between(Metric.HEART_RATE, night.startAtMillis, night.endAtMillis + 1)
    }.collectAsStateWithLifecycle(emptyList())
    val oxygen by remember(night.startAtMillis, night.endAtMillis) {
        viewModel.between(Metric.BLOOD_OXYGEN, night.startAtMillis, night.endAtMillis + 1)
    }.collectAsStateWithLifecycle(emptyList())
    VitalStrip("心率", "bpm", heart, night, Color(0xFFE53935))
    VitalStrip("血氧", "%", oxygen, night, Color(0xFF1E88E5))
}

@Composable
private fun VitalStrip(label: String, unit: String, readings: List<ReadingEntity>, night: SleepNight, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        val values = readings.map { it.value }
        Text(
            if (values.isEmpty()) "睡眠期間沒有資料"
            else "平均 ${values.average().toInt()}　最低 ${values.min()}　最高 ${values.max()} $unit",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val frame = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val totalMillis = (night.endAtMillis - night.startAtMillis).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        drawRect(frame, style = Stroke(width = 1.dp.toPx()))
        if (readings.isEmpty()) return@Canvas
        // 縱軸依資料上下各留 2，且至少涵蓋 4 個單位，血氧變化很小時才不會畫成一條直線
        val low = readings.minOf { it.value } - 2f
        val high = maxOf(readings.maxOf { it.value } + 2f, low + 4f)
        val pad = 4.dp.toPx()
        fun x(millis: Long) = (millis - night.startAtMillis).toFloat() / totalMillis * size.width
        fun y(value: Int) = pad + (high - value) / (high - low) * (size.height - 2 * pad)
        val path = Path()
        readings.forEachIndexed { i, r ->
            if (i == 0) path.moveTo(x(r.measuredAtMillis), y(r.value)) else path.lineTo(x(r.measuredAtMillis), y(r.value))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        readings.forEach { drawCircle(color, radius = 2.dp.toPx(), center = Offset(x(it.measuredAtMillis), y(it.value))) }
    }
}

private fun duration(seconds: Long) = "${seconds / 3600} 小時 ${seconds % 3600 / 60} 分"
private fun minutes(seconds: Long) = "${seconds / 60} 分"
