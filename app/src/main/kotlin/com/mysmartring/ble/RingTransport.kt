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

/** 手機的藍牙沒有開啟，連線或掃描之前就會丟出 */
class BluetoothOffException : java.io.IOException("藍牙未開啟")

/** 把 BLE 藏在這個介面後面，同步邏輯才能用側錄回放在電腦上測試 */
interface RingTransport {
    /** 手機的藍牙目前是否開啟；建立 GATT 連線或掃描之前先確認 */
    val isBluetoothEnabled: Boolean

    /** 掃描名稱以 PulseRing 開頭的裝置，collect 結束即停止掃描 */
    fun scan(): Flow<FoundRing>

    suspend fun connect(macAddress: String): RingConnection
}
