package com.fliptofocus.domain.model

/**
 * The different offline "unlock challenges" the user can choose from. Each one is
 * a small, deliberate friction step that runs entirely on-device before a blocked
 * app opens. The user picks their preferred method in Settings.
 *
 * @property displayName Short label shown in the picker.
 * @property description One-line explanation shown under the label.
 */
enum class ChallengeType(
    val displayName: String,
    val description: String
) {
    /** Place the phone flat and face-down and hold still for the whole timer. */
    FLIP(
        displayName = "Flip & hold",
        description = "Place your phone face-down and keep still until the timer ends."
    ),

    /** Simply wait out a countdown - a calm, no-sensor pause. */
    WAIT(
        displayName = "Patience timer",
        description = "Just wait out a countdown. A gentle pause before you dive in."
    ),

    /** Shake the phone a configurable number of times to burn off the impulse. */
    SHAKE(
        displayName = "Shake it off",
        description = "Shake your phone a set number of times to release the urge."
    ),

    /** Solve a few quick arithmetic problems to engage the brain. */
    MATH(
        displayName = "Quick math",
        description = "Solve a few simple problems to wake up your brain first."
    );

    companion object {
        /** Safe parse from a persisted name, falling back to [FLIP]. */
        fun fromName(name: String?): ChallengeType =
            entries.firstOrNull { it.name == name } ?: FLIP
    }
}
