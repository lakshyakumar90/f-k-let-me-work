package com.lakshyakumar.flmw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lakshyakumar.flmw.ui.state.PermissionStatus
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors

@Composable
fun PermissionSetupCard(
    status: PermissionStatus,
    onRequestVpn: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    val ext = LocalFlmwColors.current

    EditorialCard {
        Text(
            text = "Device Shields",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Grant these native Android permissions once so flmw can block sites and apps without root.",
            style = MaterialTheme.typography.bodyMedium,
            color = ext.muted
        )
        Spacer(modifier = Modifier.height(16.dp))

        PermissionRow(
            title = "Local VPN DNS Filter",
            subtitle = "Blocks websites on-device via VpnService",
            granted = status.vpnGranted,
            actionLabel = "Allow",
            onAction = onRequestVpn
        )
        HorizontalDivider(color = ext.hairline)
        PermissionRow(
            title = "App Shield Accessibility",
            subtitle = "Detects blocked apps opening during focus",
            granted = status.accessibilityGranted,
            actionLabel = "Enable",
            onAction = onOpenAccessibility
        )
        HorizontalDivider(color = ext.hairline)
        PermissionRow(
            title = "Display Over Other Apps",
            subtitle = "Shows the Not now. screen immediately",
            granted = status.overlayGranted,
            actionLabel = "Grant",
            onAction = onOpenOverlay
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val ext = LocalFlmwColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) ext.ok else ext.muted,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = ext.muted)
        }
        if (!granted) {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel)
            }
        } else {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelMedium,
                color = ext.ok
            )
        }
    }
}
