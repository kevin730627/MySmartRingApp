package com.mysmartring.ble

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow

data class FoundRing(val macAddress: String, val name: String, val rssi: Int)

/** 與戒指之間的一條連線。一次 indication 就是一個完整訊框 */
interface RingConnection : AutoCloseable {
    /** 戒指傳來的訊框位元組，連線中斷時以例外關閉 */
    val incoming: ReceiveChannel<ByteArray>

    suspend fun write(bytes: ByteArray)
}

/** 把 BLE 藏在這個介面後面，同步邏輯才能用側錄回放在電腦上測試 */
interface RingTransport {
    /** 掃描名稱以 PulseRing 開頭的裝置，collect 結束即停止掃描 */
    fun scan(): Flow<FoundRing>

    suspend fun connect(macAddress: String): RingConnection
}
