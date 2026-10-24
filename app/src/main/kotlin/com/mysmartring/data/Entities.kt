package com.mysmartring.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一次 Sync。[zoneId] 是當下手機的時區，Rebuild 時需要它把戒指時間換算成 UTC 瞬間 */
@Entity(tableName = "sync")
data class SyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val zoneId: String,
    val finishedAtMillis: Long? = null,
    val status: String = SyncStatus.STARTED,
    /** 解析時遇到的問題，空字串代表沒有 */
    val errors: String = "",
)

object SyncStatus {
    const val STARTED = "STARTED"
    const val OK = "OK"
    const val PARSE_ERROR = "PARSE_ERROR"
}

/** 戒指傳來的完整訊框原始位元組，寫入後不再修改 */
@Entity(
    tableName = "raw_frame",
    foreignKeys = [ForeignKey(SyncEntity::class, ["id"], ["syncId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("syncId")],
)
data class RawFrameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: Long,
    val receivedAtMillis: Long,
    val command: Int,
    val bytes: ByteArray,
)

/** 由 Raw Frame 衍生。(metric, measuredAtMillis) 唯一，重複的忽略 */
@Entity(
    tableName = "reading",
    foreignKeys = [ForeignKey(SyncEntity::class, ["id"], ["syncId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["metric", "measuredAtMillis"], unique = true), Index("syncId")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val measuredAtMillis: Long,
    val value: Int,
    val syncId: Long,
)

/** 由 Raw Frame 衍生。入睡時間唯一；[wakeDate] 是醒來那天（ISO 日期，以 Sync 時區換算） */
@Entity(
    tableName = "sleep_session",
    foreignKeys = [ForeignKey(SyncEntity::class, ["id"], ["syncId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["startAtMillis"], unique = true), Index("syncId"), Index("wakeDate")],
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val wakeDate: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val summary: ByteArray,
    val syncId: Long,
)

@Entity(
    tableName = "sleep_segment",
    foreignKeys = [ForeignKey(SleepSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SleepSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val stage: String,
    val startAtMillis: Long,
    val durationSeconds: Int,
)

/** 只會有一列（id 固定為 1） */
@Entity(tableName = "paired_ring")
data class PairedRingEntity(
    @PrimaryKey val id: Int = 1,
    val macAddress: String,
    val name: String,
    val pairedAtMillis: Long,
)

@Entity(tableName = "dashboard_card")
data class DashboardCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val cardType: String,
    val range: String,
    val position: Int,
)
