package com.undistractme.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undistractme.domain.model.AppConfig
import com.undistractme.domain.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val challengeDurationMinutes: Int = 5,
    val requireFaceDown: Boolean = true,
    val motionTolerance: Float = 1.0f
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appConfigRepository.observeConfig()
        .map { config ->
            SettingsUiState(
                challengeDurationMinutes = config.challengeDurationMinutes,
                requireFaceDown = config.requireFaceDown,
                motionTolerance = config.motionTolerance
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState()
        )

    fun setChallengeDuration(minutes: Int) = update { config ->
        config.copy(challengeDurationMinutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
    }

    fun setRequireFaceDown(requireFaceDown: Boolean) = update { config ->
        config.copy(requireFaceDown = requireFaceDown)
    }

    fun setMotionTolerance(tolerance: Float) = update { config ->
        config.copy(motionTolerance = tolerance.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE))
    }

    private fun update(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch {
            val current = appConfigRepository.getConfig()
            appConfigRepository.updateConfig(transform(current))
        }
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 60
        const val MIN_TOLERANCE = 0f
        const val MAX_TOLERANCE = 3f
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
