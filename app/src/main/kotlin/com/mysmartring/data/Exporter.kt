package com.mysmartring.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SleepExport(val session: SleepSessionEntity, val segments: List<SleepSegmentEntity>) {
    fun stageMinutes(stage: String) = segments.filter { it.stage == stage }.sumOf { it.durationSeconds } / 60
}

/**
 * 把資料轉成 CSV 與 JSON 文字。時間一律輸出為帶時區偏移的 ISO 8601，不輸出 Raw Frame。
 */
object Exporter {
    /** CSV 開頭的 UTF-8 BOM，Excel 才會把中文標題當成 UTF-8 */
    val BOM: String = Char(0xFEFF).toString()

    private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private fun time(millis: Long, zone: ZoneId) = ISO.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** 單一 Metric 的 CSV：時間、數值 */
    fun readingsCsv(readings: List<ReadingEntity>, zone: ZoneId): String = buildString {
        append("時間,數值\n")
        readings.forEach { append(time(it.measuredAtMillis, zone)).append(',').append(it.value).append('\n') }
    }

    /**
     * 血壓 CSV：收縮壓與舒張壓以相同時間點配成一列。戒指的血壓是估算值，不是醫療量測。
     * 只有其中一個的時間點（理論上不會發生）也會輸出，缺的欄位留空
     */
    fun bloodPressureCsv(systolic: List<ReadingEntity>, diastolic: List<ReadingEntity>, zone: ZoneId): String = buildString {
        append("時間,收縮壓,舒張壓\n")
        val sys = systolic.associate { it.measuredAtMillis to it.value }
        val dia = diastolic.associate { it.measuredAtMillis to it.value }
        (sys.keys + dia.keys).sorted().forEach {
            append(time(it, zone)).append(',').append(sys[it] ?: "").append(',').append(dia[it] ?: "").append('\n')
        }
    }

    /** 睡眠 CSV：每段 Sleep Session 一列，不合併 */
    fun sleepCsv(sleeps: List<SleepExport>, zone: ZoneId): String = buildString {
        append("入睡,醒來,深睡分鐘,淺睡分鐘,REM分鐘\n")
        sleeps.forEach {
            append(time(it.session.startAtMillis, zone)).append(',')
            append(time(it.session.endAtMillis, zone)).append(',')
            append(it.stageMinutes("DEEP")).append(',')
            append(it.stageMinutes("LIGHT")).append(',')
            append(it.stageMinutes("REM")).append('\n')
        }
    }

    /**
     * 全部資料的 JSON。所有字串都是列舉名稱、ISO 時間或日期，不含需要跳脫的字元。
     */
    fun json(readings: List<ReadingEntity>, sleeps: List<SleepExport>, zone: ZoneId): String = buildString {
        append("{\"readings\":[")
        readings.joinTo(this, ",") {
            "{\"metric\":\"${it.metric}\",\"time\":\"${time(it.measuredAtMillis, zone)}\",\"value\":${it.value}}"
        }
        append("],\"sleepSessions\":[")
        sleeps.joinTo(this, ",") { s ->
            val segments = s.segments.joinToString(",") {
                "{\"stage\":\"${it.stage}\",\"start\":\"${time(it.startAtMillis, zone)}\",\"durationSeconds\":${it.durationSeconds}}"
            }
            "{\"start\":\"${time(s.session.startAtMillis, zone)}\",\"end\":\"${time(s.session.endAtMillis, zone)}\"," +
                "\"wakeDate\":\"${s.session.wakeDate}\",\"segments\":[$segments]}"
        }
        append("]}")
    }
}
