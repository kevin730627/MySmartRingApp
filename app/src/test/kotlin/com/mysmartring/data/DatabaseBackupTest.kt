package com.mysmartring.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var backup: DatabaseBackup

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "ring.db").allowMainThreadQueries().build()

    private suspend fun addReading(at: Long) {
        val syncId = db.syncDao().insert(SyncEntity(startedAtMillis = at, zoneId = "UTC"))
        db.readingDao().insertAll(listOf(ReadingEntity(metric = "HEART_RATE", measuredAtMillis = at, value = 80, syncId = syncId)))
    }

    @Before
    fun setUp() {
        context.deleteDatabase("ring.db")
        db = open()
        backup = DatabaseBackup(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("ring.db")
    }

    @Test
    fun `備份後再還原，資料回到備份當時的狀態`() = runTest {
        addReading(1000)
        val bytes = ByteArrayOutputStream().also { backup.backupTo(it) }.toByteArray()
        addReading(2000)
        assertEquals(2, db.readingDao().count())

        backup.restoreFrom(ByteArrayInputStream(bytes))

        db = open()
        assertEquals(1, db.readingDao().count())
    }

    @Test
    fun `不是 SQLite 的檔案會被拒絕，且目前資料不動`() = runTest {
        addReading(1000)
        assertFailsWith<InvalidBackupException> { backup.restoreFrom(ByteArrayInputStream("hello".toByteArray())) }
        assertEquals(1, db.readingDao().count())
    }

    @Test
    fun `缺少必要資料表的資料庫會被拒絕，且目前資料不動`() = runTest {
        addReading(1000)
        val other = context.getDatabasePath("other.db").also { it.parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(other, null).use {
            it.execSQL("CREATE TABLE foo(id INTEGER)")
            it.version = DatabaseBackup.VERSION
        }
        assertFailsWith<InvalidBackupException> { backup.restoreFrom(other.inputStream()) }
        assertEquals(1, db.readingDao().count())
    }
}
