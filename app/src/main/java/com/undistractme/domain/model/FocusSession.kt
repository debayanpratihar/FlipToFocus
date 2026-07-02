package com.undistractme.domain.model

/**
 * Lifecycle state of a focus/blocking session.
 *
 * IN_PROGRESS  - the challenge is currently being enforced.
 * COMPLETED    - the user successfully finished the physical challenge.
 * ABANDONED    - the user ended the session early (preserves user autonomy).
 */
enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }

/**
 * A single instance of the blocking challenge being triggered for an app.
 *
 * @param id Local database identifier (0 until persisted).
 * @param startTimestamp Epoch millis when the session began.
 * @param endTimestamp Epoch millis when the session ended, or null while in progress.
 * @param challengeDurationMillis Target still-and-face-down duration for this session.
 * @param triggeringPackage The blocked app that triggered this session.
 * @param status Current lifecycle state.
 */
data class FocusSession(
    val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val challengeDurationMillis: Long,
    val triggeringPackage: String,
    val status: SessionStatus
)
