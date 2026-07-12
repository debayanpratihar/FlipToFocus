package com.fliptofocus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReadyGreen = Color(0xFF2E7D32)
private val WarnAmber = Color(0xFFEF6C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read the two special permissions whenever the screen resumes (the user may toggle them in
    // system settings and return), so the status card is always accurate.
    var accessibilityOn by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }
    var overlayOn by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = PermissionUtils.isAccessibilityServiceEnabled(context)
                overlayOn = PermissionUtils.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("FlipToFocus") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FocusModeStatusCard(
                    ready = accessibilityOn && overlayOn,
                    accessibilityOn = accessibilityOn,
                    overlayOn = overlayOn,
                    onFixAccessibility = {
                        runCatching { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) }
                    },
                    onFixOverlay = {
                        runCatching { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) }
                    }
                )
            }

            item {
                BlockingToggleCard(
                    isEnabled = uiState.isBlockingEnabled,
                    enabledAppCount = uiState.enabledAppCount,
                    onToggle = { enabled -> viewModel.setBlockingEnabled(enabled) }
                )
            }

            item {
                NavRow(
                    title = "Choose apps to block",
                    subtitle = "${uiState.enabledAppCount} app(s) currently blocked",
                    icon = Icons.Filled.List,
                    onClick = { navController.navigate("blocklist") }
                )
            }

            item {
                NavRow(
                    title = "Settings",
                    subtitle = "Unlock method, timer, sensitivity",
                    icon = Icons.Filled.Settings,
                    onClick = { navController.navigate("settings") }
                )
            }

            item {
                Text(
                    text = "Recent focus breaks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.recentSessions.isEmpty()) {
                item {
                    Text(
                        text = "No focus breaks yet. When you open a blocked app, your mindful breaks will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.recentSessions, key = { it.id }) { session ->
                    SessionRow(session)
                }
            }
        }
    }
}

@Composable
private fun FocusModeStatusCard(
    ready: Boolean,
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    onFixAccessibility: () -> Unit,
    onFixOverlay: () -> Unit
) {
    val accent = if (ready) ReadyGreen else WarnAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = if (ready) "Focus Mode is ready" else "Finish setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        text = if (ready) {
                            "FlipToFocus is watching for blocked apps."
                        } else {
                            "FlipToFocus needs a couple of permissions to protect you."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!accessibilityOn) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onFixAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Accessibility (foreground detection)")
                }
            }
            if (!overlayOn) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onFixOverlay, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow Display over other apps")
                }
            }
        }
    }
}

@Composable
private fun BlockingToggleCard(
    isEnabled: Boolean,
    enabledAppCount: Int,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blocking enabled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isEnabled) {
                        "$enabledAppCount distracting app(s) are protected by Focus Mode."
                    } else {
                        "Blocking is off. Distracting apps open normally."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDuration(session.challengeDurationMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatSessionTime(session.startTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SessionStatusBadge(session.status)
        }
    }
}

@Composable
private fun SessionStatusBadge(status: SessionStatus) {
    val (label, color) = when (status) {
        SessionStatus.COMPLETED -> "Completed" to ReadyGreen
        SessionStatus.ABANDONED -> "Ended early" to WarnAmber
        SessionStatus.IN_PROGRESS -> "In progress" to MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatSessionTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s challenge" else "${seconds}s challenge"
}
