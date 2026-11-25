package com.lakshyakumar.flmw.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lakshyakumar.flmw.ui.components.FocusHeroCard
import com.lakshyakumar.flmw.ui.components.PermissionSetupCard
import com.lakshyakumar.flmw.ui.components.StatTile
import com.lakshyakumar.flmw.ui.state.FlmwUiState
import com.lakshyakumar.flmw.ui.theme.LocalFlmwColors

@Composable
fun FocusScreen(
    uiState: FlmwUiState,
    onStartFocus: (Int?) -> Unit,
    onExtendFocus: (Int) -> Unit,
    onStopFocus: () -> Unit,
    onToggleStrict: (Boolean) -> Unit,
    onRequestVpn: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    val ext = LocalFlmwColors.current
    val focus = uiState.focus

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FocusHeroCard(
                focus = focus,
                nowMillis = uiState.nowMillis,
                onStart = onStartFocus,
                onExtend = onExtendFocus,
                onStop = onStopFocus,
                onToggleStrict = onToggleStrict
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    label = "Sites",
                    value = focus.blockedSites.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Apps",
                    value = focus.blockedPackages.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Focused",
                    value = "${focus.focusedMinutesToday}m",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            PermissionSetupCard(
                status = uiState.permissions,
                onRequestVpn = onRequestVpn,
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlay = onOpenOverlay
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "flmw Android · Built for Lakshya Kumar (lakshyakumar.in)",
                style = MaterialTheme.typography.labelSmall,
                color = ext.muted
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
