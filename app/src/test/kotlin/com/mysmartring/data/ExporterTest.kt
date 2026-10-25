package com.mysmartring.data

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExporterTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private val readings = listOf(
        ReadingEntity(metric = "HEART_RATE", measuredAtMillis = ms("2026-10-24T05:10:00Z"), value = 84, syncId = 1),
        ReadingEntity(metric = "HEART_RATE", measuredAtMillis = ms("2026-10-24T05:20:00Z"), value = 86, syncId = 1),
    )

    private fun sleep(id: Long, start: String, end: String, vararg segs: Triple<String, String, Int>) = SleepExport(
        SleepSessionEntity(id = id, startAtMillis = ms(start), endAtMillis = ms(end), wakeDate = "2026-10-25", summary = ByteArray(8), syncId = 1),
        segs.map { SleepSegmentEntity(sessionId = id, stage = it.first, startAtMillis = ms(it.second), durationSeconds = it.third) },
    )

    private val sleeps = listOf(
        sleep(1, "2026-10-24T16:54:00Z", "2026-10-24T19:20:00Z", Triple("LIGHT", "2026-10-24T16:54:00Z", 3600), Triple("DEEP", "2026-10-24T17:54:00Z", 1830)),
        sleep(2, "2026-10-24T19:23:00Z", "2026-10-25T01:05:00Z", Triple("REM", "2026-10-24T19:23:00Z", 3600)),
    )

    @Test
    fun `Reading CSV 有標題列，時間為帶時區偏移的 ISO 格式`() {
        assertEquals(
            "時間,數值\n2026-10-24T13:10:00+08:00,84\n2026-10-24T13:20:00+08:00,86\n",
            Exporter.readingsCsv(readings, zone),
        )
    }

    @Test
    fun `沒有 Reading 時 CSV 只有標題列`() {
        assertEquals("時間,數值\n", Exporter.readingsCsv(emptyList(), zone))
    }

    @Test
    fun `睡眠 CSV 每段 Session 一列且不合併`() {
        val lines = Exporter.sleepCsv(sleeps, zone).trim().lines()
        assertEquals("入睡,醒來,深睡分鐘,淺睡分鐘,REM分鐘", lines[0])
        assertEquals("2026-10-25T00:54:00+08:00,2026-10-25T03:20:00+08:00,30,60,0", lines[1])
        assertEquals("2026-10-25T03:23:00+08:00,2026-10-25T09:05:00+08:00,0,0,60", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun `JSON 可被解析並包含全部 Reading 與睡眠分段`() {
        val root = JSONObject(Exporter.json(readings, sleeps, zone))
        assertEquals(2, root.getJSONArray("readings").length())
        assertEquals(84, root.getJSONArray("readings").getJSONObject(0).getInt("value"))
        val sessions = root.getJSONArray("sleepSessions")
        assertEquals(2, sessions.length())
        assertEquals("DEEP", sessions.getJSONObject(0).getJSONArray("segments").getJSONObject(1).getString("stage"))
        assertEquals(1830, sessions.getJSONObject(0).getJSONArray("segments").getJSONObject(1).getInt("durationSeconds"))
    }

    @Test
    fun `空資料的 JSON 仍然有效`() {
        val root = JSONObject(Exporter.json(emptyList(), emptyList(), zone))
        assertEquals(0, root.getJSONArray("readings").length())
        assertEquals(0, root.getJSONArray("sleepSessions").length())
    }
}
