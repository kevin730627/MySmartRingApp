package com.mysmartring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysmartring.ui.DashboardScreen
import com.mysmartring.ui.DashboardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RingApp).container
        setContent {
            MaterialTheme {
                DashboardScreen(viewModel(factory = DashboardViewModel.factory(container)))
            }
        }
    }
}
