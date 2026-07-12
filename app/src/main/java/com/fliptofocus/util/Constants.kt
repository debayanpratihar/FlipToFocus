package com.fliptofocus.util

/**
 * Global constants shared across layers.
 */
object Constants {

    /**
     * Default seed suggestions (packageName to label) used to prime an empty blocklist the first
     * time the app runs. The user can add or remove any app afterwards.
     */
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
