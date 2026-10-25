package com.mysmartring.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairedRingDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `解除配對只忘記戒指，已儲存的資料不動`() = runTest {
        val syncId = db.syncDao().insert(SyncEntity(startedAtMillis = 1, zoneId = "UTC"))
        db.readingDao().insertAll(listOf(ReadingEntity(metric = "HEART_RATE", measuredAtMillis = 1000, value = 80, syncId = syncId)))
        db.pairedRingDao().save(PairedRingEntity(macAddress = "AA:BB", name = "PulseRing 6DB8", pairedAtMillis = 1))
        assertEquals("PulseRing 6DB8", db.pairedRingDao().observe().first()?.name)

        db.pairedRingDao().delete()

        assertNull(db.pairedRingDao().get())
        assertNull(db.pairedRingDao().observe().first())
        assertEquals(1, db.readingDao().count())
        assertEquals(1, db.syncDao().all().size)
    }

    @Test
    fun `重新配對會取代原本的戒指`() = runTest {
        db.pairedRingDao().save(PairedRingEntity(macAddress = "AA:BB", name = "舊", pairedAtMillis = 1))
        db.pairedRingDao().save(PairedRingEntity(macAddress = "CC:DD", name = "新", pairedAtMillis = 2))
        assertEquals("CC:DD", db.pairedRingDao().get()?.macAddress)
    }
}
