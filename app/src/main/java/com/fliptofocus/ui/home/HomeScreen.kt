package com.fliptofocus.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.ui.theme.IosBlue
import com.fliptofocus.ui.theme.IosGreen
import com.fliptofocus.ui.theme.IosGroup
import com.fliptofocus.ui.theme.IosOrange
import com.fliptofocus.ui.theme.IosRed
import com.fliptofocus.ui.theme.IosSecondaryLabel
import com.fliptofocus.ui.theme.IosSeparator
import com.fliptofocus.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
    val ready = accessibilityOn && overlayOn

    // Subtle one-shot entrance animation for the stats.
    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeaderBlock(ready = ready, blockingEnabled = uiState.isBlockingEnabled) }

            if (!ready) {
                item {
                    SetupCard(
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
            }

            item {
                AnimatedVisibility(
                    visible = appeared,
                    enter = fadeIn() + slideInVertically { it / 5 }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(Modifier.weight(1f), "🔥", "${uiState.streakDays}", "streak")
                        StatTile(Modifier.weight(1f), "✅", "${uiState.todayCompleted}", "today")
                        StatTile(Modifier.weight(1f), "🏆", "${uiState.longestStreak}", "best")
                    }
                }
            }

            item {
                Padded {
                    GroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Blocking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.isBlockingEnabled) {
                                        "${uiState.enabledAppCount} app(s) protected by Focus Mode"
                                    } else {
                                        "Paused - distracting apps open normally"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosSecondaryLabel
                                )
                            }
                            Switch(
                                checked = uiState.isBlockingEnabled,
                                onCheckedChange = { viewModel.setBlockingEnabled(it) }
                            )
                        }
                    }
                }
            }

            item {
                Padded {
                    GroupCard {
                        GroupRow(
                            icon = Icons.Filled.Apps,
                            iconBg = IosBlue,
                            title = "Choose apps to block",
                            subtitle = "${uiState.enabledAppCount} selected",
                            onClick = { navController.navigate("blocklist") }
                        )
                        InsetDivider()
                        GroupRow(
                            icon = Icons.Filled.Settings,
                            iconBg = IosSecondaryLabel,
                            title = "Settings",
                            subtitle = "Unlock method, difficulty, timer",
                            onClick = { navController.navigate("settings") }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT BREAKS",
                        style = MaterialTheme.typography.labelMedium,
                        color = IosSecondaryLabel,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.recentSessions.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear all", color = IosBlue)
                        }
                    }
                }
            }

            if (uiState.recentSessions.isEmpty()) {
                item {
                    Padded {
                        Text(
                            text = "No focus breaks yet. Open a blocked app to begin. Swipe a row left to delete it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosSecondaryLabel
                        )
                    }
                }
            } else {
                items(uiState.recentSessions, key = { it.id }) { session ->
                    Padded {
                        SwipeableSessionRow(
                            session = session,
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) { content() }
}

@Composable
private fun GroupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosGroup)
    ) {
        Column(content = content)
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = IosSeparator
    )
}

@Composable
private fun HeaderBlock(ready: Boolean, blockingEnabled: Boolean) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "FlipToFocus",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                !ready -> "Finish setup to activate Focus Mode"
                blockingEnabled -> "Focus Mode is on. Stay strong."
                else -> "Blocking is paused"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = IosSecondaryLabel
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier, emoji: String, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosGroup)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = IosSecondaryLabel)
        }
    }
}

@Composable
private fun GroupRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = IosSecondaryLabel)
            }
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = IosSecondaryLabel)
    }
}

@Composable
private fun SetupCard(
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    onFixAccessibility: () -> Unit,
    onFixOverlay: () -> Unit
) {
    Padded {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = IosGroup)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = IosOrange, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "Finish setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IosOrange
                    )
                }
                if (!accessibilityOn) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onFixAccessibility, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable Accessibility (app detection)")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSessionRow(session: FocusSession, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IosRed)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    ) {
        SessionRow(session)
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = IosGroup)
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
                    color = IosSecondaryLabel
                )
            }
            SessionStatusBadge(session.status)
        }
    }
}

@Composable
private fun SessionStatusBadge(status: SessionStatus) {
    val (label, color) = when (status) {
        SessionStatus.COMPLETED -> "Completed" to IosGreen
        SessionStatus.ABANDONED -> "Ended early" to IosOrange
        SessionStatus.IN_PROGRESS -> "In progress" to IosBlue
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color
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
