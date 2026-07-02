package com.undistractme.data.repository

import com.undistractme.data.local.FocusSessionDao
import com.undistractme.data.local.FocusSessionEntity
import com.undistractme.data.local.toDomain
import com.undistractme.domain.model.FocusSession
import com.undistractme.domain.model.SessionStatus
import com.undistractme.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FocusSessionRepositoryImpl @Inject constructor(
    private val dao: FocusSessionDao
) : FocusSessionRepository {

    override suspend fun startSession(
        triggeringPackage: String,
        challengeDurationMillis: Long
    ): Long =
        dao.insert(
            FocusSessionEntity(
                startTimestamp = System.currentTimeMillis(),
                endTimestamp = null,
                challengeDurationMillis = challengeDurationMillis,
                triggeringPackage = triggeringPackage,
                status = SessionStatus.IN_PROGRESS.name
            )
        )

    override suspend fun completeSession(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                endTimestamp = System.currentTimeMillis(),
                status = SessionStatus.COMPLETED.name
            )
        )
    }

    override suspend fun abandonSession(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                endTimestamp = System.currentTimeMillis(),
                status = SessionStatus.ABANDONED.name
            )
        )
    }

    override fun observeSessions(): Flow<List<FocusSession>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getActiveSession(): FocusSession? =
        dao.getActive()?.toDomain()
}
