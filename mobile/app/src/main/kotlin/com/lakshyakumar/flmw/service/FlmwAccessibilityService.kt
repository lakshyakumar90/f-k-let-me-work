package com.lakshyakumar.flmw.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.lakshyakumar.flmw.FlmwApplication
import com.lakshyakumar.flmw.ui.screens.ShieldActivity

/**
 * Watches foreground window transitions during an active focus session.
 * Immediately launches the editorial "Not now." ShieldActivity when a blocked app opens.
 */
class FlmwAccessibilityService : AccessibilityService() {

    private var lastBlockedPkg: String? = null
    private var lastShieldAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val app = application as? FlmwApplication ?: return
        val state = app.repository.focusState.value
        if (!state.isEffectivelyActive()) return

        if (state.blockedPackages.contains(pkg)) {
            val now = System.currentTimeMillis()
            if (pkg == lastBlockedPkg && (now - lastShieldAt) < 1200L) return
            lastBlockedPkg = pkg
            lastShieldAt = now

            val appLabel = runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)

            val shieldIntent = Intent(this, ShieldActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ShieldActivity.EXTRA_BLOCKED_LABEL, appLabel)
            }
            startActivity(shieldIntent)
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
