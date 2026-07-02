package com.fliptofocus.data.repository

import com.fliptofocus.data.local.AppConfigDao
import com.fliptofocus.data.local.toDomain
import com.fliptofocus.data.local.toEntity
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.repository.AppConfigRepository
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
