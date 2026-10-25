package com.mysmartring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysmartring.data.CardRange
import com.mysmartring.data.CardType
import com.mysmartring.data.DashboardLayout

/** 自訂儀表板：選 Card、排序、趨勢 Card 的時間範圍。每個修改立刻存檔，不需要儲存鈕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditorScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val list = cards.orEmpty()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("自訂儀表板") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(list) { index, card ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cardTitle(card), style = MaterialTheme.typography.titleMedium)
                        if (card.type == CardType.TREND) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CardRange.entries.forEach { r ->
                                    FilterChip(selected = card.range == r, onClick = { viewModel.setCardRange(index, r) }, label = { Text(r.label) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.moveCard(index, -1) }, enabled = index > 0) { Text("上移") }
                            TextButton(onClick = { viewModel.moveCard(index, 1) }, enabled = index < list.lastIndex) { Text("下移") }
                            TextButton(onClick = { viewModel.removeCard(index) }, enabled = list.size > 1) { Text("刪除") }
                        }
                    }
                }
            }
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("新增卡片") } }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("新增卡片") },
            text = {
                Column {
                    DashboardLayout.available.forEach { card ->
                        TextButton(onClick = { viewModel.addCard(card); adding = false }) { Text(cardTitle(card)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
}
