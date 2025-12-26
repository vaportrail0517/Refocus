package com.example.refocus.feature.home

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.gateway.AppLabelProvider
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.system.appinfo.AppIconResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val targetsRepository: TargetsRepository,
    private val appLabelProvider: AppLabelProvider,
    private val appIconResolver: AppIconResolver,
) : ViewModel() {

    data class TargetAppUiModel(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
    )

    val targetApps: StateFlow<List<TargetAppUiModel>> =
        targetsRepository
            .observeTargets()
            .mapLatest { pkgs: Set<String> ->
                withContext(Dispatchers.Default) {
                    pkgs.map { pkg: String ->
                        TargetAppUiModel(
                            packageName = pkg,
                            label = appLabelProvider.labelOf(pkg),
                            icon = appIconResolver.iconOf(pkg),
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}
