package com.mysmartring.ring

/**
 * 戒指訊框：`[命令 2B][總長度 2B 小端序][內容][CRC16 2B 小端序]`
 * 命令以兩個位元組的大端序整數表示，例如 0x0506。
 */
class Frame(val command: Int, val payload: ByteArray) {
    fun encode(): ByteArray {
        val total = HEADER_SIZE + payload.size + CRC_SIZE
        val out = ByteArray(total)
        out[0] = (command shr 8).toByte()
        out[1] = command.toByte()
        out[2] = total.toByte()
        out[3] = (total shr 8).toByte()
        payload.copyInto(out, HEADER_SIZE)
        val crc = Crc16.compute(out, 0, total - CRC_SIZE)
        out[total - 2] = crc.toByte()
        out[total - 1] = (crc shr 8).toByte()
        return out
    }

    override fun equals(other: Any?) =
        other is Frame && command == other.command && payload.contentEquals(other.payload)

    override fun hashCode() = 31 * command + payload.contentHashCode()

    override fun toString() = "Frame(0x%04x, %s)".format(command, payload.toHex())

    companion object {
        const val HEADER_SIZE = 4
        const val CRC_SIZE = 2

        fun decode(bytes: ByteArray): Frame {
            if (bytes.size < HEADER_SIZE + CRC_SIZE) throw ProtocolException("訊框太短：${bytes.size}B")
            val total = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
            if (total != bytes.size) throw ProtocolException("長度欄位 $total 與實際 ${bytes.size}B 不符")
            val expected = Crc16.compute(bytes, 0, total - CRC_SIZE)
            val actual = (bytes[total - 2].toInt() and 0xFF) or ((bytes[total - 1].toInt() and 0xFF) shl 8)
            if (expected != actual) throw ProtocolException("CRC 錯誤：預期 %04x，實際 %04x".format(expected, actual))
            val command = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            return Frame(command, bytes.copyOfRange(HEADER_SIZE, total - CRC_SIZE))
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "十六進位字串長度必須為偶數" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
