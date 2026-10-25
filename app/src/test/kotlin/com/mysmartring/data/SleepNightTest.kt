package com.mysmartring.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SleepNightTest {
    private fun at(h: Int, m: Int, s: Int = 0) = ((h * 60L + m) * 60 + s) * 1000

    private fun session(id: Long, start: Long, end: Long, vararg segs: Triple<String, Long, Int>) =
        SessionWithSegments(
            SleepSessionEntity(id = id, startAtMillis = start, endAtMillis = end, wakeDate = "2026-10-25", summary = ByteArray(8), syncId = 1),
            segs.map { SleepSegmentEntity(sessionId = id, stage = it.first, startAtMillis = it.second, durationSeconds = it.third) },
        )

    /** 仿真機資料：一晚被戒指切成相隔 3 分鐘的兩段 */
    private val first = session(
        1, at(0, 54), at(3, 20),
        Triple("LIGHT", at(0, 54), 3600), Triple("DEEP", at(1, 54), 1800), Triple("REM", at(2, 24), 3360),
    )
    private val second = session(
        2, at(3, 23), at(9, 5),
        Triple("LIGHT", at(3, 23), 7200), Triple("DEEP", at(5, 23), 3600), Triple("LIGHT", at(6, 23), 9000),
    )

    @Test
    fun `分段睡眠不合併，保留各段`() {
        val night = SleepNight.of("2026-10-25", listOf(first, second))!!
        assertEquals(2, night.sessions.size)
        assertEquals(at(0, 54), night.startAtMillis)
        assertEquals(at(9, 5), night.endAtMillis)
    }

    @Test
    fun `兩段之間的空檔是清醒時段`() {
        val night = SleepNight.of("2026-10-25", listOf(first, second))!!
        assertEquals(listOf(AwakeGap(at(3, 20), at(3, 23))), night.awakeGaps)
        assertEquals(180, night.awakeSeconds)
    }

    @Test
    fun `各階段秒數跨段加總`() {
        val night = SleepNight.of("2026-10-25", listOf(first, second))!!
        assertEquals(3600L + 7200 + 9000, night.stageSeconds("LIGHT"))
        assertEquals(1800L + 3600, night.stageSeconds("DEEP"))
        assertEquals(3360L, night.stageSeconds("REM"))
        assertEquals(night.stageSeconds("LIGHT") + night.stageSeconds("DEEP") + night.stageSeconds("REM"), night.sleepSeconds)
    }

    @Test
    fun `清醒時段不計入睡眠時間`() {
        val night = SleepNight.of("2026-10-25", listOf(first, second))!!
        assertEquals(true, night.sleepSeconds + night.awakeSeconds <= (night.endAtMillis - night.startAtMillis) / 1000)
    }

    @Test
    fun `輸入順序顛倒也依入睡時間排序`() {
        val night = SleepNight.of("2026-10-25", listOf(second, first))!!
        assertEquals(listOf(1L, 2L), night.sessions.map { it.session.id })
        assertEquals(180, night.awakeSeconds)
    }

    @Test
    fun `只有一段時沒有清醒時段`() {
        val night = SleepNight.of("2026-10-25", listOf(first))!!
        assertEquals(emptyList(), night.awakeGaps)
        assertEquals(0, night.awakeSeconds)
    }

    @Test
    fun `首尾相接的兩段沒有清醒時段`() {
        val adjacent = session(2, at(3, 20), at(4, 0), Triple("LIGHT", at(3, 20), 2400))
        assertEquals(emptyList(), SleepNight.of("2026-10-25", listOf(first, adjacent))!!.awakeGaps)
    }

    @Test
    fun `沒有 Session 時沒有這一晚`() {
        assertNull(SleepNight.of("2026-10-25", emptyList()))
    }
}
