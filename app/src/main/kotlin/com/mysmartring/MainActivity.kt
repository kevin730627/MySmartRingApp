package com.mysmartring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysmartring.ui.DashboardScreen
import com.mysmartring.ui.DashboardViewModel
import com.mysmartring.ui.DetailScreen
import com.mysmartring.ui.SettingsScreen
import com.mysmartring.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RingApp).container
        setContent {
            MaterialTheme {
                val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                // 只有三個畫面，用一個字串代替導覽函式庫："dashboard"、"settings"、"detail/<Metric 或 SLEEP>"
                var route by rememberSaveable { mutableStateOf("dashboard") }
                BackHandler(enabled = route != "dashboard") { route = "dashboard" }
                when {
                    route == "settings" -> SettingsScreen(viewModel(factory = SettingsViewModel.factory), dashboard) { route = "dashboard" }
                    route.startsWith("detail/") -> DetailScreen(route.removePrefix("detail/"), dashboard) { route = "dashboard" }
                    else -> DashboardScreen(dashboard, onOpenDetail = { route = "detail/$it" }, onOpenSettings = { route = "settings" })
                }
            }
        }
    }
}
