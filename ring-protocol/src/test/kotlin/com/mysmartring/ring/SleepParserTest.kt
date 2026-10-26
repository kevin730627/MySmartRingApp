package com.mysmartring.ring

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 以合成的睡眠串流測試階段碼與多段紀錄；形狀取自 2026-10-26 真機收到的串流 */
class SleepParserTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val base = 800_000_000L

    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun u32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** 一筆睡眠紀錄；[segments] 為（階段碼, 起始秒, 持續秒數） */
    private fun record(start: Long, end: Long, vararg segments: Triple<Int, Long, Int>): ByteArray {
        val body = segments.fold(ByteArray(0)) { acc, (code, at, dur) -> acc + byteArrayOf(code.toByte()) + u32(at) + u16(dur) + byteArrayOf(0) }
        val length = 20 + body.size
        return byteArrayOf(0xAF.toByte(), 0xFA.toByte()) + u16(length) + u32(start) + u32(end) + ByteArray(8) + body
    }

    @Test
    fun `0xF4 解析為清醒階段，不產生警告`() {
        val warnings = mutableListOf<String>()
        val stream = record(base, base + 600, Triple(0xF2, base, 465), Triple(0xF4, base + 465, 135))

        val session = Parsers.sleep(stream, zone) { warnings += it }.single()

        assertEquals(listOf(SleepStage.LIGHT, SleepStage.AWAKE), session.segments.map { it.stage })
        assertEquals(135, session.segments.last().durationSeconds)
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `不認得的階段碼不會丟掉整段睡眠，只留下警告`() {
        val warnings = mutableListOf<String>()
        val stream = record(base, base + 900, Triple(0xF2, base, 300), Triple(0x99, base + 300, 300), Triple(0xF1, base + 600, 300))

        val session = Parsers.sleep(stream, zone) { warnings += it }.single()

        assertEquals(listOf(SleepStage.LIGHT, SleepStage.UNKNOWN, SleepStage.DEEP), session.segments.map { it.stage })
        assertEquals(1, warnings.size)
        assertTrue("0x99" in warnings.single())
    }

    @Test
    fun `同一條串流的多筆紀錄依序解析`() {
        val first = record(base, base + 400, Triple(0xF2, base, 400))
        val second = record(base + 2000, base + 3000, Triple(0xF3, base + 2000, 500), Triple(0xF1, base + 2500, 500))

        val sessions = Parsers.sleep(first + second, zone)

        assertEquals(2, sessions.size)
        assertEquals(1, sessions[0].segments.size)
        assertEquals(2, sessions[1].segments.size)
        assertEquals(RingTime.toInstant(base + 2000, zone), sessions[1].startAt)
    }

    @Test
    fun `Sync 解析遇到未知階段碼時仍保留睡眠，並把警告記在錯誤中`() {
        val stream = record(base, base + 600, Triple(0xF2, base, 300), Triple(0x99, base + 300, 300))
        val frame = Frame(Commands.DATA_SLEEP, stream).encode()

        val decoded = SyncDecoder.decode(listOf(frame), zone)

        assertEquals(1, decoded.sleepSessions.size)
        assertEquals(1, decoded.errors.size)
        assertTrue(decoded.errors.single().startsWith("睡眠："))
    }

    @Test
    fun `長度欄位不合理時整份睡眠仍視為解析失敗`() {
        val broken = record(base, base + 100, Triple(0xF2, base, 100)).also { it[2] = 0x7F }
        val decoded = SyncDecoder.decode(listOf(Frame(Commands.DATA_SLEEP, broken).encode()), zone)
        assertEquals(0, decoded.sleepSessions.size)
        assertEquals(1, decoded.errors.size)
    }
}
