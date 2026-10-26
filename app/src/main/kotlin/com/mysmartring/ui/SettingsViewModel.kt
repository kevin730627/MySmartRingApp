package com.mysmartring.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mysmartring.RingApp
import com.mysmartring.data.DatabaseBackup
import com.mysmartring.data.Exporter
import com.mysmartring.data.SleepExport
import com.mysmartring.ring.Metric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** 設定頁的動作：匯出、備份與還原、重建。結果以一句話顯示在 [message] */
class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val container get() = (app as RingApp).container
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun dismissMessage() {
        _message.value = null
    }

    fun rebuild() = run("重建") {
        container.repository.rebuild()
        "已依最新解析器從 Raw Frame 重建所有資料"
    }

    /** 在使用者選的資料夾裡寫出各 Metric 的 CSV、睡眠 CSV 與 JSON */
    fun export(tree: Uri) = run("匯出") {
        val db = container.database
        val zone = ZoneId.systemDefault()
        val heart = db.readingDao().all(Metric.HEART_RATE.name)
        val oxygen = db.readingDao().all(Metric.BLOOD_OXYGEN.name)
        val steps = db.readingDao().all(Metric.STEPS.name)
        val sleeps = db.sleepDao().sessions().map { SleepExport(it, db.sleepDao().segments(it.id)) }
        val stamp = LocalDate.now(zone).toString().replace("-", "")
        val files = mapOf(
            "heart_rate_$stamp.csv" to ("text/csv" to "﻿" + Exporter.readingsCsv(heart, zone)),
            "blood_oxygen_$stamp.csv" to ("text/csv" to "﻿" + Exporter.readingsCsv(oxygen, zone)),
            // 步數的「數值」是該半小時區間的步數，時間點為區間起點
            "steps_$stamp.csv" to ("text/csv" to "﻿" + Exporter.readingsCsv(steps, zone)),
            "sleep_$stamp.csv" to ("text/csv" to "﻿" + Exporter.sleepCsv(sleeps, zone)),
            "mysmartring_$stamp.json" to ("application/json" to Exporter.json(heart + oxygen + steps, sleeps, zone)),
        )
        val resolver = app.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for ((name, content) in files) {
            val doc = DocumentsContract.createDocument(resolver, parent, content.first, name) ?: error("無法建立 $name")
            resolver.openOutputStream(doc, "wt")!!.use { it.write(content.second.toByteArray()) }
        }
        "已匯出 ${files.size} 個檔案"
    }

    fun backup(target: Uri) = run("備份") {
        app.contentResolver.openOutputStream(target, "wt")!!.use { DatabaseBackup(app, container.database).backupTo(it) }
        "已備份資料庫檔"
    }

    /** 還原成功後資料庫已關閉，必須重新啟動 app 才能再開啟 */
    fun restore(source: Uri) = run("還原") {
        app.contentResolver.openInputStream(source)!!.use { DatabaseBackup(app, container.database).restoreFrom(it) }
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(launch)
        Process.killProcess(Process.myPid())
        "已還原"
    }

    private fun run(label: String, block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "$label 失敗：${e.message ?: e::class.java.simpleName}"
            }
            _busy.value = false
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}
