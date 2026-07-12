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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val isBlockingEnabled: Boolean = true,
    val enabledAppCount: Int = 0,
    val streakDays: Int = 0,
    val longestStreak: Int = 0,
    val completedCount: Int = 0,
    val todayCompleted: Int = 0,
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
        val zone = ZoneId.systemDefault()
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
        val completedDays = completed
            .map { Instant.ofEpochMilli(it.startTimestamp).atZone(zone).toLocalDate() }
            .toSortedSet()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()

        HomeUiState(
            isBlockingEnabled = config.isBlockingEnabled,
            enabledAppCount = enabledPackages.size,
            streakDays = currentStreak(completedDays, today),
            longestStreak = longestStreak(completedDays),
            completedCount = completed.size,
            todayCompleted = completed.count {
                Instant.ofEpochMilli(it.startTimestamp).atZone(zone).toLocalDate() == today
            },
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

    /** Consecutive days (ending today, or yesterday as a grace) with at least one completed break. */
    private fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        var day = today
        if (!days.contains(day)) {
            day = day.minusDays(1)
            if (!days.contains(day)) return 0
        }
        var streak = 0
        while (days.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive completed-break days ever recorded. */
    private fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    private companion object {
        const val RECENT_SESSION_LIMIT = 30
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
