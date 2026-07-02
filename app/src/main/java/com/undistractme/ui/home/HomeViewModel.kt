package com.undistractme.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undistractme.domain.model.FocusSession
import com.undistractme.domain.repository.AppConfigRepository
import com.undistractme.domain.repository.BlockedAppRepository
import com.undistractme.domain.repository.FocusSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Home screen. Derived entirely from on-device repositories.
 */
data class HomeUiState(
    val isBlockingEnabled: Boolean = true,
    val enabledAppCount: Int = 0,
    val recentSessions: List<FocusSession> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val focusSessionRepository: FocusSessionRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        appConfigRepository.observeConfig(),
        blockedAppRepository.observeEnabledPackages(),
        focusSessionRepository.observeSessions()
    ) { config, enabledPackages, sessions ->
        HomeUiState(
            isBlockingEnabled = config.isBlockingEnabled,
            enabledAppCount = enabledPackages.size,
            recentSessions = sessions.take(RECENT_SESSION_LIMIT)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    /**
     * Persists the master blocking toggle. Service start/stop is handled by the
     * caller via a callback so this ViewModel stays free of Android framework types.
     */
    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appConfigRepository.getConfig()
            appConfigRepository.updateConfig(current.copy(isBlockingEnabled = enabled))
        }
    }

    private companion object {
        const val RECENT_SESSION_LIMIT = 15
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
