package com.mysmartring.ring

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 以 2026-10-24 的 HCI 側錄回放。資源檔每行 `TX|RX <十六進位>`，
 * 為戒指 handle 0x0010 / 0x0015 上依序出現的訊框。
 */
class CaptureReplayTest {
    private val zone = ZoneId.of("Asia/Taipei")

    private val lines = javaClass.getResourceAsStream("/capture-2026-10-24.txt")!!
        .bufferedReader().readLines().filter { it.isNotBlank() }
        .map { it.split(" ").let { (dir, hex) -> dir to hex.hexToBytes() } }

    private val tx = lines.filter { it.first == "TX" }.map { Frame.decode(it.second) }
    private val rx = lines.filter { it.first == "RX" }.map { Frame.decode(it.second) }

    private fun countOf(command: Int) = rx.filter { it.command == command }
        .map(Parsers::countResponse).first { it.recordCount > 0 }

    @Test
    fun `所有訊框的長度與 CRC 都正確`() {
        // decode 失敗會丟 ProtocolException；能走到這裡就代表全部通過
        assertTrue(tx.isNotEmpty() && rx.isNotEmpty())
    }

    @Test
    fun `編碼結果與側錄中 app 送出的位元組一致`() {
        val sent = lines.filter { it.first == "TX" }.map { it.second.toHex() }.toSet()
        assertTrue(Commands.setTime(LocalDateTime.of(2026, 10, 24, 11, 33, 25)).encode().toHex() in sent)
        for (q in listOf(
            Commands.COUNT_ACTIVITY, Commands.COUNT_SLEEP, Commands.COUNT_HEART_RATE,
            Commands.COUNT_BLOOD_PRESSURE, Commands.COUNT_SPOT_MEASUREMENT,
        )) {
            assertTrue(Commands.query(q).encode().toHex() in sent, "查詢 0x%04x".format(q))
        }
        assertTrue(Commands.ack().encode().toHex() in sent)
        for (c in listOf(
            Commands.CLEAR_ACTIVITY, Commands.CLEAR_SLEEP, Commands.CLEAR_HEART_RATE,
            Commands.CLEAR_BLOOD_PRESSURE, Commands.CLEAR_SPOT_MEASUREMENT,
        )) {
            assertTrue(Commands.clear(c).encode().toHex() in sent, "清除 0x%04x".format(c))
        }
    }

    @Test
    fun `心率筆數與戒指回報的筆數一致`() {
        val count = countOf(Commands.COUNT_HEART_RATE)
        val stream = Parsers.stream(rx, Commands.DATA_HEART_RATE)
        assertEquals(count.totalBytes, stream.size.toLong())
        val readings = Parsers.heartRate(stream, zone)
        assertEquals(count.recordCount, readings.size)
        assertTrue(readings.all { it.value in 30..220 }, "心率超出合理範圍")
    }

    @Test
    fun `複合量測筆數一致且血氧合理`() {
        val count = countOf(Commands.COUNT_SPOT_MEASUREMENT)
        val stream = Parsers.stream(rx, Commands.DATA_SPOT_MEASUREMENT)
        assertEquals(count.totalBytes, stream.size.toLong())
        val readings = Parsers.spotMeasurement(stream, zone)
        val spo2 = readings.filter { it.metric == Metric.BLOOD_OXYGEN }
        assertTrue(spo2.isNotEmpty())
        assertTrue(spo2.all { it.value in 70..100 }, "血氧超出合理範圍")
    }

    @Test
    fun `睡眠階段與 FixFate 畫面一致`() {
        val stream = Parsers.stream(rx, Commands.DATA_SLEEP)
        assertEquals(countOf(Commands.COUNT_SLEEP).totalBytes, stream.size.toLong())
        val session = Parsers.sleep(stream, zone).single()

        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 16, 39), LocalDateTime.ofInstant(session.startAt, zone))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 4, 54), LocalDateTime.ofInstant(session.endAt, zone))
        assertEquals(48, session.segments.size)

        // 深睡：FixFate 顯示 84 分鐘，封包加總 85.8 分鐘
        val deepMinutes = session.segments.filter { it.stage == SleepStage.DEEP }.sumOf { it.durationSeconds } / 60.0
        assertEquals(84.0, deepMinutes, 2.0)

        // 階段片段加總約等於入睡到醒來（實測差 18 秒，來自各片段的取整）
        val total = session.segments.sumOf { it.durationSeconds }
        val span = session.endAt.epochSecond - session.startAt.epochSecond
        assertEquals(span.toDouble(), total.toDouble(), 60.0)
    }

    @Test
    fun `重建是冪等的`() {
        val stream = Parsers.stream(rx, Commands.DATA_HEART_RATE)
        assertEquals(Parsers.heartRate(stream, zone), Parsers.heartRate(stream, zone))
    }

    @Test
    fun `戒指時間以時區換算為 UTC 瞬間`() {
        val seconds = 0x326E1DF7L // 2026-10-23 13:30:31 戒指本地時間
        assertEquals(LocalDateTime.of(2026, 10, 23, 5, 30, 31).toInstant(ZoneOffset.UTC), RingTime.toInstant(seconds, zone))
    }
}
