package com.mysmartring.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.SleepNight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 階段圖由上到下的橫帶；戒指沒有量到清醒，這裡的「清醒時段」是兩段睡眠之間的空檔 */
private enum class Lane(val label: String, val stage: String?, val color: Color) {
    AWAKE("清醒時段", null, Color(0xFFE57373)),
    REM("REM", "REM", Color(0xFF26A69A)),
    LIGHT("淺睡", "LIGHT", Color(0xFFFFA726)),
    DEEP("深睡", "DEEP", Color(0xFF3F51B5)),
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun SleepCard(viewModel: DashboardViewModel) {
    val night by remember { viewModel.latestNight() }.collectAsStateWithLifecycle(null)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("睡眠", style = MaterialTheme.typography.titleMedium)
            val n = night
            if (n == null) {
                Text("尚無資料", style = MaterialTheme.typography.bodyMedium)
            } else {
                Summary(n)
                Hypnogram(n)
            }
        }
    }
}

@Composable
private fun Summary(night: SleepNight) {
    val date = LocalDate.parse(night.wakeDate)
    Text("${date.monthValue}/${date.dayOfMonth} 醒來・共 ${night.sessions.size} 段", style = MaterialTheme.typography.bodySmall)
    Text(duration(night.sleepSeconds), style = MaterialTheme.typography.displaySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (lane in listOf(Lane.DEEP, Lane.LIGHT, Lane.REM)) {
            Stat(lane.label, minutes(night.stageSeconds(lane.stage!!)), lane.color)
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
private fun Hypnogram(night: SleepNight) {
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
                    val lane = Lane.entries.first { it.stage == seg.stage }
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

private fun duration(seconds: Long) = "${seconds / 3600} 小時 ${seconds % 3600 / 60} 分"
private fun minutes(seconds: Long) = "${seconds / 60} 分"
