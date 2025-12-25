package com.example.refocus.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    val completedFlow: Flow<Boolean>

    suspend fun setCompleted(completed: Boolean = true)
}
