package com.mysmartring.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert
    suspend fun insert(sync: SyncEntity): Long

    @Update
    suspend fun update(sync: SyncEntity)

    @Query("SELECT * FROM sync ORDER BY id")
    suspend fun all(): List<SyncEntity>
}

@Dao
interface RawFrameDao {
    @Insert
    suspend fun insertAll(frames: List<RawFrameEntity>)

    @Query("SELECT * FROM raw_frame WHERE syncId = :syncId ORDER BY id")
    suspend fun forSync(syncId: Long): List<RawFrameEntity>

    @Query("SELECT COUNT(*) FROM raw_frame")
    suspend fun count(): Int
}

@Dao
interface ReadingDao {
    /** 唯一鍵衝突時忽略，先到的先留 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<ReadingEntity>)

    @Query("SELECT * FROM reading WHERE metric = :metric AND measuredAtMillis >= :from AND measuredAtMillis < :to ORDER BY measuredAtMillis")
    suspend fun between(metric: String, from: Long, to: Long): List<ReadingEntity>

    @Query("SELECT * FROM reading WHERE metric = :metric ORDER BY measuredAtMillis DESC LIMIT 1")
    fun latest(metric: String): Flow<ReadingEntity?>

    @Query("SELECT * FROM reading WHERE metric = :metric AND measuredAtMillis >= :from AND measuredAtMillis < :to ORDER BY measuredAtMillis")
    fun observeBetween(metric: String, from: Long, to: Long): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM reading WHERE metric = :metric ORDER BY measuredAtMillis")
    suspend fun all(metric: String): List<ReadingEntity>

    @Query("SELECT COUNT(*) FROM reading")
    suspend fun count(): Int

    @Query("DELETE FROM reading")
    suspend fun deleteAll()
}

@Dao
interface SleepDao {
    /** 回傳新列的 id；入睡時間重複而被忽略時為 -1 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<SleepSegmentEntity>)

    @Query("SELECT * FROM sleep_session ORDER BY startAtMillis")
    suspend fun sessions(): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_segment WHERE sessionId = :sessionId ORDER BY startAtMillis")
    suspend fun segments(sessionId: Long): List<SleepSegmentEntity>

    @Query("SELECT * FROM sleep_session ORDER BY startAtMillis")
    fun observeAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_segment ORDER BY startAtMillis")
    fun observeAllSegments(): Flow<List<SleepSegmentEntity>>

    @Query("SELECT MAX(wakeDate) FROM sleep_session")
    fun observeLatestWakeDate(): Flow<String?>

    @Query("SELECT * FROM sleep_session WHERE wakeDate = :wakeDate ORDER BY startAtMillis")
    fun observeSessions(wakeDate: String): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_segment WHERE sessionId IN (SELECT id FROM sleep_session WHERE wakeDate = :wakeDate) ORDER BY startAtMillis")
    fun observeSegments(wakeDate: String): Flow<List<SleepSegmentEntity>>

    @Query("DELETE FROM sleep_session")
    suspend fun deleteAllSessions()
}

@Dao
interface PairedRingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(ring: PairedRingEntity)

    @Query("SELECT * FROM paired_ring WHERE id = 1")
    suspend fun get(): PairedRingEntity?
}

@Dao
interface DashboardCardDao {
    @Insert
    suspend fun insertAll(cards: List<DashboardCardEntity>)

    @Query("SELECT * FROM dashboard_card ORDER BY position")
    suspend fun all(): List<DashboardCardEntity>
}
