package com.undistractme.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undistractme.data.InstalledAppsProvider
import com.undistractme.domain.model.BlockedApp
import com.undistractme.domain.repository.BlockedAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One selectable row in the blocklist: an installed launchable app plus whether it
 * is currently in the blocklist and enabled.
 */
data class BlocklistItem(
    val packageName: String,
    val appLabel: String,
    val isBlocked: Boolean
)

data class BlocklistUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val apps: List<BlocklistItem> = emptyList()
)

@HiltViewModel
class BlocklistViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository
) : ViewModel() {

    // null == still loading the installed-app list off the main thread.
    private val installedApps = MutableStateFlow<List<BlockedApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            installedApps.value = installedAppsProvider.getLaunchableApps()
        }
    }

    val uiState: StateFlow<BlocklistUiState> = combine(
        installedApps,
        query,
        blockedAppRepository.observeBlockedApps()
    ) { installed, currentQuery, blocked ->
        if (installed == null) {
            BlocklistUiState(isLoading = true, query = currentQuery)
        } else {
            val enabledBlockedPackages = blocked
                .filter { it.isEnabled }
                .map { it.packageName }
                .toSet()
            val filtered = if (currentQuery.isBlank()) {
                installed
            } else {
                installed.filter { app ->
                    app.appLabel.contains(currentQuery, ignoreCase = true) ||
                        app.packageName.contains(currentQuery, ignoreCase = true)
                }
            }
            BlocklistUiState(
                isLoading = false,
                query = currentQuery,
                apps = filtered.map { app ->
                    BlocklistItem(
                        packageName = app.packageName,
                        appLabel = app.appLabel,
                        isBlocked = app.packageName in enabledBlockedPackages
                    )
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BlocklistUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /**
     * Adds the app to the blocklist (enabled) when checked, removes it when unchecked.
     */
    fun setBlocked(item: BlocklistItem, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) {
                blockedAppRepository.addBlockedApp(
                    BlockedApp(
                        packageName = item.packageName,
                        appLabel = item.appLabel,
                        isEnabled = true
                    )
                )
            } else {
                blockedAppRepository.removeBlockedApp(item.packageName)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
