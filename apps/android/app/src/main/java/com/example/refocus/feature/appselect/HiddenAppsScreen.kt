package com.example.refocus.feature.appselect

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import com.example.refocus.feature.appselect.components.SearchBar
import com.example.refocus.ui.components.SettingsBaseDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(
    onNavigateBack: () -> Unit,
    parentEntry: NavBackStackEntry? = null,
) {
    val catalogViewModel: AppListViewModel =
        if (parentEntry != null) {
            hiltViewModel(parentEntry)
        } else {
            hiltViewModel()
        }
    val sessionViewModel: AppSelectEditSessionViewModel =
        if (parentEntry != null) {
            hiltViewModel(parentEntry)
        } else {
            hiltViewModel()
        }

    val apps by catalogViewModel.apps.collectAsState()
    val draftHidden by sessionViewModel.draftHiddenState.collectAsState()
    val draftTargets by sessionViewModel.draftTargetsState.collectAsState()
    val committedTargets by sessionViewModel.committedTargets.collectAsState()
    val isDirty by sessionViewModel.isDirtyHidden.collectAsState()
    val isSaving by sessionViewModel.isSavingState.collectAsState()
    val isLoaded by sessionViewModel.isLoadedState.collectAsState()

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var confirmTargetsToRemove by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered =
        remember(apps, query) {
            val q = query.text.trim()
            if (q.isEmpty()) {
                apps
            } else {
                apps.filter { it.label.contains(q, ignoreCase = true) }
            }
        }

    val (hiddenApps, visibleApps) =
        remember(filtered, draftHidden) {
            val hidden = filtered.filter { it.packageName in draftHidden }
            val others = filtered.filter { it.packageName !in draftHidden }
            hidden to others
        }

    if (confirmTargetsToRemove.isNotEmpty()) {
        SettingsBaseDialog(
            title = "対象から外れます",
            description =
                "非表示にしたアプリは，対象アプリから外れます．\n" +
                    "（保存済みの対象から外れるアプリ：${confirmTargetsToRemove.size}件）\n" +
                    "このまま保存しますか．",
            confirmLabel = "保存",
            dismissLabel = "キャンセル",
            onConfirm = {
                confirmTargetsToRemove = emptySet()
                sessionViewModel.saveHiddenOnly { onNavigateBack() }
            },
            onDismiss = { confirmTargetsToRemove = emptySet() },
        )
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("非表示アプリ") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "戻る",
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        bottomBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(
                    onClick = {
                        val toRemove = committedTargets intersect draftHidden
                        if (toRemove.isNotEmpty()) {
                            confirmTargetsToRemove = toRemove
                        } else {
                            sessionViewModel.saveHiddenOnly { onNavigateBack() }
                        }
                    },
                    enabled = isLoaded && isDirty && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "ここで非表示にしたアプリは，対象アプリ選択画面の候補に表示されません．",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )

            SearchBar(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                label = "検索",
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                if (hiddenApps.isEmpty() && visibleApps.isEmpty()) {
                    item {
                        Text(
                            text = "該当するアプリがありません．",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    if (hiddenApps.isNotEmpty()) {
                        item { SectionHeader(title = "非表示中（${hiddenApps.size}）") }
                        items(
                            items = hiddenApps,
                            key = { it.packageName },
                        ) { appItem ->
                            HiddenAppRow(
                                app = appItem,
                                isHidden = true,
                                isTarget = appItem.packageName in draftTargets,
                                onClick = { sessionViewModel.toggleHidden(appItem.packageName) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    item { SectionHeader(title = "その他（${visibleApps.size}）") }
                    if (visibleApps.isEmpty()) {
                        item {
                            Text(
                                text = "その他のアプリがありません．",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(
                            items = visibleApps,
                            key = { it.packageName },
                        ) { appItem ->
                            HiddenAppRow(
                                app = appItem,
                                isHidden = false,
                                isTarget = appItem.packageName in draftTargets,
                                onClick = { sessionViewModel.toggleHidden(appItem.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun HiddenAppRow(
    app: AppListViewModel.AppCatalogUiModel,
    isHidden: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconPainter =
            remember(app.packageName) {
                app.icon?.let { drawable ->
                    val bitmap = drawable.toBitmap()
                    BitmapPainter(bitmap.asImageBitmap())
                }
            }

        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(modifier = Modifier.size(40.dp))
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(app.label)
            Text(
                text = formatUsage(app.usageTimeMs),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isTarget) {
                Text(
                    text = "現在：対象",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Checkbox(
            checked = isHidden,
            onCheckedChange = { onClick() },
        )
    }
}

private fun formatUsage(ms: Long): String {
    if (ms <= 0L) return "過去7日間 0分"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remMinutes = minutes % 60
    return if (hours > 0) {
        "過去7日間 ${hours}時間${remMinutes}分"
    } else {
        "過去7日間 ${minutes}分"
    }
}
