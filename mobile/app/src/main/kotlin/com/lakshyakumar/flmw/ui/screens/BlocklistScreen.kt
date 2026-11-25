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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.lakshyakumar.flmw.ui.theme.flmwSwitchColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private val SITE_SUGGESTIONS = listOf("tiktok.com", "twitch.tv", "linkedin.com", "netflix.com", "facebook.com")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocklistScreen(
    uiState: FlmwUiState,
    onAddSite: (String) -> Unit,
    onRemoveSite: (String) -> Unit,
    onTogglePackage: (String) -> Unit
) {
    val ext = LocalFlmwColors.current
    var tab by remember { mutableIntStateOf(0) }
    var domainInput by remember { mutableStateOf("") }
    var appSearch by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Websites (${uiState.focus.blockedSites.size})")
            }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Apps (${uiState.focus.blockedPackages.size})")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (tab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    EditorialCard {
                        Text("Add Website", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = domainInput,
                                onValueChange = { domainInput = it },
                                placeholder = { Text("x.com", fontFamily = FontFamily.Monospace) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onAddSite(domainInput)
                                    domainInput = ""
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(52.dp)
                            ) { Text("Add") }
                        }

                        val available = SITE_SUGGESTIONS.filterNot { uiState.focus.blockedSites.contains(it) }
                        if (available.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                available.forEach { suggestion ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onAddSite(suggestion) },
                                        label = { Text("+ $suggestion", style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    EditorialCard {
                        Text("Blocked Domains", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.focus.blockedSites.isEmpty()) {
                            Text(
                                "No domains added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.muted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            uiState.focus.blockedSites.forEachIndexed { index, domain ->
                                if (index > 0) HorizontalDivider(color = ext.hairline)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = domain,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = { onRemoveSite(domain) }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove $domain",
                                            tint = ext.muted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val filteredApps = remember(uiState.installedApps, appSearch) {
                if (appSearch.isBlank()) uiState.installedApps
                else uiState.installedApps.filter {
                    it.label.contains(appSearch, ignoreCase = true) ||
                        it.packageName.contains(appSearch, ignoreCase = true)
                }
            }

            OutlinedTextField(
                value = appSearch,
                onValueChange = { appSearch = it },
                placeholder = { Text("Search installed apps...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val blocked = uiState.focus.blockedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.muted
                            )
                        }
                        Switch(
                            colors = flmwSwitchColors(),
                            checked = blocked,
                            onCheckedChange = { onTogglePackage(app.packageName) }
                        )
                    }
                    HorizontalDivider(color = ext.hairline)
                }
            }
        }
    }
}
