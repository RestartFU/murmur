package com.restartfu.murmur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.restartfu.murmur.ui.MurmurTheme
import com.restartfu.murmur.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurmurTheme {
                SetupScreen()
            }
        }
    }
}
