package com.fliptofocus.domain.repository

import com.fliptofocus.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow

interface BlockedAppRepository {
    fun observeBlockedApps(): Flow<List<BlockedApp>>
    fun observeEnabledPackages(): Flow<List<String>>
    suspend fun getEnabledPackages(): Set<String>
    suspend fun addBlockedApp(app: BlockedApp)
    suspend fun removeBlockedApp(packageName: String)
    suspend fun setEnabled(packageName: String, enabled: Boolean)
    suspend fun seedDefaultsIfEmpty()
}
