package com.mysmartring.ring

import java.time.ZoneId

/**
 * 把一次 Sync 收到的 Frame 解析成 Reading 與 Sleep Session。
 * 同型別的多個封包其實是同一條位元組串流（可能切在欄位中間），所以先串接再解析。
 */
object Parsers {
    fun countResponse(frame: Frame): CountResponse {
        val p = frame.payload
        // 沒有資料時戒指只回 2B 的 0 筆
        if (p.size == 2 && p.u16(0) == 0) return CountResponse(0, 0, 0)
        if (p.size != 10) throw ProtocolException("筆數回應長度應為 10B，實際 ${p.size}B")
        return CountResponse(p.u16(0), p.u16(2), p.u32(6))
    }

    /** 串接同一命令的所有封包內容 */
    fun stream(frames: List<Frame>, command: Int): ByteArray =
        frames.filter { it.command == command }
            .fold(ByteArray(0)) { acc, f -> acc + f.payload }

    /** 0515：每筆 6B，`[時間 u32][狀態 1B][心率 1B]` */
    fun heartRate(stream: ByteArray, zone: ZoneId): List<Reading> {
        requireMultiple(stream, 6, "心率")
        return (stream.indices step 6).mapNotNull { i ->
            val bpm = stream.u8(i + 5)
            if (bpm == 0) null
            else Reading(Metric.HEART_RATE, RingTime.toInstant(stream.u32(i), zone), bpm)
        }
    }

    /** 0518：每筆 20B，`[時間 u32][66 01][心率][收縮壓][舒張壓][血氧][8B 未知][2B]`。只取心率與血氧 */
    fun spotMeasurement(stream: ByteArray, zone: ZoneId): List<Reading> {
        requireMultiple(stream, 20, "複合量測")
        return (stream.indices step 20).flatMap { i ->
            val at = RingTime.toInstant(stream.u32(i), zone)
            listOfNotNull(
                stream.u8(i + 6).takeIf { it != 0 }?.let { Reading(Metric.HEART_RATE, at, it) },
                stream.u8(i + 9).takeIf { it != 0 }?.let { Reading(Metric.BLOOD_OXYGEN, at, it) },
            )
        }
    }

    /** 0511：每筆 14B，`[起始 u32][結束 u32][步數 u16][距離 u16][熱量 u16]`，每筆涵蓋半小時，沒有活動的區間不會出現 */
    fun activity(stream: ByteArray, zone: ZoneId): List<ActivityRecord> {
        requireMultiple(stream, 14, "活動")
        return (stream.indices step 14).map { i ->
            ActivityRecord(
                startAt = RingTime.toInstant(stream.u32(i), zone),
                endAt = RingTime.toInstant(stream.u32(i + 4), zone),
                steps = stream.u16(i + 8),
                distanceMeters = stream.u16(i + 10),
                kcal = stream.u16(i + 12),
            )
        }
    }

    /**
     * 0513：`[標記 2B][總長度 u16][入睡 u32][醒來 u32][摘要 8B][階段記錄 N×8B]`
     * 階段記錄 `[階段碼 1B][起始 u32][持續秒數 u16][00]`。串流可能包含多段睡眠，依序讀取。
     * 遇到不認得的階段碼時，該片段記為 [SleepStage.UNKNOWN] 並呼叫 [onWarning]，不丟掉整段睡眠
     */
    fun sleep(stream: ByteArray, zone: ZoneId, onWarning: (String) -> Unit = {}): List<SleepSession> {
        val sessions = mutableListOf<SleepSession>()
        var pos = 0
        while (pos < stream.size) {
            if (stream.size - pos < SLEEP_HEADER) throw ProtocolException("睡眠串流尾端殘缺")
            val length = stream.u16(pos + 2)
            if (length < SLEEP_HEADER || pos + length > stream.size) {
                throw ProtocolException("睡眠長度欄位 $length 不合理")
            }
            if ((length - SLEEP_HEADER) % 8 != 0) throw ProtocolException("睡眠階段記錄長度不是 8 的倍數")
            val segments = (pos + SLEEP_HEADER until pos + length step 8).map { i ->
                val stage = SleepStage.fromCode(stream.u8(i))
                if (stage == SleepStage.UNKNOWN) onWarning("未知的睡眠階段碼 0x%02x".format(stream.u8(i)))
                SleepSegment(
                    stage = stage,
                    startAt = RingTime.toInstant(stream.u32(i + 1), zone),
                    durationSeconds = stream.u16(i + 5),
                )
            }
            sessions += SleepSession(
                startAt = RingTime.toInstant(stream.u32(pos + 4), zone),
                endAt = RingTime.toInstant(stream.u32(pos + 8), zone),
                segments = segments,
                summary = stream.copyOfRange(pos + 12, pos + 20),
            )
            pos += length
        }
        return sessions
    }

    private const val SLEEP_HEADER = 20

    private fun requireMultiple(stream: ByteArray, size: Int, name: String) {
        if (stream.size % size != 0) throw ProtocolException("$name 串流 ${stream.size}B 不是 ${size}B 的倍數")
    }
}

internal fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
internal fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.u32(i: Int): Long =
    u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)
