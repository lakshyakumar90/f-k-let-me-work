package com.lakshyakumar.flmw.ui.state

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lakshyakumar.flmw.data.FocusRepository
import com.lakshyakumar.flmw.data.FocusState
import com.lakshyakumar.flmw.data.InstalledAppItem
import com.lakshyakumar.flmw.service.FlmwAccessibilityService
import com.lakshyakumar.flmw.service.FlmwVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val vpnGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false
)

data class FlmwUiState(
    val focus: FocusState = FocusState(),
    val nowMillis: Long = System.currentTimeMillis(),
    val installedApps: List<InstalledAppItem> = emptyList(),
    val permissions: PermissionStatus = PermissionStatus(),
    val customPrivateDns: Boolean = false
)

class FocusViewModel(
    private val repository: FocusRepository,
    private val appContext: Context
) : ViewModel() {

    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private val installedAppsFlow = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val permissionsFlow = MutableStateFlow(readPermissions())

    val uiState: StateFlow<FlmwUiState> = combine(
        repository.focusState,
        nowFlow,
        installedAppsFlow,
        permissionsFlow
    ) { focus, now, apps, perms ->
        FlmwUiState(
            focus = focus,
            nowMillis = now,
            installedApps = apps,
            permissions = perms,
            customPrivateDns = FlmwVpnService.hasCustomPrivateDns(appContext)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FlmwUiState(
            focus = repository.focusState.value,
            permissions = permissionsFlow.value,
            customPrivateDns = FlmwVpnService.hasCustomPrivateDns(appContext)
        )
    )

    init {
        startTicker()
        loadInstalledApps()
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                nowFlow.value = now
                val current = repository.focusState.value
                if (current.active && !current.indefinite && current.endsAtMillis != null && now >= current.endsAtMillis) {
                    repository.stopFocus()
                    FlmwVpnService.stop(appContext)
                }
                delay(1_000L)
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = appContext.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(launcherIntent, 0)
            val items = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == appContext.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString() ?: pkg
                    InstalledAppItem(packageName = pkg, label = label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            installedAppsFlow.value = items
        }
    }

    fun refreshPermissions() {
        permissionsFlow.value = readPermissions()
    }

    private fun readPermissions(): PermissionStatus {
        val vpnReady = VpnService.prepare(appContext) == null
        val overlayReady = Settings.canDrawOverlays(appContext)
        val expectedComponent = ComponentName(appContext, FlmwAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accessibilityReady = enabledServices.contains(expectedComponent, ignoreCase = true)
        return PermissionStatus(
            vpnGranted = vpnReady,
            accessibilityGranted = accessibilityReady,
            overlayGranted = overlayReady
        )
    }

    fun startFocus(durationMinutes: Int?) {
        viewModelScope.launch {
            repository.startFocus(durationMinutes)
            if (VpnService.prepare(appContext) == null) {
                FlmwVpnService.start(appContext)
            }
            refreshPermissions()
        }
    }

    fun extendFocus(extraMinutes: Int) {
        viewModelScope.launch { repository.extendFocus(extraMinutes) }
    }

    fun stopFocus() {
        viewModelScope.launch {
            repository.stopFocus()
            FlmwVpnService.stop(appContext)
        }
    }

    fun setStrictFriction(enabled: Boolean) {
        viewModelScope.launch { repository.setStrictFriction(enabled) }
    }

    fun toggleTheme(systemDark: Boolean) {
        viewModelScope.launch { repository.toggleTheme(systemDark) }
    }

    fun addSite(domain: String) {
        viewModelScope.launch { repository.addSite(domain) }
    }

    fun removeSite(domain: String) {
        viewModelScope.launch { repository.removeSite(domain) }
    }

    fun togglePackage(packageName: String) {
        viewModelScope.launch { repository.togglePackage(packageName) }
    }

    fun addSchedule(label: String, hour: Int, minute: Int, durationMinutes: Int, days: List<Int>) {
        viewModelScope.launch { repository.addSchedule(label, hour, minute, durationMinutes, days) }
    }

    fun toggleSchedule(id: String) {
        viewModelScope.launch { repository.toggleSchedule(id) }
    }

    fun removeSchedule(id: String) {
        viewModelScope.launch { repository.removeSchedule(id) }
    }

    companion object {
        fun provideFactory(
            repository: FocusRepository,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FocusViewModel(repository, context) as T
            }
        }
    }
}
