package com.undistractme.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undistractme.sensor.ChallengeState
import java.util.Locale

/**
 * Fully self-contained dark overlay shown on top of a distracting app while the
 * offline sensor challenge is in progress. It intentionally establishes its own
 * [MaterialTheme] dark color scheme so it does not depend on the hosting activity
 * theme (this is rendered from a service window, not an Activity).
 *
 * Compliance note: the overlay enforces ONLY the sensor challenge. Back is consumed
 * so the overlay is not trivially dismissed, but a low-emphasis "End session early"
 * control (with confirmation) always preserves user autonomy via [onEndEarly].
 *
 * The confirmation is rendered INLINE inside this same overlay ComposeView rather
 * than as a Compose Dialog/AlertDialog. A Dialog would spin up its own separate
 * top-level window using this ComposeView's application context, which has no
 * Activity token and would crash the service overlay with a
 * WindowManager.BadTokenException. Keeping the confirmation in-window means the
 * single WindowManager window OverlayManager added is the only one that ever
 * exists.
 */

// --- Overlay-local palette (independent of the app/activity theme) ---
private val OverlayBackground = Color(0xFF0B0F14)
private val OverlaySurface = Color(0xFF161B22)
private val OverlayPrimary = Color(0xFF6EE7B7)
private val OverlayOnBackground = Color(0xFFE6EAF0)
private val OverlayOnSurfaceVariant = Color(0xFF9AA4B2)
private val OverlayTrack = Color(0xFF2A313B)
private val PositionValidColor = Color(0xFF34D399)
private val PositionInvalidColor = Color(0xFFFBBF24)

private val OverlayColorScheme = darkColorScheme(
    primary = OverlayPrimary,
    onPrimary = Color(0xFF07130D),
    background = OverlayBackground,
    onBackground = OverlayOnBackground,
    surface = OverlaySurface,
    onSurface = OverlayOnBackground,
    onSurfaceVariant = OverlayOnSurfaceVariant,
    surfaceVariant = OverlayTrack
)

@Composable
fun FocusOverlayScreen(
    state: ChallengeState,
    triggeringAppLabel: String,
    onEndEarly: () -> Unit
) {
    // Consume the system back gesture/button: the overlay must not be dismissed by
    // back. The ONLY sanctioned exit is the confirmed "End session early" control.
    BackHandler(enabled = true) { /* intentionally consume, no dismissal */ }

    var showEndDialog by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = OverlayColorScheme) {
        // A single Box hosts both the main content and the inline confirmation so the
        // confirmation overlays the challenge UI without opening a second window.
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = OverlayBackground
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Take a mindful break",
                            color = OverlayOnBackground,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$triggeringAppLabel is paused for now. Rest your phone and let the timer do its thing.",
                            color = OverlayOnSurfaceVariant,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        CountdownRing(
                            progress = state.progress,
                            remainingMillis = state.remainingMillis,
                            isComplete = state.isComplete
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        PositionStatusChip(isPositionValid = state.isPositionValid)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Moving or lifting your phone resets the timer.",
                            color = OverlayOnSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 320.dp)
                        )
                    }

                    // Low-emphasis autonomy control, anchored at the bottom.
                    TextButton(
                        onClick = { showEndDialog = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Text(
                            text = "End session early",
                            color = OverlayOnSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (showEndDialog) {
                EndSessionConfirmation(
                    triggeringAppLabel = triggeringAppLabel,
                    onConfirm = {
                        showEndDialog = false
                        onEndEarly()
                    },
                    onDismiss = { showEndDialog = false }
                )
            }
        }
    }
}

/**
 * Inline confirmation rendered within the same overlay window (NOT a Compose
 * Dialog). A dimmed, click-consuming scrim covers the challenge UI and a centered
 * card offers "Keep going" / "End session". Because this lives inside the overlay's
 * ComposeView, no extra WindowManager window/token is required.
 */
@Composable
private fun EndSessionConfirmation(
    triggeringAppLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Tapping the scrim dismisses the confirmation; also swallows taps so
            // they never reach the challenge UI behind it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 360.dp)
                // Consume taps on the card itself so it does not dismiss.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(20.dp),
            color = OverlaySurface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "End this session?",
                    color = OverlayOnBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can stop the break at any time. This session will be recorded as abandoned and $triggeringAppLabel will open normally.",
                    color = OverlayOnSurfaceVariant,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Keep going",
                            color = OverlayPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = "End session",
                            color = PositionInvalidColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(
    progress: Float,
    remainingMillis: Long,
    isComplete: Boolean
) {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Static track behind the progress ring.
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = OverlayTrack,
            strokeWidth = 10.dp
        )
        // Active progress ring.
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = if (isComplete) PositionValidColor else OverlayPrimary,
            strokeWidth = 10.dp
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatMillis(remainingMillis),
                color = OverlayOnBackground,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isComplete) "All done" else "remaining",
                color = OverlayOnSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PositionStatusChip(isPositionValid: Boolean) {
    val accent = if (isPositionValid) PositionValidColor else PositionInvalidColor
    val label = if (isPositionValid) {
        "Phone is face-down â€” hold still"
    } else {
        "Place your phone face-down and keep still"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .padding(PaddingValues(horizontal = 18.dp, vertical = 12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Formats a millisecond duration as mm:ss (non-negative, clamped at zero).
 */
private fun formatMillis(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
