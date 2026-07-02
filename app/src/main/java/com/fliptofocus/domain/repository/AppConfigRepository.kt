package com.fliptofocus.domain.repository

import com.fliptofocus.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

interface AppConfigRepository {
    fun observeConfig(): Flow<AppConfig>
    suspend fun getConfig(): AppConfig
    suspend fun updateConfig(config: AppConfig)
}
