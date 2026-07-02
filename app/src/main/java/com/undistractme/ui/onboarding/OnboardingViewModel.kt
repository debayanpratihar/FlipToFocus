package com.undistractme.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Ordered steps of the prominent-disclosure onboarding flow.
 *
 * The two permission steps deliberately precede any OS permission dialog so the
 * user reads the plain-language disclosure BEFORE granting access â€” a Google
 * Play requirement for sensitive permissions.
 */
enum class OnboardingStep {
    WELCOME,
    USAGE_ACCESS,
    OVERLAY,
    ALL_SET
}

/**
 * Holds the current onboarding step so it survives configuration changes.
 *
 * Permission state itself is intentionally NOT stored here â€” it is re-read from
 * the OS on every ON_RESUME by the screen, because the user grants permissions
 * in an external Settings activity and returns.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val steps = OnboardingStep.values()
    val lastStepIndex: Int = steps.lastIndex

    private val _stepIndex = MutableStateFlow(0)
    val stepIndex: StateFlow<Int> = _stepIndex.asStateFlow()

    fun currentStep(): OnboardingStep = steps[_stepIndex.value.coerceIn(0, lastStepIndex)]

    fun next() {
        val current = _stepIndex.value
        if (current < lastStepIndex) {
            _stepIndex.value = current + 1
        }
    }

    fun back() {
        val current = _stepIndex.value
        if (current > 0) {
            _stepIndex.value = current - 1
        }
    }
}
