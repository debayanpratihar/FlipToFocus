package com.fliptofocus.domain.model

/**
 * User-configurable settings for the focus blocker. All values are stored
 * strictly on-device; nothing is ever transmitted.
 *
 * @param challengeType Which offline unlock method must be completed.
 * @param challengeDurationMinutes How long the timed challenges run (1..60).
 * @param requireFaceDown Whether the phone must be face-down (vs. either flat orientation). FLIP only.
 * @param motionTolerance Sensitivity offset for motion detection; lower is stricter. FLIP only.
 * @param shakeCount How many shakes the SHAKE challenge requires.
 * @param mathProblemCount How many problems the MATH challenge requires.
 * @param isBlockingEnabled Master switch for the whole blocking feature.
 */
data class AppConfig(
    val challengeType: ChallengeType = ChallengeType.FLIP,
    val challengeDurationMinutes: Int = 5,
    val requireFaceDown: Boolean = true,
    val motionTolerance: Float = 1.0f,
    val shakeCount: Int = 30,
    val mathProblemCount: Int = 3,
    val isBlockingEnabled: Boolean = true
)
