package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aragon.ui.MainScreen
import com.example.aragon.ui.MainViewModel
import com.example.ui.theme.AragonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AragonTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
