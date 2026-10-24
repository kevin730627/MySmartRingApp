package com.mysmartring.ring

/** CRC-16/CCITT-FALSE（多項式 0x1021、初始值 0xFFFF），戒指訊框尾端的檢查碼 */
object Crc16 {
    fun compute(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
