package com.hydrosense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hydrosense.app.ui.AppRoot
import com.hydrosense.app.ui.theme.HydroSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HydroSenseTheme { AppRoot() } }
    }
}
