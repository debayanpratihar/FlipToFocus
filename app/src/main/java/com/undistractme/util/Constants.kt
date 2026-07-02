package com.undistractme.util

/**
 * Global, compile-time constants shared across every layer of the app.
 *
 * These literals are part of the canonical cross-module contract: the service,
 * notification, and sensor layers all rely on the exact values declared here, so
 * nothing in this object may be renamed or re-typed without coordinating a change
 * across the whole codebase.
 */
object Constants {
    const val NOTIFICATION_CHANNEL_ID = "undistract_focus_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Focus Blocking"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val POLL_INTERVAL_MS = 400L
    const val DEFAULT_CHALLENGE_MINUTES = 5
    const val ACTION_START = "com.undistractme.action.START_BLOCKING"
    const val ACTION_STOP = "com.undistractme.action.STOP_BLOCKING"

    // Default seed suggestions (packageName to label).
    val DEFAULT_BLOCKED = listOf(
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android" to "X (Twitter)",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit"
    )
}
