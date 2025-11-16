package com.example.refocus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.refocus.ui.theme.RefocusTheme
import com.example.refocus.navigation.RefocusNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RefocusTheme {
                RefocusNavHost()
            }
        }
    }
}