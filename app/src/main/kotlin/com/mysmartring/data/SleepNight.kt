package com.mysmartring.data

/** 清醒時段：同一晚相鄰兩段 Sleep Session 之間的空檔 */
data class AwakeGap(val startAtMillis: Long, val endAtMillis: Long) {
    val seconds: Long get() = (endAtMillis - startAtMillis) / 1000
}

class SessionWithSegments(val session: SleepSessionEntity, val segments: List<SleepSegmentEntity>)

/**
 * 醒來在同一天的睡眠。戒指可能把一晚切成多段 Sleep Session，這裡不合併它們，
 * 只把段與段之間的空檔算成清醒時段，供顯示與統計。
 */
class SleepNight private constructor(
    val wakeDate: String,
    val sessions: List<SessionWithSegments>,
) {
    val startAtMillis: Long get() = sessions.first().session.startAtMillis
    val endAtMillis: Long get() = sessions.last().session.endAtMillis

    val awakeGaps: List<AwakeGap> = sessions.zipWithNext { a, b ->
        AwakeGap(a.session.endAtMillis, b.session.startAtMillis)
    }.filter { it.seconds > 0 }

    /** 各睡眠階段的總秒數，沒出現的階段為 0 */
    fun stageSeconds(stage: String): Long =
        sessions.sumOf { s -> s.segments.filter { it.stage == stage }.sumOf { it.durationSeconds.toLong() } }

    val sleepSeconds: Long get() = sessions.sumOf { s -> s.segments.sumOf { it.durationSeconds.toLong() } }
    val awakeSeconds: Long get() = awakeGaps.sumOf { it.seconds }

    companion object {
        /** [sessions] 為同一個醒來日的所有 Session；沒有資料時回傳 null */
        fun of(wakeDate: String, sessions: List<SessionWithSegments>): SleepNight? {
            if (sessions.isEmpty()) return null
            return SleepNight(
                wakeDate,
                sessions.sortedBy { it.session.startAtMillis }
                    .map { SessionWithSegments(it.session, it.segments.sortedBy { s -> s.startAtMillis }) },
            )
        }
    }
}
