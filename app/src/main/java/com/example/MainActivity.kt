package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.NeonPadConsoleScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GamepadViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen awake while using phone as a PC gaming controller
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val gamepadViewModel: GamepadViewModel = viewModel()
                NeonPadConsoleScreen(
                    viewModel = gamepadViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
