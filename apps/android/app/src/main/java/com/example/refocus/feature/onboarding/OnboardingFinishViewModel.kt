package com.example.refocus.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingFinishViewModel
    @Inject
    constructor(
        private val onboardingRepository: OnboardingRepository,
    ) : ViewModel() {
        init {
            viewModelScope.launch {
                onboardingRepository.setCompleted(true)
            }
        }
    }
