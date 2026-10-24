package com.mysmartring.ble

import com.mysmartring.data.ReceivedFrame
import com.mysmartring.ring.Commands
import com.mysmartring.ring.Frame
import com.mysmartring.ring.Parsers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime

/** 戒指上的一類資料：查筆數、收資料、清除 */
private class DataType(val name: String, val count: Int, val data: Int, val clear: Int)

private val DATA_TYPES = listOf(
    DataType("心率", Commands.COUNT_HEART_RATE, Commands.DATA_HEART_RATE, Commands.CLEAR_HEART_RATE),
    DataType("複合量測", Commands.COUNT_SPOT_MEASUREMENT, Commands.DATA_SPOT_MEASUREMENT, Commands.CLEAR_SPOT_MEASUREMENT),
    DataType("睡眠", Commands.COUNT_SLEEP, Commands.DATA_SLEEP, Commands.CLEAR_SLEEP),
    // 血壓與活動 MVP 不解析，但一樣收下並保存 Raw Frame，之後可由 Rebuild 解析
    DataType("血壓", Commands.COUNT_BLOOD_PRESSURE, Commands.DATA_BLOOD_PRESSURE, Commands.CLEAR_BLOOD_PRESSURE),
    DataType("活動", Commands.COUNT_ACTIVITY, Commands.DATA_ACTIVITY, Commands.CLEAR_ACTIVITY),
)

class SyncReport(val frameCount: Int, val fetched: Map<String, Int>, val cleared: List<String>)

/**
 * 一次 Sync 的流程：校時，逐類查筆數並收資料，**全部收完並交給 [commit] 寫入成功之後**，
 * 才對戒指送清除命令（ADR 0001）。[commit] 丟出例外時不會送任何清除命令。
 */
class SyncRunner(
    private val connection: RingConnection,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val frameTimeoutMs: Long = 5_000,
) {
    suspend fun run(commit: suspend (List<ReceivedFrame>) -> Unit): SyncReport {
        val received = mutableListOf<ReceivedFrame>()
        val fetched = linkedMapOf<String, Int>()
        val toClear = mutableListOf<DataType>()

        connection.write(Commands.setTime(now()).encode())
        awaitCommand(Commands.SET_TIME)

        for (type in DATA_TYPES) {
            connection.write(Commands.query(type.count).encode())
            val count = Parsers.countResponse(awaitCommand(type.count))
            if (count.recordCount == 0) continue

            var bytes = 0L
            var frames = 0
            while (bytes < count.totalBytes) {
                val raw = awaitRaw { commandOf(it) == type.data }
                received += ReceivedFrame(Instant.now(), raw)
                bytes += raw.size - Frame.HEADER_SIZE - Frame.CRC_SIZE
                frames++
            }
            // 戒指在一批結束時會送 0580 作結，app 以 0580 回覆。沒收到也不影響資料
            withTimeoutOrNull(1_000) { awaitRaw { commandOf(it) == Commands.ACK } }
            connection.write(Commands.ack().encode())
            fetched[type.name] = count.recordCount
            toClear += type
        }

        if (received.isNotEmpty()) commit(received)

        val cleared = mutableListOf<String>()
        for (type in toClear) {
            connection.write(Commands.clear(type.clear).encode())
            withTimeoutOrNull(frameTimeoutMs) { awaitRaw { commandOf(it) == type.clear } }
            cleared += type.name
        }
        return SyncReport(received.size, fetched, cleared)
    }

    private suspend fun awaitCommand(command: Int): Frame =
        Frame.decode(awaitRaw { commandOf(it) == command })

    /** 收訊框直到符合條件，不符合的（例如對不上的回應）直接丟棄 */
    private suspend fun awaitRaw(matches: (ByteArray) -> Boolean): ByteArray = withTimeout(frameTimeoutMs) {
        var raw = connection.incoming.receive()
        while (!matches(raw)) raw = connection.incoming.receive()
        raw
    }

    private fun commandOf(bytes: ByteArray): Int =
        if (bytes.size >= 2) ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF) else -1
}
