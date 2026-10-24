package com.mysmartring.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SyncEntity::class,
        RawFrameEntity::class,
        ReadingEntity::class,
        SleepSessionEntity::class,
        SleepSegmentEntity::class,
        PairedRingEntity::class,
        DashboardCardEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao
    abstract fun rawFrameDao(): RawFrameDao
    abstract fun readingDao(): ReadingDao
    abstract fun sleepDao(): SleepDao
    abstract fun pairedRingDao(): PairedRingDao
    abstract fun dashboardCardDao(): DashboardCardDao
}
