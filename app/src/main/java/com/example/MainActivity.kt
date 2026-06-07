package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.BaseballRepository
import com.example.ui.BaseballViewModel
import com.example.ui.BaseballViewModelFactory
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup transparent status and navigation bars standard layout in edge-to-edge Compose
        enableEdgeToEdge()

        // Core data dependency injection (Room + Repository pattern)
        val repository = BaseballRepository.getInstance(applicationContext)
        val factory = BaseballViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[BaseballViewModel::class.java]

        setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}
