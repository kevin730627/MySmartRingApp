package com.mysmartring.ring

import java.time.Instant

/**
 * 血壓以收縮壓與舒張壓兩個 Metric 各存一筆，時間點相同。戒指的血壓是估算值：
 * 收縮壓與心率的相關係數約 0.95，不是獨立量測，不可當成醫療數據。
 *
 * [STEPS] 是半小時區間內的步數，不是瞬間值：同一天的所有 Reading 加總才是當日步數 */
enum class Metric { HEART_RATE, BLOOD_OXYGEN, STEPS, BLOOD_PRESSURE_SYSTOLIC, BLOOD_PRESSURE_DIASTOLIC }

/** 某個 Metric 在某個時間點的一筆數值 */
data class Reading(val metric: Metric, val measuredAt: Instant, val value: Int)

/**
 * 戒指回報的睡眠階段碼。[AWAKE] 是從樣本推斷的：一段 2 分 15 秒的 0xF4 出現在第一段睡眠結尾，
 * 緊接著一段 32 分鐘的空檔，之後才進入下一段睡眠。[UNKNOWN] 代表尚未認得的階段碼，
 * 該片段照樣保存（解析仍成功），並在 Sync 的錯誤欄位留下警告。
 */
enum class SleepStage(val code: Int) {
    DEEP(0xF1),
    LIGHT(0xF2),
    REM(0xF3),
    AWAKE(0xF4),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): SleepStage = entries.firstOrNull { it.code == code } ?: UNKNOWN
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

/**
 * 活動 `0511` 的一筆，涵蓋半小時。只有 [steps] 目前被使用；[distanceMeters] 與 [kcal]
 * 是從欄位間的比例推測（約 0.63 公尺與 0.04 大卡每步），尚未用外部數據驗證，不要顯示或匯出。
 */
data class ActivityRecord(
    val startAt: Instant,
    val endAt: Instant,
    val steps: Int,
    val distanceMeters: Int,
    val kcal: Int,
)

/** 戒指對筆數查詢的回應：`[記錄數 u16][封包數 u16][00 00][總位元組數 u32]`，沒有資料時只有 `[00 00]` */
data class CountResponse(val recordCount: Int, val packetCount: Int, val totalBytes: Long)
