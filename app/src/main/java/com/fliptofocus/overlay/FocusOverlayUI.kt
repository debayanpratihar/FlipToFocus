package com.fliptofocus.overlay

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fliptofocus.domain.model.ChallengeType
import com.fliptofocus.sensor.ChallengeState
import java.util.Locale

/**
 * Fully self-contained dark overlay shown on top of a distracting app while an offline unlock
 * challenge is in progress. It establishes its own [MaterialTheme] dark color scheme so it does not
 * depend on the hosting activity theme (this is rendered from a service window, not an Activity).
 *
 * It renders whichever challenge [ChallengeState.type] is active (flip / wait / shake / math).
 *
 * Compliance: the overlay enforces ONLY the challenge. Back is consumed so it is not trivially
 * dismissed, but a low-emphasis "End session early" control (with confirmation) always preserves
 * user autonomy via [onEndEarly]. The confirmation is rendered INLINE (not a Compose Dialog) to
 * avoid spawning a second window from a non-Activity context.
 */

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

private data class ChallengeCopy(val title: String, val subtitle: String)

private fun copyFor(type: ChallengeType, app: String): ChallengeCopy {
    val subtitle = when (type) {
        ChallengeType.FLIP -> "$app is locked. Place your phone face-down and hold still to unlock."
        ChallengeType.WAIT -> "$app is locked. Wait for the timer to finish to unlock."
        ChallengeType.SHAKE -> "$app is locked. Shake your phone to unlock."
        ChallengeType.MATH -> "$app is locked. Solve the problems to unlock."
    }
    return ChallengeCopy(title = "Focus Mode active", subtitle = subtitle)
}

@Composable
fun FocusOverlayScreen(
    state: ChallengeState,
    triggeringAppLabel: String,
    onEndEarly: () -> Unit,
    onMathAnswer: (Int) -> Unit = {},
    onLeaveToHome: () -> Unit = {}
) {
    // Consume the system back gesture/button so the overlay is not dismissed by Back. The ONLY
    // sanctioned exit is the confirmed "End session early" control.
    BackHandler(enabled = true) { /* intentionally consume, no dismissal */ }

    var showEndDialog by remember { mutableStateOf(false) }
    val copy = copyFor(state.type, triggeringAppLabel)

    MaterialTheme(colorScheme = OverlayColorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0B0F14),
                                Color(0xFF161B2E),
                                Color(0xFF0B0F14)
                            )
                        )
                    )
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
                            text = copy.title,
                            color = OverlayOnBackground,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = copy.subtitle,
                            color = OverlayOnSurfaceVariant,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )
                        Spacer(modifier = Modifier.height(36.dp))

                        when (state.type) {
                            ChallengeType.FLIP -> {
                                ProgressRing(
                                    progress = state.progress,
                                    isComplete = state.isComplete,
                                    bigText = formatMillis(state.remainingMillis),
                                    smallText = if (state.isComplete) "All done" else "remaining"
                                )
                                Spacer(modifier = Modifier.height(36.dp))
                                PositionStatusChip(isPositionValid = state.isPositionValid)
                                Spacer(modifier = Modifier.height(16.dp))
                                Hint("Moving or lifting your phone resets the timer.")
                            }
                            ChallengeType.WAIT -> {
                                ProgressRing(
                                    progress = state.progress,
                                    isComplete = state.isComplete,
                                    bigText = formatMillis(state.remainingMillis),
                                    smallText = if (state.isComplete) "All done" else "remaining"
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Hint("Relax - the timer keeps running on its own.")
                            }
                            ChallengeType.SHAKE -> {
                                ProgressRing(
                                    progress = state.progress,
                                    isComplete = state.isComplete,
                                    bigText = "${state.shakeCount}",
                                    smallText = "of ${state.shakeTarget} shakes"
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Hint("Give your phone a firm shake to count each one.")
                            }
                            ChallengeType.MATH -> {
                                MathChallenge(state = state, onAnswer = onMathAnswer)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // For non-physical challenges, let the user step away and use a DIFFERENT
                        // app while this one stays locked (they don't have to stare at the screen).
                        if (state.type == ChallengeType.WAIT || state.type == ChallengeType.MATH) {
                            OutlinedButton(onClick = onLeaveToHome) {
                                Text(text = "Use another app", fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        TextButton(onClick = { showEndDialog = true }) {
                            Text(
                                text = "End session early",
                                color = OverlayOnSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
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

@Composable
private fun MathChallenge(
    state: ChallengeState,
    onAnswer: (Int) -> Unit
) {
    Text(
        text = state.mathQuestion ?: "",
        color = OverlayOnBackground,
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(28.dp))

    val options = state.mathOptions
    // Render options as a responsive 2-column grid of large tap targets.
    Column(
        modifier = Modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowOptions.forEach { option ->
                    Button(
                        onClick = { onAnswer(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlaySurface,
                            contentColor = OverlayOnBackground
                        )
                    ) {
                        Text(text = "$option", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))
    LinearProgressIndicator(
        progress = { state.progress },
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth(),
        color = OverlayPrimary,
        trackColor = OverlayTrack
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Solved ${state.mathSolved} of ${state.mathTotal}",
        color = OverlayOnSurfaceVariant,
        fontSize = 14.sp
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = OverlayOnSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp)
    )
}

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
                    text = "Lose your streak?",
                    color = OverlayOnBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ending now unlocks $triggeringAppLabel, but it breaks your focus streak and logs this break as abandoned. Stay strong?",
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
                        Text(text = "Keep going", color = OverlayPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(text = "End session", color = PositionInvalidColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    isComplete: Boolean,
    bigText: String,
    smallText: String
) {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = OverlayTrack,
            strokeWidth = 10.dp
        )
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = if (isComplete) PositionValidColor else OverlayPrimary,
            strokeWidth = 10.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bigText,
                color = OverlayOnBackground,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = smallText, color = OverlayOnSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PositionStatusChip(isPositionValid: Boolean) {
    val accent = if (isPositionValid) PositionValidColor else PositionInvalidColor
    val label = if (isPositionValid) {
        "Phone is face-down - hold still"
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

/** Formats a millisecond duration as mm:ss (non-negative, clamped at zero). */
private fun formatMillis(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
