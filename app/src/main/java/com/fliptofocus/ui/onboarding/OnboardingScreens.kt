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
 *   1. Welcome - explains the offline, gesture-based concept.
 *   2. Accessibility disclosure - verbatim [R.string.accessibility_disclosure] shown BEFORE
 *      deep-linking to the OS Accessibility settings.
 *   3. Overlay disclosure - verbatim [R.string.overlay_disclosure] shown BEFORE deep-linking to
 *      the "Display over other apps" settings.
 *   4. All set - enters the app.
 *
 * Permission state is re-read from the OS on every ON_RESUME (the user leaves to a Settings screen
 * and returns), and "Next"/"Start" stays disabled until the permission for the current step is
 * actually granted.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stepIndex by viewModel.stepIndex.collectAsState()
    val step = OnboardingStep.values()[stepIndex.coerceIn(0, viewModel.lastStepIndex)]

    var hasAccessibility by remember {
        mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context))
    }
    var canDrawOverlays by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(context)
                canDrawOverlays = PermissionUtils.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(context) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { canDrawOverlays = PermissionUtils.canDrawOverlays(context) }

    val isLastStep = stepIndex == viewModel.lastStepIndex
    val canProceed = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.ACCESSIBILITY -> hasAccessibility
        OnboardingStep.OVERLAY -> canDrawOverlays
        OnboardingStep.ALL_SET -> hasAccessibility && canDrawOverlays
    }

    BackHandler(enabled = stepIndex > 0) { viewModel.back() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            StepIndicator(current = stepIndex, total = viewModel.lastStepIndex + 1)
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeContent()

                    OnboardingStep.ACCESSIBILITY -> DisclosureCard(
                        icon = Icons.Filled.Settings,
                        heading = "Accessibility Access",
                        body = stringResource(R.string.accessibility_disclosure),
                        granted = hasAccessibility,
                        grantLabel = "Open Accessibility Settings",
                        helper = "In the list, tap FlipToFocus and turn it on, then press Back.",
                        onGrant = {
                            runCatching {
                                accessibilityLauncher.launch(PermissionUtils.accessibilitySettingsIntent())
                            }
                        }
                    )

                    OnboardingStep.OVERLAY -> DisclosureCard(
                        icon = Icons.Filled.Info,
                        heading = "Display Over Other Apps",
                        body = stringResource(R.string.overlay_disclosure),
                        granted = canDrawOverlays,
                        grantLabel = "Grant Permission",
                        helper = null,
                        onGrant = {
                            runCatching {
                                overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(context))
                            }
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
            text = "Reclaim your focus. When you open an app you chose to block, FlipToFocus turns " +
                "on Focus Mode and asks you to complete a quick offline gesture - by default, " +
                "flipping your phone face-down and holding still - before it unlocks.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Everything runs entirely on your device. No account, no internet, no tracking - ever.",
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
    helper: String?,
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
            if (helper != null && !granted) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
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
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
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
            text = "Choose which apps to block from the blocklist, then relax - Focus Mode steps in " +
                "whenever one of them is opened.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You can change your blocked apps, the unlock method, and the timer at any time " +
                "from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
