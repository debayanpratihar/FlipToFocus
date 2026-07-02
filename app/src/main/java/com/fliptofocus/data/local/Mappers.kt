package com.fliptofocus.data.local

import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.model.BlockedApp
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus

// ---------------------------------------------------------------------------
// BlockedApp <-> BlockedAppEntity
// ---------------------------------------------------------------------------

fun BlockedAppEntity.toDomain(): BlockedApp =
    BlockedApp(
        packageName = packageName,
        appLabel = appLabel,
        isEnabled = isEnabled
    )

fun BlockedApp.toEntity(addedAt: Long = System.currentTimeMillis()): BlockedAppEntity =
    BlockedAppEntity(
        packageName = packageName,
        appLabel = appLabel,
        isEnabled = isEnabled,
        addedAt = addedAt
    )

// ---------------------------------------------------------------------------
// FocusSession <-> FocusSessionEntity
// ---------------------------------------------------------------------------

fun FocusSessionEntity.toDomain(): FocusSession =
    FocusSession(
        id = id,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        challengeDurationMillis = challengeDurationMillis,
        triggeringPackage = triggeringPackage,
        status = runCatching { SessionStatus.valueOf(status) }
            .getOrDefault(SessionStatus.IN_PROGRESS)
    )

fun FocusSession.toEntity(): FocusSessionEntity =
    FocusSessionEntity(
        id = id,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        challengeDurationMillis = challengeDurationMillis,
        triggeringPackage = triggeringPackage,
        status = status.name
    )

// ---------------------------------------------------------------------------
// AppConfig <-> AppConfigEntity
// ---------------------------------------------------------------------------

fun AppConfigEntity.toDomain(): AppConfig =
    AppConfig(
        challengeDurationMinutes = challengeDurationMinutes,
        requireFaceDown = requireFaceDown,
        motionTolerance = motionTolerance,
        isBlockingEnabled = isBlockingEnabled
    )

fun AppConfig.toEntity(): AppConfigEntity =
    AppConfigEntity(
        id = 1,
        challengeDurationMinutes = challengeDurationMinutes,
        requireFaceDown = requireFaceDown,
        motionTolerance = motionTolerance,
        isBlockingEnabled = isBlockingEnabled
    )
