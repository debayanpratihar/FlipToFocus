package com.fliptofocus.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fliptofocus.R
import com.fliptofocus.util.PermissionUtils

private val GrantedGreen = Color(0xFF2E7D32)

/**
 * Four-step prominent-disclosure onboarding.
 *
 * Steps:
 *   1. Welcome â€” explains the offline, sensor-based concept.
 *   2. Usage Access disclosure â€” verbatim [R.string.usage_access_disclosure]
 *      shown BEFORE deep-linking to the OS Usage Access settings.
 *   3. Overlay disclosure â€” verbatim [R.string.overlay_disclosure] shown BEFORE
 *      deep-linking to the "Display over other apps" settings.
 *   4. All set â€” starts the service and enters the app.
 *
 * Permission state is re-read from the OS on every ON_RESUME (the user leaves to
 * a Settings screen and returns), and "Next"/"Start" stays disabled until the
 * permission required by the current step has actually been granted.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stepIndex by viewModel.stepIndex.collectAsState()
    val step = OnboardingStep.values()[stepIndex.coerceIn(0, viewModel.lastStepIndex)]

    var hasUsageAccess by remember { mutableStateOf(PermissionUtils.hasUsageAccess(context)) }
    var canDrawOverlays by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = PermissionUtils.hasUsageAccess(context)
                canDrawOverlays = PermissionUtils.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launchers deep-link to the relevant Settings screen; the result callback and
    // ON_RESUME both refresh the granted flags when the user returns.
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasUsageAccess = PermissionUtils.hasUsageAccess(context) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { canDrawOverlays = PermissionUtils.canDrawOverlays(context) }

    val isLastStep = stepIndex == viewModel.lastStepIndex
    val canProceed = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.USAGE_ACCESS -> hasUsageAccess
        OnboardingStep.OVERLAY -> canDrawOverlays
        OnboardingStep.ALL_SET -> hasUsageAccess && canDrawOverlays
    }

    // Back moves to the previous step rather than exiting, until the first step.
    BackHandler(enabled = stepIndex > 0) { viewModel.back() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            StepIndicator(
                current = stepIndex,
                total = viewModel.lastStepIndex + 1
            )
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeContent()

                    OnboardingStep.USAGE_ACCESS -> DisclosureCard(
                        icon = Icons.Filled.Settings,
                        heading = "Usage Access",
                        body = stringResource(R.string.usage_access_disclosure),
                        granted = hasUsageAccess,
                        grantLabel = "Grant Permission",
                        onGrant = {
                            usageLauncher.launch(PermissionUtils.usageAccessSettingsIntent())
                        }
                    )

                    OnboardingStep.OVERLAY -> DisclosureCard(
                        icon = Icons.Filled.Info,
                        heading = "Display Over Other Apps",
                        body = stringResource(R.string.overlay_disclosure),
                        granted = canDrawOverlays,
                        grantLabel = "Grant Permission",
                        onGrant = {
                            overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(context))
                        }
                    )

                    OnboardingStep.ALL_SET -> AllSetContent()
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { viewModel.back() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = { if (isLastStep) onFinished() else viewModel.next() },
                    enabled = canProceed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLastStep) "Start" else "Next")
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            val reached = i <= current
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 11.dp else 8.dp)
                    .background(
                        color = if (reached) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Reclaim your focus. When you open a distracting app, " +
                "FlipToFocus asks you to place your phone face-down and stay " +
                "still for a short break before it lets you continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Everything runs entirely on your device. No account, no " +
                "internet, no tracking â€” ever.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Next, we'll explain the two permissions the app needs and why.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DisclosureCard(
    icon: ImageVector,
    heading: String,
    body: String,
    granted: Boolean,
    grantLabel: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = GrantedGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Permission granted",
                        color = GrantedGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(grantLabel)
                }
            }
        }
    }
}

@Composable
private fun AllSetContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = GrantedGreen,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Choose which apps to block from the blocklist, then relax â€” " +
                "we'll gently step in whenever one of them is opened.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You can end any focus break early at any time, and change your " +
                "choices whenever you like.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
