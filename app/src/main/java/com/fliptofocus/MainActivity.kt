package com.fliptofocus

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fliptofocus.ui.navigation.AppNavigation
import com.fliptofocus.ui.theme.FlipToFocusTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the whole Compose UI.
 *
 * With foreground detection handled by [com.fliptofocus.service.FocusAccessibilityService], the
 * activity no longer starts any service - it just hosts navigation. If the previous run crashed,
 * it surfaces the captured stack trace so the user can report it easily.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(FlipToFocusApp.CRASH_PREFS, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(FlipToFocusApp.KEY_TRACE, null)

        setContent {
            FlipToFocusTheme {
                var trace by remember { mutableStateOf(crashTrace) }
                AppNavigation()
                trace?.let { captured ->
                    CrashReportDialog(
                        trace = captured,
                        onDismiss = {
                            prefs.edit().clear().apply()
                            trace = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashReportDialog(trace: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FlipToFocus hit an error") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "The last run crashed. Please tap Copy and send this to the developer:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(text = trace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(trace)) }) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}
