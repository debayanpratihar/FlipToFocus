package com.fliptofocus.domain.model

/**
 * User-configurable settings for the focus blocker. All values are stored
 * strictly on-device; nothing is ever transmitted.
 *
 * @param challengeDurationMinutes How long the user must hold the position (1..60).
 * @param requireFaceDown Whether the phone must be face-down (vs. either flat orientation).
 * @param motionTolerance Sensitivity offset for motion detection; lower is stricter.
 * @param isBlockingEnabled Master switch for the whole blocking feature.
 */
data class AppConfig(
    val challengeDurationMinutes: Int = 5,
    val requireFaceDown: Boolean = true,
    val motionTolerance: Float = 1.0f,
    val isBlockingEnabled: Boolean = true
)
