package com.mysmartring.ble

import com.mysmartring.data.ReceivedFrame
import com.mysmartring.ring.Commands
import com.mysmartring.ring.hexToBytes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun commandOf(b: ByteArray) = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)

/**
 * 以側錄檔假扮戒指：app 每寫出一個命令，就回放側錄中緊接在該命令之後的戒指訊框。
 * 同一個命令在側錄中出現多次時，依序使用。
 */
private class ReplayConnection(events: MutableList<String>) : RingConnection {
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> get() = channel
    private val log = events
    private val replies = HashMap<Int, ArrayDeque<List<ByteArray>>>()

    init {
        val lines = javaClass.getResourceAsStream("/capture-2026-10-24.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
            .map { it.split(" ").let { (dir, hex) -> dir to hex.hexToBytes() } }
        var current: MutableList<ByteArray>? = null
        for ((dir, bytes) in lines) {
            if (dir == "TX") {
                current = mutableListOf()
                replies.getOrPut(commandOf(bytes)) { ArrayDeque() }.addLast(current)
            } else {
                current?.add(bytes)
            }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        val command = commandOf(bytes)
        log += "write %04x".format(command)
        replies[command]?.removeFirstOrNull()?.forEach { channel.trySend(it) }
    }

    override fun close() {
        channel.close()
    }
}

class SyncRunnerTest {
    private val clearCommands = setOf(
        Commands.CLEAR_ACTIVITY, Commands.CLEAR_SLEEP, Commands.CLEAR_HEART_RATE,
        Commands.CLEAR_BLOOD_PRESSURE, Commands.CLEAR_SPOT_MEASUREMENT,
    ).map { "write %04x".format(it) }.toSet()

    private fun runner(events: MutableList<String>) =
        SyncRunner(ReplayConnection(events), now = { LocalDateTime.of(2026, 10, 24, 11, 33, 25) })

    @Test
    fun `收完全部資料並在寫入成功之後才送出清除命令`() = runTest {
        val events = mutableListOf<String>()
        var committed: List<ReceivedFrame> = emptyList()

        val report = runner(events).run { frames ->
            events += "commit"
            committed = frames
        }

        assertEquals(setOf("心率", "複合量測", "睡眠", "血壓", "活動"), report.fetched.keys)
        assertEquals(125, report.fetched["心率"])
        assertEquals(report.frameCount, committed.size)

        val commitAt = events.indexOf("commit")
        val clears = events.withIndex().filter { it.value in clearCommands }
        assertEquals(5, clears.size, "五類資料都應清除")
        assertTrue(clears.all { it.index > commitAt }, "清除命令必須在 commit 之後：$events")
        assertEquals(5, report.cleared.size)
    }

    @Test
    fun `寫入失敗時不會送出任何清除命令`() = runTest {
        val events = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            runner(events).run { error("磁碟已滿") }
        }

        assertTrue(events.none { it in clearCommands }, "不應送出清除命令：$events")
    }

    @Test
    fun `收到的資料訊框與側錄原始位元組一致`() = runTest {
        var committed: List<ReceivedFrame> = emptyList()
        runner(mutableListOf()).run { committed = it }

        val dataCommands = setOf(
            Commands.DATA_HEART_RATE, Commands.DATA_SPOT_MEASUREMENT, Commands.DATA_SLEEP,
            Commands.DATA_BLOOD_PRESSURE, Commands.DATA_ACTIVITY,
        )
        assertTrue(committed.all { commandOf(it.bytes) in dataCommands })
        assertTrue(committed.any { commandOf(it.bytes) == Commands.DATA_SLEEP })
    }
}
