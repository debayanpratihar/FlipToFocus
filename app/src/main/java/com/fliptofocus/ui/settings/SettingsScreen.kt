package com.fliptofocus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fliptofocus.domain.model.ChallengeType
import com.fliptofocus.domain.model.Difficulty
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.ui.theme.IosBlue
import com.fliptofocus.ui.theme.IosGroup
import com.fliptofocus.ui.theme.IosSecondaryLabel
import com.fliptofocus.ui.theme.IosSeparator
import java.util.Locale
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IosBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            SectionHeader("UNLOCK METHOD")
            SettingsGroup {
                ChallengeType.entries.forEachIndexed { index, type ->
                    SelectableRow(
                        title = type.displayName,
                        subtitle = type.description,
                        selected = uiState.challengeType == type,
                        onClick = { viewModel.setChallengeType(type) }
                    )
                    if (index != ChallengeType.entries.lastIndex) InsetDivider()
                }
            }

            SectionHeader("DIFFICULTY")
            SettingsGroup {
                Difficulty.entries.forEachIndexed { index, level ->
                    SelectableRow(
                        title = level.displayName,
                        subtitle = level.description,
                        selected = uiState.difficulty == level,
                        onClick = { viewModel.setDifficulty(level) }
                    )
                    if (index != Difficulty.entries.lastIndex) InsetDivider()
                }
            }

            SectionHeader("CHALLENGE")
            SettingsGroup {
                when (uiState.challengeType) {
                    ChallengeType.FLIP -> {
                        SliderRow(
                            title = "Timer",
                            value = uiState.challengeDurationMinutes.toFloat(),
                            valueRange = SettingsViewModel.MIN_MINUTES.toFloat()..SettingsViewModel.MAX_MINUTES.toFloat(),
                            steps = SettingsViewModel.MAX_MINUTES - SettingsViewModel.MIN_MINUTES - 1,
                            labelFor = { "${it.roundToInt()} min" },
                            onCommit = { viewModel.setChallengeDuration(it.roundToInt()) }
                        )
                        InsetDivider()
                        SwitchRow(
                            title = "Require face-down",
                            subtitle = "Phone must lie flat and face-down.",
                            checked = uiState.requireFaceDown,
                            onCheckedChange = { viewModel.setRequireFaceDown(it) }
                        )
                        InsetDivider()
                        SliderRow(
                            title = "Motion sensitivity",
                            value = uiState.motionTolerance,
                            valueRange = SettingsViewModel.MIN_TOLERANCE..SettingsViewModel.MAX_TOLERANCE,
                            steps = 5,
                            labelFor = { String.format(Locale.getDefault(), "%.1f", it) },
                            onCommit = { viewModel.setMotionTolerance(it) }
                        )
                    }
                    ChallengeType.WAIT, ChallengeType.COOLDOWN -> {
                        SliderRow(
                            title = "Timer",
                            value = uiState.challengeDurationMinutes.toFloat(),
                            valueRange = SettingsViewModel.MIN_MINUTES.toFloat()..SettingsViewModel.MAX_MINUTES.toFloat(),
                            steps = SettingsViewModel.MAX_MINUTES - SettingsViewModel.MIN_MINUTES - 1,
                            labelFor = { "${it.roundToInt()} min" },
                            onCommit = { viewModel.setChallengeDuration(it.roundToInt()) }
                        )
                    }
                    ChallengeType.SHAKE -> {
                        SliderRow(
                            title = "Shakes required",
                            value = uiState.shakeCount.toFloat(),
                            valueRange = SettingsViewModel.MIN_SHAKES.toFloat()..SettingsViewModel.MAX_SHAKES.toFloat(),
                            steps = ((SettingsViewModel.MAX_SHAKES - SettingsViewModel.MIN_SHAKES) / 5) - 1,
                            labelFor = { "${it.roundToInt()}" },
                            onCommit = { viewModel.setShakeCount(it.roundToInt()) }
                        )
                    }
                    ChallengeType.MATH -> {
                        SliderRow(
                            title = "Problems to solve",
                            value = uiState.mathProblemCount.toFloat(),
                            valueRange = SettingsViewModel.MIN_MATH.toFloat()..SettingsViewModel.MAX_MATH.toFloat(),
                            steps = SettingsViewModel.MAX_MATH - SettingsViewModel.MIN_MATH - 1,
                            labelFor = { "${it.roundToInt()}" },
                            onCommit = { viewModel.setMathProblemCount(it.roundToInt()) }
                        )
                    }
                }
            }

            SectionHeader("PRIVACY")
            SettingsGroup {
                Text(
                    text = "FlipToFocus works 100% offline. Your blocklist, settings, and focus history stay on this device. The app has no internet permission and never sends, collects, or shares your data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosSecondaryLabel,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "FlipToFocus • Offline focus blocker",
                style = MaterialTheme.typography.bodySmall,
                color = IosSecondaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = IosSecondaryLabel,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosGroup)
    ) {
        Column(content = content)
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = IosSeparator)
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = IosSecondaryLabel)
        }
        if (selected) {
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = IosBlue)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = IosSecondaryLabel)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    labelFor: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = labelFor(v),
                style = MaterialTheme.typography.bodyMedium,
                color = IosBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onCommit(v) },
            valueRange = valueRange,
            steps = steps
        )
    }
}
