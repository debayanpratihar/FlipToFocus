package com.fliptofocus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.model.ChallengeType
import com.fliptofocus.domain.model.Difficulty
import com.fliptofocus.domain.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val challengeType: ChallengeType = ChallengeType.FLIP,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val challengeDurationMinutes: Int = 5,
    val requireFaceDown: Boolean = true,
    val motionTolerance: Float = 1.0f,
    val shakeCount: Int = 30,
    val mathProblemCount: Int = 3
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appConfigRepository.observeConfig()
        .map { config ->
            SettingsUiState(
                challengeType = config.challengeType,
                difficulty = config.difficulty,
                challengeDurationMinutes = config.challengeDurationMinutes,
                requireFaceDown = config.requireFaceDown,
                motionTolerance = config.motionTolerance,
                shakeCount = config.shakeCount,
                mathProblemCount = config.mathProblemCount
            )
        }
        .catch { emit(SettingsUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState()
        )

    fun setChallengeType(type: ChallengeType) = update { it.copy(challengeType = type) }

    fun setDifficulty(difficulty: Difficulty) = update { it.copy(difficulty = difficulty) }

    fun setChallengeDuration(minutes: Int) = update {
        it.copy(challengeDurationMinutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
    }

    fun setRequireFaceDown(requireFaceDown: Boolean) = update {
        it.copy(requireFaceDown = requireFaceDown)
    }

    fun setMotionTolerance(tolerance: Float) = update {
        it.copy(motionTolerance = tolerance.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE))
    }

    fun setShakeCount(count: Int) = update {
        it.copy(shakeCount = count.coerceIn(MIN_SHAKES, MAX_SHAKES))
    }

    fun setMathProblemCount(count: Int) = update {
        it.copy(mathProblemCount = count.coerceIn(MIN_MATH, MAX_MATH))
    }

    private fun update(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch {
            runCatching {
                val current = appConfigRepository.getConfig()
                appConfigRepository.updateConfig(transform(current))
            }
        }
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 60
        const val MIN_TOLERANCE = 0f
        const val MAX_TOLERANCE = 3f
        const val MIN_SHAKES = 5
        const val MAX_SHAKES = 100
        const val MIN_MATH = 1
        const val MAX_MATH = 10
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
