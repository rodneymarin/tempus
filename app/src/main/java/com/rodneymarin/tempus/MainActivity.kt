package com.rodneymarin.tempus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rodneymarin.tempus.ui.navigation.TempusNavHost
import com.rodneymarin.tempus.ui.theme.TempusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempusTheme {
                TempusNavHost()
            }
        }
    }
}
