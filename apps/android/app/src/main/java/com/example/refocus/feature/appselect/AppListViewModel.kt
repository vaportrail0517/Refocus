package com.example.refocus.feature.appselect

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.data.repository.TargetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AppListViewModel @Inject constructor(
    application: Application,
    private val targetsRepository: TargetsRepository
) : AndroidViewModel(application) {

    data class AppUiModel(
        val label: String,
        val packageName: String,
        val usageTimeMs: Long,
        val isSelected: Boolean,
        val icon: Drawable?
    )

    private val pm: PackageManager = application.packageManager
    private val usageStatsManager: UsageStatsManager? =
        application.getSystemService(UsageStatsManager::class.java)
    private val _apps = MutableStateFlow<List<AppUiModel>>(emptyList())
    val apps: StateFlow<List<AppUiModel>> = _apps.asStateFlow()
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val selfPackageName: String = application.packageName

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val currentTargets = targetsRepository.observeTargets().first()
            selected.value = currentTargets

            // パッケージ一覧取得・UsageStats照会・アイコン読み込みは重いので Main から退避する
            val appList = withContext(Dispatchers.Default) {
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities: List<ResolveInfo> = pm.queryIntentActivities(launchIntent, 0)
                val usageMap = queryUsageTime()
                activities
                    .filter { resolveInfo ->
                        resolveInfo.activityInfo.packageName != selfPackageName
                    }
                    .map { info ->
                        val label = info.loadLabel(pm).toString()
                        val pkg = info.activityInfo.packageName
                        val usage = usageMap[pkg] ?: 0L
                        val icon = try {
                            info.loadIcon(pm)
                        } catch (e: Exception) {
                            // 一部のアプリで loadIcon が例外を投げることがあるため，UI は継続させる
                            RefocusLog.w("AppListViewModel", e) { "Failed to load icon for package=$pkg" }
                            null
                        }
                        AppUiModel(
                            label = label,
                            packageName = pkg,
                            usageTimeMs = usage,
                            isSelected = pkg in currentTargets,
                            icon = icon
                        )
                    }
                    .sortedByDescending { it.usageTimeMs }
            }

            _apps.value = appList
        }
    }

    private fun queryUsageTime(): Map<String, Long> {
        val usm = usageStatsManager ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(7)
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start,
            end
        ) ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        for (s in stats) {
            val pkg = s.packageName
            val time = s.totalTimeInForeground
            map[pkg] = (map[pkg] ?: 0L) + time
        }
        return map
    }

    fun toggleSelection(packageName: String) {
        val current = selected.value
        val new = if (packageName in current) current - packageName else current + packageName
        selected.value = new
        _apps.value = _apps.value.map {
            if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            targetsRepository.setTargets(selected.value)
            onSaved()
        }
    }
}
