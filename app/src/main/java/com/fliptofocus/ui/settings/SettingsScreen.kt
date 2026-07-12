package com.fliptofocus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.fliptofocus.domain.model.ChallengeType
import com.fliptofocus.domain.model.Difficulty
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChallengeMethodCard(
                selected = uiState.challengeType,
                onSelect = viewModel::setChallengeType
            )

            DifficultyCard(
                selected = uiState.difficulty,
                onSelect = viewModel::setDifficulty
            )

            when (uiState.challengeType) {
                ChallengeType.FLIP -> {
                    ChallengeDurationCard(uiState.challengeDurationMinutes, viewModel::setChallengeDuration)
                    RequireFaceDownCard(uiState.requireFaceDown, viewModel::setRequireFaceDown)
                    MotionToleranceCard(uiState.motionTolerance, viewModel::setMotionTolerance)
                }
                ChallengeType.WAIT -> {
                    ChallengeDurationCard(uiState.challengeDurationMinutes, viewModel::setChallengeDuration)
                }
                ChallengeType.SHAKE -> {
                    ShakeCountCard(uiState.shakeCount, viewModel::setShakeCount)
                }
                ChallengeType.MATH -> {
                    MathCountCard(uiState.mathProblemCount, viewModel::setMathProblemCount)
                }
            }

            PrivacyStatementCard()
        }
    }
}

@Composable
private fun ChallengeMethodCard(
    selected: ChallengeType,
    onSelect: (ChallengeType) -> Unit
) {
    SettingCard(title = "Unlock method") {
        Text(
            text = "Choose what you need to do to unlock a blocked app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        ChallengeType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(type) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = type == selected, onClick = { onSelect(type) })
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyCard(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit
) {
    SettingCard(title = "Difficulty") {
        Text(
            text = "Scales the challenge: a longer timer, more shakes/problems, and stricter motion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Difficulty.entries.forEach { level ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(level) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = level == selected, onClick = { onSelect(level) })
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = level.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeDurationCard(minutes: Int, onDurationChange: (Int) -> Unit) {
    var sliderValue by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    SettingCard(title = "Timer duration") {
        Text(
            text = "The timer runs for ${sliderValue.roundToInt()} minute(s) before a blocked app unlocks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onDurationChange(sliderValue.roundToInt()) },
            valueRange = SettingsViewModel.MIN_MINUTES.toFloat()..SettingsViewModel.MAX_MINUTES.toFloat(),
            steps = SettingsViewModel.MAX_MINUTES - SettingsViewModel.MIN_MINUTES - 1
        )
        Text(
            text = "${sliderValue.roundToInt()} min",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RequireFaceDownCard(requireFaceDown: Boolean, onToggle: (Boolean) -> Unit) {
    SettingCard(title = "Require face-down") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "When on, the phone must lie flat and face-down. When off, flat face-up is also accepted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(12.dp))
            Switch(checked = requireFaceDown, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun MotionToleranceCard(motionTolerance: Float, onToleranceChange: (Float) -> Unit) {
    var sliderValue by remember(motionTolerance) { mutableFloatStateOf(motionTolerance) }
    SettingCard(title = "Motion sensitivity") {
        Text(
            text = "Controls how much movement resets the timer. Lower is stricter - even small movements restart the challenge.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onToleranceChange(sliderValue) },
            valueRange = SettingsViewModel.MIN_TOLERANCE..SettingsViewModel.MAX_TOLERANCE,
            steps = 5
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Strict",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(java.util.Locale.getDefault(), "%.1f", sliderValue),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Lenient",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShakeCountCard(count: Int, onChange: (Int) -> Unit) {
    var sliderValue by remember(count) { mutableFloatStateOf(count.toFloat()) }
    SettingCard(title = "Number of shakes") {
        Text(
            text = "Shake your phone ${sliderValue.roundToInt()} time(s) to unlock a blocked app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
            valueRange = SettingsViewModel.MIN_SHAKES.toFloat()..SettingsViewModel.MAX_SHAKES.toFloat(),
            steps = ((SettingsViewModel.MAX_SHAKES - SettingsViewModel.MIN_SHAKES) / 5) - 1
        )
        Text(
            text = "${sliderValue.roundToInt()} shakes",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MathCountCard(count: Int, onChange: (Int) -> Unit) {
    var sliderValue by remember(count) { mutableFloatStateOf(count.toFloat()) }
    SettingCard(title = "Number of problems") {
        Text(
            text = "Solve ${sliderValue.roundToInt()} quick problem(s) to unlock a blocked app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
            valueRange = SettingsViewModel.MIN_MATH.toFloat()..SettingsViewModel.MAX_MATH.toFloat(),
            steps = SettingsViewModel.MAX_MATH - SettingsViewModel.MIN_MATH - 1
        )
        Text(
            text = "${sliderValue.roundToInt()} problems",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PrivacyStatementCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your privacy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "FlipToFocus works 100% offline. All settings, blocklists, and focus history stay on this device. The app has no internet permission and never sends, collects, or shares your data.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
