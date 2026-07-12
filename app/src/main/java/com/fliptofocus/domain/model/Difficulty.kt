package com.fliptofocus.domain.model

/**
 * Difficulty scales how demanding the unlock challenge is. The [factor] multiplies the base
 * challenge parameters (timer length, shake count, number of math problems) and also tightens
 * motion strictness for the flip challenge.
 */
enum class Difficulty(val displayName: String, val description: String, val factor: Float) {
    EASY("Easy", "A gentle nudge - shorter and more forgiving.", 0.5f),
    MEDIUM("Medium", "A balanced pause. Recommended.", 1.0f),
    HARD("Hard", "A serious commitment - longer and stricter.", 2.0f);

    companion object {
        fun fromName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
