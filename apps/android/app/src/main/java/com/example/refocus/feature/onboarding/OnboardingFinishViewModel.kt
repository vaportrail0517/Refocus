package com.example.refocus.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.data.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingFinishViewModel @Inject constructor(
    application: Application,
    private val onboardingRepository: OnboardingRepository
) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            onboardingRepository.setCompleted(true)
        }
    }
}
