package com.example.refocus.feature.entry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.refocus.data.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EntryViewModel @Inject constructor(
    application: Application,
    val onboardingRepository: OnboardingRepository
) : AndroidViewModel(application)
