package com.fliptofocus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.domain.repository.FocusSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * UI state for the Home dashboard. Derived entirely from on-device repositories.
 */
data class HomeUiState(
    val isBlockingEnabled: Boolean = true,
    val enabledAppCount: Int = 0,
    val streakDays: Int = 0,
    val completedCount: Int = 0,
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
            streakDays = computeStreakDays(sessions),
            completedCount = sessions.count { it.status == SessionStatus.COMPLETED },
            recentSessions = sessions.take(RECENT_SESSION_LIMIT)
        )
    }
        .catch { emit(HomeUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState()
        )

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val current = appConfigRepository.getConfig()
                appConfigRepository.updateConfig(current.copy(isBlockingEnabled = enabled))
            }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { runCatching { focusSessionRepository.deleteSession(id) } }
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { focusSessionRepository.clearHistory() } }
    }

    /**
     * The focus streak: how many consecutive days (ending today, or yesterday if today has none
     * yet) the user completed at least one challenge. A grace day means missing today alone does
     * not immediately zero the streak.
     */
    private fun computeStreakDays(sessions: List<FocusSession>): Int {
        val zone = ZoneId.systemDefault()
        val completedDays = sessions.asSequence()
            .filter { it.status == SessionStatus.COMPLETED }
            .map { Instant.ofEpochMilli(it.startTimestamp).atZone(zone).toLocalDate() }
            .toSet()
        if (completedDays.isEmpty()) return 0

        var day = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        if (!completedDays.contains(day)) {
            day = day.minusDays(1)
            if (!completedDays.contains(day)) return 0
        }
        var streak = 0
        while (completedDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private companion object {
        const val RECENT_SESSION_LIMIT = 30
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
