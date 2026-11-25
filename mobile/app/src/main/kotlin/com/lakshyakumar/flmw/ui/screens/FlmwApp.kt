package com.lakshyakumar.flmw.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.isSystemInDarkTheme
import com.lakshyakumar.flmw.ui.state.FlmwUiState
import com.lakshyakumar.flmw.ui.state.FocusViewModel
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlmwApp(
    viewModel: FocusViewModel,
    uiState: FlmwUiState,
    useDarkTheme: Boolean
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val ext = LocalFlmwColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Refresh permission statuses whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshPermissions()
        }
    }

    val requestVpnPermission = {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            viewModel.refreshPermissions()
        }
    }

    val openAccessibilitySettings = {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    val openOverlaySettings = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    val isActive = uiState.focus.isEffectivelyActive(uiState.nowMillis)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isActive) MaterialTheme.colorScheme.primary else ext.muted)
                        )
                        Text(
                            text = "flmw",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleTheme(systemDark) }) {
                        Icon(
                            imageVector = if (useDarkTheme) {
                                Icons.Rounded.LightMode
                            } else {
                                Icons.Rounded.DarkMode
                            },
                            contentDescription = if (useDarkTheme) "Switch to light mode" else "Switch to dark mode"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                    label = { Text("Focus") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                    label = { Text("Blocklist") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    label = { Text("Schedules") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> FocusScreen(
                    uiState = uiState,
                    onStartFocus = { mins ->
                        val prep = VpnService.prepare(context)
                        if (prep != null) vpnLauncher.launch(prep)
                        viewModel.startFocus(mins)
                    },
                    onExtendFocus = viewModel::extendFocus,
                    onStopFocus = viewModel::stopFocus,
                    onToggleStrict = viewModel::setStrictFriction,
                    onRequestVpn = requestVpnPermission,
                    onOpenAccessibility = openAccessibilitySettings,
                    onOpenOverlay = openOverlaySettings
                )
                1 -> BlocklistScreen(
                    uiState = uiState,
                    onAddSite = viewModel::addSite,
                    onRemoveSite = viewModel::removeSite,
                    onTogglePackage = viewModel::togglePackage
                )
                2 -> SchedulesScreen(
                    uiState = uiState,
                    onAddSchedule = viewModel::addSchedule,
                    onToggleSchedule = viewModel::toggleSchedule,
                    onRemoveSchedule = viewModel::removeSchedule
                )
            }
        }
    }
}
