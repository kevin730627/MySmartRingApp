package com.mysmartring.ring

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 血壓 `0517`。數值取自 2026-10-24 13:00 的真機封包：收縮壓 114、舒張壓 74、心率 86 */
class BloodPressureParserTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val base = 800_000_000L

    private fun u32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** 一筆 8B：時間、狀態、收縮壓、舒張壓、心率 */
    private fun record(at: Long, systolic: Int, diastolic: Int, hr: Int, status: Int = 1) =
        u32(at) + byteArrayOf(status.toByte(), systolic.toByte(), diastolic.toByte(), hr.toByte())

    @Test
    fun `每筆產生收縮壓與舒張壓兩個 Reading，時間相同`() {
        val readings = Parsers.bloodPressure(record(base, 114, 74, 86), zone)

        assertEquals(listOf(Metric.BLOOD_PRESSURE_SYSTOLIC, Metric.BLOOD_PRESSURE_DIASTOLIC), readings.map { it.metric })
        assertEquals(listOf(114, 74), readings.map { it.value })
        assertEquals(1, readings.map { it.measuredAt }.toSet().size)
        assertEquals(RingTime.toInstant(base, zone), readings[0].measuredAt)
    }

    @Test
    fun `多筆依序解析`() {
        val stream = record(base, 114, 74, 86) + record(base + 600, 115, 75, 84) + record(base + 1200, 109, 74, 76)
        val readings = Parsers.bloodPressure(stream, zone)
        assertEquals(6, readings.size)
        assertEquals(listOf(114, 115, 109), readings.filter { it.metric == Metric.BLOOD_PRESSURE_SYSTOLIC }.map { it.value })
    }

    @Test
    fun `收縮壓或舒張壓為 0 的無效記錄略過`() {
        val stream = record(base, 0, 74, 86) + record(base + 600, 115, 0, 84) + record(base + 1200, 109, 74, 76)
        val readings = Parsers.bloodPressure(stream, zone)
        assertEquals(2, readings.size)
        assertEquals(RingTime.toInstant(base + 1200, zone), readings[0].measuredAt)
    }

    @Test
    fun `長度不是 8 的倍數時，Sync 記錄錯誤且不影響其他資料`() {
        val broken = record(base, 114, 74, 86) + byteArrayOf(1, 2, 3)
        val heartRate = u32(base) + byteArrayOf(1, 80)
        val decoded = SyncDecoder.decode(
            listOf(Frame(Commands.DATA_BLOOD_PRESSURE, broken).encode(), Frame(Commands.DATA_HEART_RATE, heartRate).encode()),
            zone,
        )
        assertTrue(decoded.errors.single().startsWith("血壓："))
        assertEquals(listOf(Metric.HEART_RATE), decoded.readings.map { it.metric })
    }

    @Test
    fun `Sync 解析出血壓 Reading`() {
        val decoded = SyncDecoder.decode(listOf(Frame(Commands.DATA_BLOOD_PRESSURE, record(base, 114, 74, 86)).encode()), zone)
        assertEquals(listOf(114, 74), decoded.readings.map { it.value })
        assertEquals(emptyList(), decoded.errors)
    }
}
