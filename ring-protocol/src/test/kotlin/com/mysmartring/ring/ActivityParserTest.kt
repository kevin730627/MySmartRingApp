package com.mysmartring.ring

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 活動 `0511`。三個 u16 取自 2026-10-24 與 10-25 的真機封包 */
class ActivityParserTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val base = 800_000_000L

    private fun u32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** 一筆 14B：起始、結束、再接 6B 的 [fields]（三個 u16 的十六進位） */
    private fun record(start: Long, fields: String) = u32(start) + u32(start + 1800) + fields.hexToBytes()

    @Test
    fun `解析步數、距離與熱量`() {
        val stream = record(base, "c902c4011c00") + record(base + 1800, "4d0600044100")

        val records = Parsers.activity(stream, zone)

        assertEquals(2, records.size)
        assertEquals(713, records[0].steps)
        assertEquals(452, records[0].distanceMeters)
        assertEquals(28, records[0].kcal)
        assertEquals(1613, records[1].steps)
        assertEquals(RingTime.toInstant(base, zone), records[0].startAt)
        assertEquals(RingTime.toInstant(base + 1800, zone), records[0].endAt)
    }

    @Test
    fun `三個欄位的比例在真機樣本中一致`() {
        // 距離約為步數的 0.63 倍，熱量約為步數的 1/25；這是判斷欄位意義的依據
        val samples = listOf("650040000400", "5300 3400 0300", "c902c4011c00", "4d0600044100", "4c0b2c077500", "8c0340022400")
            .map { Parsers.activity(record(base, it.replace(" ", "")), zone).single() }
        for (r in samples) {
            assertEquals(0.635, r.distanceMeters.toDouble() / r.steps, 0.01)
            assertEquals(0.04, r.kcal.toDouble() / r.steps, 0.005)
        }
    }

    @Test
    fun `長度不是 14 的倍數時視為解析失敗`() {
        val stream = record(base, "c902c4011c00") + byteArrayOf(1, 2, 3)
        val decoded = SyncDecoder.decode(listOf(Frame(Commands.DATA_ACTIVITY, stream).encode()), zone)
        assertEquals(0, decoded.readings.size)
        assertTrue(decoded.errors.single().startsWith("活動："))
    }

    @Test
    fun `Sync 把步數轉成以區間起點為時間的 Reading，0 步的區間略過`() {
        val stream = record(base, "c902c4011c00") + record(base + 1800, "000000000000") + record(base + 3600, "0b0006000000")
        val decoded = SyncDecoder.decode(listOf(Frame(Commands.DATA_ACTIVITY, stream).encode()), zone)

        val steps = decoded.readings.filter { it.metric == Metric.STEPS }
        assertEquals(listOf(713, 11), steps.map { it.value })
        assertEquals(RingTime.toInstant(base, zone), steps[0].measuredAt)
        assertEquals(RingTime.toInstant(base + 3600, zone), steps[1].measuredAt)
        assertEquals(emptyList(), decoded.errors)
    }
}
