package com.mysmartring.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class InvalidBackupException(message: String) : Exception(message)

/** 資料庫檔案的備份與還原 */
class DatabaseBackup(private val context: Context, private val db: AppDatabase, private val name: String = "ring.db") {
    private val dbFile get() = context.getDatabasePath(name)

    /** 先把 WAL 內容併回主檔，再複製整個資料庫檔 */
    fun backupTo(out: OutputStream) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        dbFile.inputStream().use { it.copyTo(out) }
    }

    /**
     * 以 [input] 的內容取代目前的資料庫。先存成暫存檔並驗證，通過才關閉並取代；
     * 驗證失敗時目前的資料完全不動。還原後需要重新啟動 app，才會重新開啟資料庫。
     */
    fun restoreFrom(input: InputStream) {
        val temp = File(context.cacheDir, "restore.db")
        try {
            temp.outputStream().use { input.copyTo(it) }
            validate(temp)
            db.close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            temp.copyTo(dbFile, overwrite = true)
        } finally {
            temp.delete()
        }
    }

    private fun validate(file: File) {
        val header = file.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (!header.contentEquals("SQLite format 3\u0000".toByteArray())) throw InvalidBackupException("不是 SQLite 資料庫檔")
        val check = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw InvalidBackupException("無法開啟資料庫檔：${e.message}")
        }
        check.use {
            if (it.version != VERSION) throw InvalidBackupException("資料庫版本 ${it.version} 與目前的 $VERSION 不符")
            val tables = it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            val missing = REQUIRED_TABLES - tables
            if (missing.isNotEmpty()) throw InvalidBackupException("缺少資料表：$missing")
        }
    }

    companion object {
        const val VERSION = 1
        private val REQUIRED_TABLES = setOf("sync", "raw_frame", "reading", "sleep_session", "sleep_segment", "paired_ring", "dashboard_card")
    }
}
