package com.undistractme.domain.repository

import com.undistractme.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    suspend fun startSession(triggeringPackage: String, challengeDurationMillis: Long): Long
    suspend fun completeSession(id: Long)
    suspend fun abandonSession(id: Long)
    fun observeSessions(): Flow<List<FocusSession>>
    suspend fun getActiveSession(): FocusSession?
}
