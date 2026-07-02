package com.fliptofocus.sensor

import com.fliptofocus.domain.model.ChallengeType

/**
 * Immutable snapshot of the offline unlock challenge currently in progress. A single
 * type drives which fields are meaningful; [progress] is computed per-type so the
 * overlay can render one consistent progress ring/bar for any challenge.
 *
 * Timed challenges (FLIP, WAIT) use [targetMillis]/[remainingMillis]; SHAKE uses
 * [shakeCount]/[shakeTarget]; MATH uses [mathSolved]/[mathTotal] plus the current
 * [mathQuestion] and [mathOptions].
 */
data class ChallengeState(
    val type: ChallengeType = ChallengeType.FLIP,
    val targetMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val isPositionValid: Boolean = false,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    // SHAKE
    val shakeTarget: Int = 0,
    val shakeCount: Int = 0,
    // MATH
    val mathQuestion: String? = null,
    val mathOptions: List<Int> = emptyList(),
    val mathTotal: Int = 0,
    val mathSolved: Int = 0
) {
    /** 0f..1f completion fraction, computed from whichever dimension this type tracks. */
    val progress: Float
        get() = when (type) {
            ChallengeType.SHAKE ->
                if (shakeTarget <= 0) 0f
                else (shakeCount.toFloat() / shakeTarget).coerceIn(0f, 1f)
            ChallengeType.MATH ->
                if (mathTotal <= 0) 0f
                else (mathSolved.toFloat() / mathTotal).coerceIn(0f, 1f)
            else ->
                if (targetMillis <= 0L) 0f
                else ((targetMillis - remainingMillis).toFloat() / targetMillis).coerceIn(0f, 1f)
        }
}
