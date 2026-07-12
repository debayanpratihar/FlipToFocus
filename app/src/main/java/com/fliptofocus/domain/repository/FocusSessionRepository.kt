package com.fliptofocus.domain.repository

import com.fliptofocus.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    suspend fun startSession(triggeringPackage: String, challengeDurationMillis: Long): Long
    suspend fun completeSession(id: Long)
    suspend fun abandonSession(id: Long)
    fun observeSessions(): Flow<List<FocusSession>>
    suspend fun getActiveSession(): FocusSession?
    suspend fun deleteSession(id: Long)
    suspend fun clearHistory()
}
