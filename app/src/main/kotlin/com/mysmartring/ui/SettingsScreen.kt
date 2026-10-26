package com.mysmartring.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

private enum class Confirm(val title: String, val text: String) {
    REBUILD("重建資料", "會清除所有 Reading 與睡眠，依最新解析器從 Raw Frame 重算。Raw Frame 不受影響。"),
    UNPAIR("解除配對", "本 app 會忘記這枚戒指，之後不再自動同步。已儲存的心率、血氧與睡眠資料都會保留。戒指上尚未同步的資料也還在，重新配對後仍可取得。"),
    RESTORE("還原資料庫", "會用選取的檔案完全取代目前所有資料，目前尚未備份的資料會消失。完成後 app 會重新啟動。"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, dashboard: DashboardViewModel, onEditDashboard: () -> Unit, onBack: () -> Unit) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val ring by remember { dashboard.pairedRing }.collectAsStateWithLifecycle(null)

    val exportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::export) }
    val backupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(viewModel::backup) }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::restore) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("設定") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("儀表板", "選擇要顯示的卡片、調整順序，以及趨勢卡片的時間範圍。") {
                Button(onClick = onEditDashboard) { Text("自訂儀表板") }
            }
            val paired = ring
            Section(
                "戒指",
                if (paired == null) "目前沒有配對的戒指。配對前戒指必須先從 FixFate 解除綁定。"
                else "已配對：${paired.name}。換戒指或戒指失聯時可重新配對，不再使用時可解除配對。",
            ) {
                Button(onClick = { dashboard.pair(); onBack() }, enabled = !busy) { Text(if (paired == null) "配對戒指" else "重新配對") }
                if (paired != null) OutlinedButton(onClick = { confirm = Confirm.UNPAIR }, enabled = !busy) { Text("解除配對") }
            }
            Section("匯出", "選一個資料夾，寫出心率、血氧、血壓、步數、睡眠的 CSV 與一份完整 JSON。步數是每半小時區間的步數。不含 Raw Frame。") {
                Button(onClick = { exportFolder.launch(null) }, enabled = !busy) { Text("匯出 CSV 與 JSON") }
            }
            Section("備份與還原", "備份整個資料庫檔（含 Raw Frame）。自動備份已關閉，換手機前請先備份。") {
                Button(onClick = { backupFile.launch("mysmartring_${LocalDate.now().toString().replace("-", "")}.db") }, enabled = !busy) { Text("備份資料庫") }
                OutlinedButton(onClick = { confirm = Confirm.RESTORE }, enabled = !busy) { Text("還原資料庫") }
            }
            Section("重建", "解析器更新後，用保存的 Raw Frame 重新算出 Reading 與睡眠。") {
                OutlinedButton(onClick = { confirm = Confirm.REBUILD }, enabled = !busy) { Text("重建資料") }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        Confirm.UNPAIR -> dashboard.unpair()
                        Confirm.REBUILD -> viewModel.rebuild()
                        Confirm.RESTORE -> restoreFile.launch(arrayOf("*/*"))
                    }
                }) { Text("繼續") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("好") } },
        )
    }
}

@Composable
private fun Section(title: String, description: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(description, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            content()
        }
    }
}
