package com.mysmartring.data

import androidx.room.withTransaction
import com.mysmartring.ring.DecodedSync
import com.mysmartring.ring.Frame
import com.mysmartring.ring.SyncDecoder
import java.time.Instant
import java.time.ZoneId

/** BLE 收到的一個完整訊框 */
class ReceivedFrame(val receivedAt: Instant, val bytes: ByteArray)

class RingRepository(private val db: AppDatabase) {

    /**
     * 儲存一次 Sync 收到的訊框並解析。
     *
     * Raw Frame 先在自己的交易中寫入並提交，之後才解析。回傳時代表 Raw Frame 已經安全落地，
     * 呼叫端這時才可以對戒指送清除命令；即使解析失敗，原始資料也不會遺失。
     */
    suspend fun ingest(startedAt: Instant, zone: ZoneId, frames: List<ReceivedFrame>): Long {
        val syncId = db.withTransaction {
            val id = db.syncDao().insert(SyncEntity(startedAtMillis = startedAt.toEpochMilli(), zoneId = zone.id))
            db.rawFrameDao().insertAll(
                frames.map { RawFrameEntity(syncId = id, receivedAtMillis = it.receivedAt.toEpochMilli(), command = commandOf(it.bytes), bytes = it.bytes) },
            )
            id
        }
        derive(syncId)
        return syncId
    }

    /** 清除所有衍生資料，再依最新的解析器從 Raw Frame 依序重算 */
    suspend fun rebuild() {
        db.withTransaction {
            db.readingDao().deleteAll()
            db.sleepDao().deleteAllSessions()
            db.syncDao().all().forEach { deriveInTransaction(it) }
        }
    }

    private suspend fun derive(syncId: Long) {
        db.withTransaction {
            deriveInTransaction(db.syncDao().all().first { it.id == syncId })
        }
    }

    private suspend fun deriveInTransaction(sync: SyncEntity) {
        val zone = ZoneId.of(sync.zoneId)
        val raw = db.rawFrameDao().forSync(sync.id).map { it.bytes }
        val decoded = SyncDecoder.decode(raw, zone)
        store(sync, decoded, zone)
        db.syncDao().update(
            sync.copy(
                finishedAtMillis = sync.finishedAtMillis ?: System.currentTimeMillis(),
                status = if (decoded.errors.isEmpty()) SyncStatus.OK else SyncStatus.PARSE_ERROR,
                errors = decoded.errors.joinToString("\n"),
            ),
        )
    }

    private suspend fun store(sync: SyncEntity, decoded: DecodedSync, zone: ZoneId) {
        db.readingDao().insertAll(
            decoded.readings.map {
                ReadingEntity(metric = it.metric.name, measuredAtMillis = it.measuredAt.toEpochMilli(), value = it.value, syncId = sync.id)
            },
        )
        for (s in decoded.sleepSessions) {
            val id = db.sleepDao().insertSession(
                SleepSessionEntity(
                    startAtMillis = s.startAt.toEpochMilli(),
                    endAtMillis = s.endAt.toEpochMilli(),
                    wakeDate = s.endAt.atZone(zone).toLocalDate().toString(),
                    summary = s.summary,
                    syncId = sync.id,
                ),
            )
            if (id == -1L) continue // 同一段睡眠已存在
            db.sleepDao().insertSegments(
                s.segments.map {
                    SleepSegmentEntity(sessionId = id, stage = it.stage.name, startAtMillis = it.startAt.toEpochMilli(), durationSeconds = it.durationSeconds)
                },
            )
        }
    }

    /** 無法解析命令的壞訊框也要保存，命令記為 -1 */
    private fun commandOf(bytes: ByteArray): Int =
        if (bytes.size >= 2) ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF) else -1
}
