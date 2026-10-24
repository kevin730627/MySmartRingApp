package com.mysmartring.ring

import java.time.LocalDateTime

/** app 送給戒指的命令與戒指回傳的資料型別 */
object Commands {
    const val SET_TIME = 0x0100

    // 查詢筆數（戒指以同命令回應 [CountResponse]）
    const val COUNT_ACTIVITY = 0x0502
    const val COUNT_SLEEP = 0x0504
    const val COUNT_HEART_RATE = 0x0506
    const val COUNT_BLOOD_PRESSURE = 0x0508
    const val COUNT_SPOT_MEASUREMENT = 0x0509

    // 戒指主動推送的資料
    const val DATA_ACTIVITY = 0x0511
    const val DATA_SLEEP = 0x0513
    const val DATA_HEART_RATE = 0x0515
    const val DATA_BLOOD_PRESSURE = 0x0517
    const val DATA_SPOT_MEASUREMENT = 0x0518

    /** 收完一批資料後的確認 */
    const val ACK = 0x0580

    // 清除戒指上已取得的資料。必須在 Raw Frame 寫入資料庫之後才能送出
    const val CLEAR_ACTIVITY = 0x0540
    const val CLEAR_SLEEP = 0x0541
    const val CLEAR_HEART_RATE = 0x0542
    const val CLEAR_BLOOD_PRESSURE = 0x0543
    const val CLEAR_SPOT_MEASUREMENT = 0x0544

    /** 校時：年(2B 小端序) 月 日 時 分 秒 星期（星期一為 0） */
    fun setTime(now: LocalDateTime): Frame = Frame(
        SET_TIME,
        byteArrayOf(
            now.year.toByte(), (now.year shr 8).toByte(),
            now.monthValue.toByte(), now.dayOfMonth.toByte(),
            now.hour.toByte(), now.minute.toByte(), now.second.toByte(),
            (now.dayOfWeek.value - 1).toByte(),
        ),
    )

    fun query(command: Int): Frame = Frame(command, ByteArray(0))

    fun ack(): Frame = Frame(ACK, byteArrayOf(0))

    fun clear(command: Int): Frame = Frame(command, byteArrayOf(2))
}
