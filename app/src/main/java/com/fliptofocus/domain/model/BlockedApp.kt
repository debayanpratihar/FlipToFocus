package com.fliptofocus.domain.model

/**
 * A user-selected application that should be intercepted by the focus blocker.
 *
 * @param packageName Android package identifier (e.g. "com.instagram.android").
 * @param appLabel Human-readable label shown in the UI.
 * @param isEnabled Whether blocking is currently active for this app.
 */
data class BlockedApp(
    val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean = true
)
