package com.lakshyakumar.flmw.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.lakshyakumar.flmw.ui.theme.flmwSwitchColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lakshyakumar.flmw.ui.components.EditorialCard
import com.lakshyakumar.flmw.ui.state.FlmwUiState
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors

private val DAY_LABELS = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulesScreen(
    uiState: FlmwUiState,
    onAddSchedule: (String, Int, Int, Int, List<Int>) -> Unit,
    onToggleSchedule: (String) -> Unit,
    onRemoveSchedule: (String) -> Unit
) {
    val ext = LocalFlmwColors.current
    var label by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("09") }
    var startMin by remember { mutableStateOf("00") }
    var durationMin by remember { mutableStateOf("120") }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EditorialCard {
                Text("New Schedule", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Label (e.g. Deep work sprint)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = startMin,
                        onValueChange = { startMin = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationMin,
                        onValueChange = { durationMin = it.filter(Char::isDigit).take(3) },
                        label = { Text("Duration (m)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEach { (dayNum, short) ->
                        val picked = selectedDays.contains(dayNum)
                        FilterChip(
                            selected = picked,
                            onClick = {
                                selectedDays = if (picked) selectedDays - dayNum else selectedDays + dayNum
                            },
                            label = { Text(short) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val h = startHour.toIntOrNull() ?: 9
                        val m = startMin.toIntOrNull() ?: 0
                        val d = durationMin.toIntOrNull() ?: 60
                        onAddSchedule(label, h, m, d, selectedDays.toList())
                        label = ""
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Schedule")
                }
            }
        }

        items(uiState.focus.schedules, key = { it.id }) { sched ->
            EditorialCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = sched.label, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "%02d:%02d · %dm · %s".format(
                                sched.hour,
                                sched.minute,
                                sched.durationMinutes,
                                formatDays(sched.daysOfWeek)
                            ),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.muted
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            colors = flmwSwitchColors(),
                            checked = sched.enabled,
                            onCheckedChange = { onToggleSchedule(sched.id) }
                        )
                        IconButton(onClick = { onRemoveSchedule(sched.id) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete schedule",
                                tint = ext.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDays(days: List<Int>): String {
    if (days == listOf(1, 2, 3, 4, 5)) return "Mon–Fri"
    if (days.size == 7) return "Every day"
    val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    return days.mapNotNull { names[it] }.joinToString(", ")
}
