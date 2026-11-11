package com.mysmartring.ring

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 體溫（`0518` 第 13、14 位元組）與電量（`0200` 回應）。數值取自 2026-10-24 的真機側錄 */
class TemperatureBatteryParserTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val base = 800_000_000L

    private fun u32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** 一筆 20B：時間、[66 01]、心率、收縮壓、舒張壓、血氧、00、[?]、十分位前的 2 個位元組、整數度、十分位 … */
    private fun record(at: Long, degrees: Int, tenths: Int) =
        u32(at) + byteArrayOf(0x66, 0x01, 83, 112, 76, 98, 0, 31, 2, degrees.toByte(), tenths.toByte(), 0, 0, 0, 0, 0)

    @Test
    fun `體溫為整數度加十分位，以 0_1 度為單位存成整數`() {
        val temperature = Parsers.spotMeasurement(record(base, 36, 8), zone).single { it.metric == Metric.TEMPERATURE }
        assertEquals(368, temperature.value)
        assertEquals(RingTime.toInstant(base, zone), temperature.measuredAt)
    }

    @Test
    fun `整數度不合理或十分位超過 9 的記錄不產生體溫，其他數值照常`() {
        val stream = record(base, 0, 0) + record(base + 600, 36, 12) + record(base + 1200, 36, 5)
        val readings = Parsers.spotMeasurement(stream, zone)
        assertEquals(listOf(365), readings.filter { it.metric == Metric.TEMPERATURE }.map { it.value })
        assertEquals(3, readings.count { it.metric == Metric.HEART_RATE })
    }

    @Test
    fun `電量取自狀態回應的第 6 個位元組`() {
        val frame = Frame.decode(("02001e00a300070000620001000300000000000000000100000000008339").hexToBytes())
        assertEquals(98, Parsers.batteryPercent(frame))
    }

    @Test
    fun `電量超過 100 或回應太短視為解析失敗`() {
        assertFailsWith<ProtocolException> { Parsers.batteryPercent(Frame(Commands.GET_STATUS, byteArrayOf(0, 0, 0, 0, 0, 101))) }
        assertFailsWith<ProtocolException> { Parsers.batteryPercent(Frame(Commands.GET_STATUS, byteArrayOf(0, 0))) }
    }

    @Test
    fun `電量查詢與側錄中 app 送出的位元組一致`() {
        assertEquals("0200080047436fec", Commands.statusQuery().encode().toHex())
    }

    @Test
    fun `真機側錄的體溫都落在人體指溫的合理範圍`() {
        val lines = javaClass.getResourceAsStream("/capture-2026-10-24.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
            .map { it.split(" ").let { (dir, hex) -> dir to hex.hexToBytes() } }
        val frames = lines.filter { it.first == "RX" }.map { Frame.decode(it.second) }
        val temperatures = Parsers.spotMeasurement(Parsers.stream(frames, Commands.DATA_SPOT_MEASUREMENT), zone)
            .filter { it.metric == Metric.TEMPERATURE }
        assertEquals(125, temperatures.size)
        assertTrue(temperatures.all { it.value in 350..375 }, "體溫超出 35.0–37.5：${temperatures.map { it.value }.distinct().sorted()}")
    }

    @Test
    fun `SyncDecoder 從側錄還原出電量與體溫`() {
        val raw = javaClass.getResourceAsStream("/capture-2026-10-24.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
            .map { it.split(" ") }.filter { it[0] == "RX" }.map { it[1].hexToBytes() }
        val decoded = SyncDecoder.decode(raw, zone)
        assertEquals(emptyList(), decoded.errors)
        assertEquals(98, decoded.batteryPercent)
        assertTrue(decoded.readings.any { it.metric == Metric.TEMPERATURE })
    }
}
