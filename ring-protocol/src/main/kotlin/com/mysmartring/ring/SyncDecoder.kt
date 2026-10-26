package com.mysmartring.ring

import java.time.ZoneId

/** 一次 Sync 的解析結果。某一型別解析失敗時，其餘型別仍然保留，失敗原因放在 [errors] */
data class DecodedSync(
    val readings: List<Reading>,
    val sleepSessions: List<SleepSession>,
    val errors: List<String>,
)

/**
 * 從一次 Sync 的 Raw Frame（完整訊框位元組，依收到順序）還原出 Reading 與 Sleep Session。
 * 純函式，同樣的輸入永遠得到同樣的結果，因此 Rebuild 可以重複執行。
 */
object SyncDecoder {
    fun decode(rawFrames: List<ByteArray>, zone: ZoneId): DecodedSync {
        val errors = mutableListOf<String>()
        val frames = rawFrames.mapNotNull {
            try {
                Frame.decode(it)
            } catch (e: ProtocolException) {
                errors += "訊框無法解析：${e.message}"
                null
            }
        }

        fun <T> attempt(name: String, block: () -> List<T>): List<T> =
            try {
                block()
            } catch (e: ProtocolException) {
                errors += "$name：${e.message}"
                emptyList()
            }

        val heartRate = attempt("心率") {
            Parsers.heartRate(Parsers.stream(frames, Commands.DATA_HEART_RATE), zone)
        }
        val spot = attempt("複合量測") {
            Parsers.spotMeasurement(Parsers.stream(frames, Commands.DATA_SPOT_MEASUREMENT), zone)
        }
        val sleep = attempt("睡眠") {
            Parsers.sleep(Parsers.stream(frames, Commands.DATA_SLEEP), zone) { errors += "睡眠：$it" }
        }
        // 步數以區間起點為時間點；0 步的區間不產生 Reading
        val steps = attempt("活動") {
            Parsers.activity(Parsers.stream(frames, Commands.DATA_ACTIVITY), zone)
                .filter { it.steps > 0 }
                .map { Reading(Metric.STEPS, it.startAt, it.steps) }
        }
        return DecodedSync(heartRate + spot + steps, sleep, errors)
    }
}
