package com.lakshyakumar.flmw.ui.screens

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lakshyakumar.flmw.FlmwApplication
import com.lakshyakumar.flmw.data.ThemeMode
import com.lakshyakumar.flmw.service.FlmwVpnService
import com.lakshyakumar.flmw.ui.components.StatusPill
import com.lakshyakumar.flmw.ui.theme.FlmwTheme
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors
import kotlinx.coroutines.launch

/**
 * Full-screen editorial "Not now." shield displayed when a blocked app opens.
 */
class ShieldActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val blockedLabel = intent.getStringExtra(EXTRA_BLOCKED_LABEL) ?: "Distraction"
        val repo = (application as FlmwApplication).repository

        setContent {
            val state by repo.focusState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            FlmwTheme(darkTheme = dark) {
                ShieldContent(
                    blockedLabel = blockedLabel,
                    onBackToWork = {
                        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        startActivity(home)
                        finish()
                    },
                    onForceUnlock = {
                        scope.launch {
                            repo.stopFocus()
                            FlmwVpnService.stop(applicationContext)
                            finish()
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_BLOCKED_LABEL = "extra_blocked_label"
    }
}

@Composable
private fun ShieldContent(
    blockedLabel: String,
    onBackToWork: () -> Unit,
    onForceUnlock: () -> Unit
) {
    val ext = LocalFlmwColors.current
    var showFrictionInput by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusPill(text = "$blockedLabel blocked", active = true)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Not now.",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "You asked flmw to keep you locked in. Close $blockedLabel and finish what you started.",
                style = MaterialTheme.typography.bodyMedium,
                color = ext.muted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onBackToWork,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Text("  Back to work")
            }

            Spacer(modifier = Modifier.height(40.dp))
            HorizontalDivider(color = ext.hairline)
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { showFrictionInput = !showFrictionInput }) {
                Text(
                    text = "Okay, unblock me. I really want to do it. I'm free from work. Turn it off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.muted,
                    textAlign = TextAlign.Center
                )
            }

            if (showFrictionInput) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    placeholder = { Text("Type: i am done working", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onForceUnlock,
                    enabled = phrase.trim().equals("i am done working", ignoreCase = true),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm unlock")
                }
            }
        }
    }
}
