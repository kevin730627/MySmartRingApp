package com.mysmartring.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysmartring.data.ReadingEntity
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_HOUR = DateTimeFormatter.ofPattern("M/d HH:mm")

/**
 * Reading 的折線圖。橫軸固定為 [startMillis] 起的 [days] 天，標籤精確到分鐘，
 * 縱軸依資料的最大最小值留白，不從 0 開始。
 */
@Composable
fun TrendChart(readings: List<ReadingEntity>, startMillis: Long, days: Int, modifier: Modifier = Modifier, second: List<ReadingEntity> = emptyList()) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(readings, second) {
        // Vico 的 x 值最多容許四位小數，取整數分鐘
        fun xs(list: List<ReadingEntity>) = list.map { ((it.measuredAtMillis - startMillis) / 60_000).toDouble() }
        producer.runTransaction {
            lineSeries {
                series(xs(readings), readings.map { it.value })
                // 第二條線（例如舒張壓）；空的序列 Vico 不接受，所以有資料才加
                if (second.isNotEmpty()) series(xs(second), second.map { it.value })
            }
        }
    }
    val zone = ZoneId.systemDefault()
    val formatter = if (days == 1) HOUR_MINUTE else DAY_HOUR
    val all = readings + second
    val minY = (all.minOf { it.value } - 5).toDouble()
    val maxY = (all.maxOf { it.value } + 5).toDouble()
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = days * 1440.0, minY = minY, maxY = maxY),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, value, _ ->
                    formatter.format(Instant.ofEpochMilli(startMillis + (value * 60_000).toLong()).atZone(zone))
                },
            ),
        ),
        modelProducer = producer,
        // 整個範圍縮放到剛好塞進圖表寬度，不橫向捲動
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
        modifier = modifier.fillMaxWidth().height(180.dp),
    )
}
