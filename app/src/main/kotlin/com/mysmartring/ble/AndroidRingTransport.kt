package com.mysmartring.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/** PulseRing 的資料服務與特徵（見 docs/ring-protocol.md） */
internal object RingGatt {
    val SERVICE: UUID = UUID.fromString("be940000-7333-be46-b7ae-689e71722bd5")

    /** 命令寫入，同時以 indication 回應 */
    val COMMAND: UUID = UUID.fromString("be940001-7333-be46-b7ae-689e71722bd5")

    /** 戒指主動推送的資料，indication */
    val DATA: UUID = UUID.fromString("be940003-7333-be46-b7ae-689e71722bd5")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val NAME_PREFIX = "PulseRing"
    const val REQUESTED_MTU = 517
}

/** 呼叫前須已取得 BLUETOOTH_SCAN 與 BLUETOOTH_CONNECT 權限 */
@SuppressLint("MissingPermission")
class AndroidRingTransport(private val context: Context) : RingTransport {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java).adapter

    override fun scan(): Flow<FoundRing> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner ?: throw IOException("藍牙未開啟")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                if (name.startsWith(RingGatt.NAME_PREFIX)) {
                    trySend(FoundRing(result.device.address, name, result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("掃描失敗，錯誤碼 $errorCode"))
            }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        awaitClose { scanner.stopScan(callback) }
    }

    override suspend fun connect(macAddress: String): RingConnection {
        val connection = GattConnection(context)
        try {
            connection.open(adapter.getRemoteDevice(macAddress))
        } catch (e: Throwable) {
            connection.close()
            throw e
        }
        return connection
    }
}

@SuppressLint("MissingPermission")
private class GattConnection(private val context: Context) : RingConnection {
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> get() = channel

    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null

    // 每個 GATT 操作一次只能有一個在進行，結果由對應的 callback 完成
    private var connected = CompletableDeferred<Unit>()
    private var mtu = CompletableDeferred<Int>()
    private var services = CompletableDeferred<Unit>()
    private var descriptorWrite = CompletableDeferred<Int>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val error = IOException("連線中斷（狀態 $status）")
                connected.completeExceptionally(error)
                services.completeExceptionally(error)
                descriptorWrite.completeExceptionally(error)
                channel.close(error)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            this@GattConnection.mtu.complete(mtu)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) services.complete(Unit)
            else services.completeExceptionally(IOException("探索服務失敗（狀態 $status）"))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorWrite.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            channel.trySend(value.copyOf())
        }

        @Deprecated("API 33 以前的舊版回呼")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { channel.trySend(it.copyOf()) }
        }
    }

    suspend fun open(device: BluetoothDevice) {
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("無法建立 GATT 連線")
        gatt = g
        withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }

        // MTU 協商失敗不致命，只是單包可能較小
        if (g.requestMtu(RingGatt.REQUESTED_MTU)) {
            runCatching { withTimeout(STEP_TIMEOUT_MS) { mtu.await() } }
        }

        if (!g.discoverServices()) throw IOException("無法開始探索服務")
        withTimeout(STEP_TIMEOUT_MS) { services.await() }

        val service = g.getService(RingGatt.SERVICE) ?: throw IOException("找不到戒指的資料服務，這可能不是 PulseRing")
        command = service.getCharacteristic(RingGatt.COMMAND) ?: throw IOException("找不到命令特徵")
        val data = service.getCharacteristic(RingGatt.DATA) ?: throw IOException("找不到資料特徵")
        enableIndication(g, command!!)
        enableIndication(g, data)
    }

    private suspend fun enableIndication(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(RingGatt.CCCD) ?: throw IOException("特徵沒有 CCCD")
        descriptorWrite = CompletableDeferred()
        val started = if (android.os.Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }
        if (!started) throw IOException("無法啟用 indication")
        val status = withTimeout(STEP_TIMEOUT_MS) { descriptorWrite.await() }
        if (status != BluetoothGatt.GATT_SUCCESS) throw IOException("啟用 indication 失敗（狀態 $status）")
    }

    override suspend fun write(bytes: ByteArray) {
        val g = gatt ?: throw IOException("尚未連線")
        val c = command ?: throw IOException("尚未連線")
        var status = writeOnce(g, c, bytes)
        // 前一個 GATT 操作的 callback 已回來，但堆疊內部可能還沒釋放，會短暫回報忙碌
        var retries = 0
        while (status == BUSY && retries++ < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS)
            status = writeOnce(g, c, bytes)
        }
        if (status != BluetoothStatusCodes.SUCCESS) throw IOException("寫入失敗（錯誤碼 $status）")
    }

    private fun writeOnce(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray): Int =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                c.value = bytes
                if (g.writeCharacteristic(c)) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
            }
        }

    override fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        channel.close()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val STEP_TIMEOUT_MS = 8_000L
        const val BUSY = 201 // BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY
        const val MAX_BUSY_RETRIES = 20
        const val BUSY_RETRY_DELAY_MS = 50L
    }
}
