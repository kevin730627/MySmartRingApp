package com.mysmartring.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mysmartring.ring.hexToBytes
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingRepositoryTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val startedAt = Instant.parse("2026-10-24T03:33:00Z")

    private lateinit var db: AppDatabase
    private lateinit var repo: RingRepository

    private val captured = javaClass.getResourceAsStream("/capture-2026-10-24.txt")!!
        .bufferedReader().readLines().filter { it.startsWith("RX ") }
        .map { ReceivedFrame(startedAt, it.removePrefix("RX ").hexToBytes()) }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = RingRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `Sync 會保存全部 Raw Frame 並解析出 Reading 與 Sleep Session`() = runTest {
        val syncId = repo.ingest(startedAt, zone, captured)

        assertEquals(captured.size, db.rawFrameDao().forSync(syncId).size)
        assertTrue(db.readingDao().count() > 0)
        assertEquals(SyncStatus.OK, db.syncDao().all().single().status)

        val session = db.sleepDao().sessions().single()
        assertEquals("2026-10-24", session.wakeDate)
        assertEquals(48, db.sleepDao().segments(session.id).size)
    }

    @Test
    fun `原始位元組完整保存`() = runTest {
        val syncId = repo.ingest(startedAt, zone, captured)
        val stored = db.rawFrameDao().forSync(syncId).map { it.bytes.toList() }
        assertEquals(captured.map { it.bytes.toList() }, stored)
    }

    @Test
    fun `重複的 Sync 不會產生重複的 Reading 或 Sleep Session`() = runTest {
        repo.ingest(startedAt, zone, captured)
        val readings = db.readingDao().count()

        repo.ingest(startedAt.plusSeconds(60), zone, captured)

        assertEquals(readings, db.readingDao().count())
        assertEquals(1, db.sleepDao().sessions().size)
        assertEquals(captured.size * 2, db.rawFrameDao().count())
    }

    @Test
    fun `Rebuild 重算出完全相同的資料且可重複執行`() = runTest {
        repo.ingest(startedAt, zone, captured)
        val range = 0L to Long.MAX_VALUE
        val before = db.readingDao().between("HEART_RATE", range.first, range.second).map { it.metric to it.measuredAtMillis to it.value }
        val sessionsBefore = db.sleepDao().sessions().map { it.startAtMillis to it.wakeDate }

        repo.rebuild()
        repo.rebuild()

        assertEquals(before, db.readingDao().between("HEART_RATE", range.first, range.second).map { it.metric to it.measuredAtMillis to it.value })
        assertEquals(sessionsBefore, db.sleepDao().sessions().map { it.startAtMillis to it.wakeDate })
        assertEquals(48, db.sleepDao().segments(db.sleepDao().sessions().single().id).size)
    }

    @Test
    fun `Rebuild 依各次 Sync 當時的時區換算`() = runTest {
        repo.ingest(startedAt, ZoneId.of("Asia/Taipei"), captured)
        val first = db.readingDao().between("HEART_RATE", 0, Long.MAX_VALUE).first().measuredAtMillis

        repo.rebuild()

        assertEquals(first, db.readingDao().between("HEART_RATE", 0, Long.MAX_VALUE).first().measuredAtMillis)
    }

    @Test
    fun `壞訊框仍會保存，Sync 標記為解析錯誤`() = runTest {
        val bad = captured + ReceivedFrame(startedAt, byteArrayOf(1, 2, 3))
        val syncId = repo.ingest(startedAt, zone, bad)

        assertEquals(bad.size, db.rawFrameDao().forSync(syncId).size)
        val sync = db.syncDao().all().single()
        assertEquals(SyncStatus.PARSE_ERROR, sync.status)
        assertTrue(sync.errors.isNotEmpty())
        assertTrue(db.readingDao().count() > 0)
    }
}
