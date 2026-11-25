package com.lakshyakumar.flmw

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lakshyakumar.flmw.data.ThemeMode
import com.lakshyakumar.flmw.ui.screens.FlmwApp
import com.lakshyakumar.flmw.ui.state.FocusViewModel
import com.lakshyakumar.flmw.ui.theme.FlmwTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as FlmwApplication
            val viewModel: FocusViewModel = viewModel(
                factory = FocusViewModel.provideFactory(app.repository, applicationContext)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (uiState.focus.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            FlmwTheme(darkTheme = useDarkTheme) {
                FlmwApp(
                    viewModel = viewModel,
                    uiState = uiState,
                    useDarkTheme = useDarkTheme
                )
            }
        }
    }
}
