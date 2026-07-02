package com.undistractme.data.repository

import com.undistractme.data.local.AppConfigDao
import com.undistractme.data.local.toDomain
import com.undistractme.data.local.toEntity
import com.undistractme.domain.model.AppConfig
import com.undistractme.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppConfigRepositoryImpl @Inject constructor(
    private val dao: AppConfigDao
) : AppConfigRepository {

    override fun observeConfig(): Flow<AppConfig> =
        dao.observe().map { entity -> entity?.toDomain() ?: AppConfig() }

    override suspend fun getConfig(): AppConfig =
        dao.get()?.toDomain() ?: AppConfig()

    override suspend fun updateConfig(config: AppConfig) {
        dao.upsert(config.toEntity())
    }
}
