package com.fliptofocus.sensor

/**
 * Immutable snapshot of the offline physical challenge the user must complete.
 *
 * @param targetMillis total duration the user must hold the required position without moving.
 * @param remainingMillis time still required; resets to [targetMillis] whenever the position is
 *   invalid or motion is detected.
 * @param isPositionValid whether the phone is currently in the required flat position.
 * @param isRunning whether the challenge is actively being tracked.
 * @param isComplete whether the challenge has been satisfied.
 */
data class ChallengeState(
    val targetMillis: Long,
    val remainingMillis: Long,
    val isPositionValid: Boolean,
    val isRunning: Boolean,
    val isComplete: Boolean
) {
    val progress: Float
        get() = if (targetMillis <= 0L) 0f
        else ((targetMillis - remainingMillis).toFloat() / targetMillis).coerceIn(0f, 1f)
}
