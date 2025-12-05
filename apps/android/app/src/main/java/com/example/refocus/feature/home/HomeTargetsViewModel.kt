package com.example.refocus.feature.home

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.data.repository.TargetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeTargetsViewModel @Inject constructor(
    application: Application,
    private val targetsRepository: TargetsRepository,
) : AndroidViewModel(application) {

    data class TargetAppUiModel(
        val packageName: String,
        val label: String,
    )

    private val pm: PackageManager = application.packageManager

    val targetApps: StateFlow<List<TargetAppUiModel>> =
        targetsRepository
            .observeTargets()
            .map { pkgs: Set<String> ->
                pkgs.mapNotNull { pkg: String ->
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        TargetAppUiModel(
                            packageName = pkg,
                            label = label,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
}
