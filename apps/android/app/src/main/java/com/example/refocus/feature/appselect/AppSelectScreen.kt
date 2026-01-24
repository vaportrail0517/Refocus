package com.example.refocus.feature.appselect

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.refocus.feature.appselect.components.SearchBar
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onFinished: () -> Unit,
    onFinishedWithoutPermission: () -> Unit,
    onOpenHiddenApps: () -> Unit,
) {
    val permissionStatusProvider = rememberPermissionStatusProvider()
    val catalogViewModel: AppListViewModel = hiltViewModel()
    val sessionViewModel: AppSelectEditSessionViewModel = hiltViewModel()

    val apps by catalogViewModel.apps.collectAsState()
    val selectedPackages by sessionViewModel.draftTargetsState.collectAsState()
    val hiddenPackages by sessionViewModel.draftHiddenState.collectAsState()
    val isLoaded by sessionViewModel.isLoadedState.collectAsState()
    val isSaving by sessionViewModel.isSavingState.collectAsState()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val filtered =
        remember(apps, query) {
            val q = query.text.trim()
            if (q.isEmpty()) {
                apps
            } else {
                apps.filter { it.label.contains(q, ignoreCase = true) }
            }
        }

    // Phase1（対象選択UI改善）：選択中を常に上部に集約して表示する
    // Phase2（hiddenApps 基盤）：候補から hiddenApps を除外できるようにする
    val (selectedApps, candidateApps) =
        remember(filtered, selectedPackages, hiddenPackages) {
            val selected = filtered.filter { it.packageName in selectedPackages }
            val candidates = filtered.filter { it.packageName !in selectedPackages && it.packageName !in hiddenPackages }
            selected to candidates
        }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("対象アプリ") },
                actions = {
                    IconButton(onClick = onOpenHiddenApps) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "非表示アプリを管理",
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
                        val snapshot = permissionStatusProvider.readCurrentInstant()
                        val hasCorePermissions = snapshot.usageGranted && snapshot.overlayGranted
                        sessionViewModel.saveAll {
                            if (hasCorePermissions) {
                                onFinished()
                            } else {
                                onFinishedWithoutPermission()
                            }
                        }
                    },
                    enabled = isLoaded && !isSaving,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Text("完了")
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
                if (selectedApps.isEmpty() && candidateApps.isEmpty()) {
                    item {
                        Text(
                            text = "該当するアプリがありません．",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    if (selectedApps.isNotEmpty()) {
                        item {
                            SectionHeader(title = "選択中（${selectedApps.size}）")
                        }
                        items(
                            items = selectedApps,
                            key = { it.packageName },
                        ) { appItem ->
                            AppRow(
                                app = appItem,
                                isSelected = appItem.packageName in selectedPackages,
                                onClick = { sessionViewModel.toggleTarget(appItem.packageName) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    item {
                        SectionHeader(title = "候補（${candidateApps.size}）")
                    }
                    if (candidateApps.isEmpty()) {
                        item {
                            Text(
                                text = "候補がありません．",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(
                            items = candidateApps,
                            key = { it.packageName },
                        ) { appItem ->
                            AppRow(
                                app = appItem,
                                isSelected = appItem.packageName in selectedPackages,
                                onClick = { sessionViewModel.toggleTarget(appItem.packageName) },
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
private fun AppRow(
    app: AppListViewModel.AppCatalogUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drawable → Painter 変換（パッケージ名ごとに remember）
        val iconPainter =
            remember(app.packageName) {
                app.icon?.let { drawable ->
                    // サイズは Drawable 側に任せつつ，そのままBitmap化
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
            Box(
                modifier =
                    Modifier
                        .size(40.dp),
            ) {
                //  Text(app.label.firstOrNull()?.toString() ?: "") などでもOK
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(app.label)
            Text(formatUsage(app.usageTimeMs), style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = isSelected,
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
