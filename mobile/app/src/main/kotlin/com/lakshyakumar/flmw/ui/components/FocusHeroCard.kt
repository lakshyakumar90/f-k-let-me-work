package com.lakshyakumar.flmw.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.lakshyakumar.flmw.ui.theme.flmwSwitchColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lakshyakumar.flmw.data.FocusState
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusHeroCard(
    focus: FocusState,
    nowMillis: Long,
    onStart: (Int?) -> Unit,
    onExtend: (Int) -> Unit,
    onStop: () -> Unit,
    onToggleStrict: (Boolean) -> Unit
) {
    val ext = LocalFlmwColors.current
    val isActive = focus.isEffectivelyActive(nowMillis)
    var showStopDialog by remember { mutableStateOf(false) }
    var stopConfirmText by remember { mutableStateOf("") }

    val remainingMs = if (isActive && !focus.indefinite && focus.endsAtMillis != null) {
        (focus.endsAtMillis - nowMillis).coerceAtLeast(0L)
    } else 0L

    val elapsedMs = if (isActive && focus.startedAtMillis != null) {
        (nowMillis - focus.startedAtMillis).coerceAtLeast(0L)
    } else 0L

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = if (isActive) {
                    "Locked · ${focus.blockedSites.size} sites, ${focus.blockedPackages.size} apps"
                } else {
                    "Standby · Ready to lock"
                },
                active = isActive
            )

            Text(
                text = when {
                    !isActive -> "00:00"
                    focus.indefinite -> "∞"
                    else -> formatDuration(remainingMs)
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = isActive, label = "heroHeadline") { active ->
            Column {
                Text(
                    text = if (active) "Stay on target." else "Ready when you are.",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (active) {
                        if (focus.indefinite) {
                            "Elapsed ${formatDuration(elapsedMs)} · Local DNS filter & app shield active."
                        } else {
                            "Local DNS filter & app shield active until timer ends."
                        }
                    } else {
                        "Pick a focus block below to silence distracting websites and Android apps locally."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.muted
                )
            }
        }

        if (isActive && !focus.indefinite && focus.totalDurationMillis > 0L) {
            Spacer(modifier = Modifier.height(18.dp))
            val progress = ((focus.totalDurationMillis - remainingMs).toFloat() / focus.totalDurationMillis.toFloat())
                .coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = ext.surfaceSubtle
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = ext.hairline)
        Spacer(modifier = Modifier.height(18.dp))

        if (!isActive) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onStart(25) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("25m")
                }
                OutlinedButton(
                    onClick = { onStart(50) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.hairline)
                ) { Text("50m") }
                OutlinedButton(
                    onClick = { onStart(120) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.hairline)
                ) { Text("2h") }
                OutlinedButton(
                    onClick = { onStart(null) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.hairline)
                ) { Text("Until I stop") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!focus.indefinite) {
                    OutlinedButton(
                        onClick = { onExtend(15) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.hairline)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("+15m")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (focus.strictFriction) {
                            stopConfirmText = ""
                            showStopDialog = true
                        } else {
                            onStop()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop focus")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Strict unblock friction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.muted
                )
                Switch(
                    colors = flmwSwitchColors(),
                    checked = focus.strictFriction,
                    onCheckedChange = onToggleStrict
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("End focus early?", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Type \"stop focus\" below to release blocked websites and apps now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.muted
                    )
                    OutlinedTextField(
                        value = stopConfirmText,
                        onValueChange = { stopConfirmText = it },
                        singleLine = true,
                        placeholder = { Text("stop focus", fontFamily = FontFamily.Monospace) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (stopConfirmText.trim().equals("stop focus", ignoreCase = true)) {
                            showStopDialog = false
                            onStop()
                        }
                    },
                    enabled = stopConfirmText.trim().equals("stop focus", ignoreCase = true)
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Keep working") }
            }
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
