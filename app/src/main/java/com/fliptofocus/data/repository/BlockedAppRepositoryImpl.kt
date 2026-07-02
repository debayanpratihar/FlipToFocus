package com.fliptofocus.data.repository

import com.fliptofocus.data.local.BlockedAppDao
import com.fliptofocus.data.local.BlockedAppEntity
import com.fliptofocus.data.local.toDomain
import com.fliptofocus.domain.model.BlockedApp
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BlockedAppRepositoryImpl @Inject constructor(
    private val dao: BlockedAppDao
) : BlockedAppRepository {

    override fun observeBlockedApps(): Flow<List<BlockedApp>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeEnabledPackages(): Flow<List<String>> =
        dao.observeEnabledPackages()

    override suspend fun getEnabledPackages(): Set<String> =
        dao.getEnabled().map { it.packageName }.toSet()

    override suspend fun addBlockedApp(app: BlockedApp) {
        dao.upsert(
            BlockedAppEntity(
                packageName = app.packageName,
                appLabel = app.appLabel,
                isEnabled = app.isEnabled,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeBlockedApp(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        dao.setEnabled(packageName, enabled)
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            val now = System.currentTimeMillis()
            Constants.DEFAULT_BLOCKED.forEach { (packageName, label) ->
                dao.upsert(
                    BlockedAppEntity(
                        packageName = packageName,
                        appLabel = label,
                        isEnabled = true,
                        addedAt = now
                    )
                )
            }
        }
    }
}
