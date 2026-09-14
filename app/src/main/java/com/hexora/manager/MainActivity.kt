package com.hexora.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexora.manager.core.storage.ThemeMode
import com.hexora.manager.ui.HexoraApp
import com.hexora.manager.ui.HexoraViewModel
import com.hexora.manager.ui.theme.HexoraTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HexoraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM).value
            val systemDark = isSystemInDarkTheme()
            HexoraTheme(
                darkTheme = when (mode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                HexoraApp(viewModel = viewModel)
            }
        }
    }
}
