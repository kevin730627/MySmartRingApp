package com.mysmartring.ring

import java.time.Instant

enum class Metric { HEART_RATE, BLOOD_OXYGEN }

/** 某個 Metric 在某個時間點的一筆數值 */
data class Reading(val metric: Metric, val measuredAt: Instant, val value: Int)

/** 戒指只回報這三種階段，沒有清醒 */
enum class SleepStage(val code: Int) {
    DEEP(0xF1),
    LIGHT(0xF2),
    REM(0xF3);

    companion object {
        fun fromCode(code: Int): SleepStage =
            entries.firstOrNull { it.code == code }
                ?: throw ProtocolException("未知的睡眠階段碼 0x%02x".format(code))
    }
}

data class SleepSegment(val stage: SleepStage, val startAt: Instant, val durationSeconds: Int)

data class SleepSession(
    val startAt: Instant,
    val endAt: Instant,
    val segments: List<SleepSegment>,
    /** 戒指給的 8 位元組摘要，意義尚未解開，先原樣保留 */
    val summary: ByteArray,
) {
    override fun equals(other: Any?) = other is SleepSession && startAt == other.startAt &&
        endAt == other.endAt && segments == other.segments && summary.contentEquals(other.summary)

    override fun hashCode() = 31 * (31 * startAt.hashCode() + endAt.hashCode()) + segments.hashCode()
}

/** 戒指對筆數查詢的回應：`[記錄數 u16][封包數 u16][00 00][總位元組數 u32]`，沒有資料時只有 `[00 00]` */
data class CountResponse(val recordCount: Int, val packetCount: Int, val totalBytes: Long)
