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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import com.example.refocus.feature.appselect.components.SearchBar
import com.example.refocus.feature.appselect.components.rememberAppIconPainter
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onFinished: () -> Unit,
    onFinishedWithoutPermission: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    parentEntry: NavBackStackEntry? = null,
) {
    val permissionStatusProvider = rememberPermissionStatusProvider()
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
    val selectedPackages by sessionViewModel.draftTargetsState.collectAsState()
    val hiddenPackages by sessionViewModel.draftHiddenState.collectAsState()
    val isLoaded by sessionViewModel.isLoadedState.collectAsState()
    val isSaving by sessionViewModel.isSavingState.collectAsState()

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val q = query.text.trim()

    // Phase6（UX）：非表示が原因で「見つからない」状況を解消するため，
    // - 非表示件数の可視化と管理導線
    // - 検索時に「非表示も含める」トグル
    // - 検索結果が0のときに原因候補と導線を提示
    var includeHiddenInSearch by rememberSaveable { mutableStateOf(false) }

    val searchMatched =
        remember(apps, q) {
            if (q.isEmpty()) {
                apps
            } else {
                apps.filter { it.label.contains(q, ignoreCase = true) }
            }
        }

    // Phase1（対象選択UI改善）：選択中を常に上部に集約して表示する
    // Phase2（hiddenApps 基盤）：候補から hiddenApps を除外できるようにする
    val selectedApps = searchMatched.filter { it.packageName in selectedPackages }
    val nonSelectedMatched = searchMatched.filter { it.packageName !in selectedPackages }
    val visibleCandidates = nonSelectedMatched.filter { it.packageName !in hiddenPackages }
    val hiddenMatched = nonSelectedMatched.filter { it.packageName in hiddenPackages }
    val hiddenMatchesToShow = if (includeHiddenInSearch && q.isNotEmpty()) hiddenMatched else emptyList()

    val hiddenCount = hiddenPackages.size

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

            if (hiddenCount > 0) {
                HiddenAppsInfoBar(
                    hiddenCount = hiddenCount,
                    onOpenHiddenApps = onOpenHiddenApps,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (q.isNotEmpty()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = includeHiddenInSearch,
                            onCheckedChange = { includeHiddenInSearch = it },
                        )
                        Text(
                            text = "検索で非表示も含める",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                val isAllEmpty = selectedApps.isEmpty() && visibleCandidates.isEmpty() && hiddenMatchesToShow.isEmpty()

                if (isAllEmpty) {
                    item {
                        EmptySearchState(
                            query = q,
                            hiddenMatchedCount = hiddenMatched.size,
                            onOpenHiddenApps = onOpenHiddenApps,
                            onEnableIncludeHiddenInSearch = {
                                includeHiddenInSearch = true
                            },
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
                                isHidden = false,
                                onClick = { sessionViewModel.toggleTarget(appItem.packageName) },
                                onHiddenClick = onOpenHiddenApps,
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    item {
                        SectionHeader(title = "候補（${visibleCandidates.size}）")
                    }

                    if (visibleCandidates.isEmpty()) {
                        item {
                            val msg =
                                if (q.isNotEmpty() && hiddenMatched.isNotEmpty() && !includeHiddenInSearch) {
                                    "候補がありません．検索に一致するアプリが非表示に${hiddenMatched.size}件あります．"
                                } else {
                                    "候補がありません．"
                                }
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            if (q.isNotEmpty() && hiddenMatched.isNotEmpty() && !includeHiddenInSearch) {
                                TextButton(onClick = { includeHiddenInSearch = true }) {
                                    Text("検索で非表示も含めるをONにする")
                                }
                            }
                        }
                    } else {
                        items(
                            items = visibleCandidates,
                            key = { it.packageName },
                        ) { appItem ->
                            AppRow(
                                app = appItem,
                                isSelected = appItem.packageName in selectedPackages,
                                isHidden = false,
                                onClick = { sessionViewModel.toggleTarget(appItem.packageName) },
                                onHiddenClick = onOpenHiddenApps,
                            )
                        }
                    }

                    if (hiddenMatchesToShow.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item {
                            SectionHeader(title = "非表示（検索結果）（${hiddenMatchesToShow.size}）")
                        }
                        items(
                            items = hiddenMatchesToShow,
                            key = { it.packageName },
                        ) { appItem ->
                            AppRow(
                                app = appItem,
                                isSelected = false,
                                isHidden = true,
                                onClick = { },
                                onHiddenClick = onOpenHiddenApps,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenAppsInfoBar(
    hiddenCount: Int,
    onOpenHiddenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clickable { onOpenHiddenApps() },
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "非表示中のアプリが${hiddenCount}件あります．",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "管理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    hiddenMatchedCount: Int,
    onOpenHiddenApps: () -> Unit,
    onEnableIncludeHiddenInSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val hasQuery = query.isNotEmpty()
        val hasHiddenHit = hiddenMatchedCount > 0

        val msg =
            when {
                hasQuery && hasHiddenHit ->
                    "該当する候補がありません．検索に一致するアプリが非表示に${hiddenMatchedCount}件あります．"
                hasQuery ->
                    "該当するアプリがありません．"
                else ->
                    "該当するアプリがありません．"
            }

        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (hasQuery && hasHiddenHit) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenHiddenApps) {
                    Text("非表示アプリを管理")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onEnableIncludeHiddenInSearch) {
                    Text("検索で非表示も含めるをONにする")
                }
            }
        } else if (!hasQuery && hiddenMatchedCount > 0) {
            TextButton(
                onClick = onOpenHiddenApps,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("非表示アプリを管理")
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
    isHidden: Boolean,
    onClick: () -> Unit,
    onHiddenClick: () -> Unit,
) {
    val rowClick = if (isHidden) onHiddenClick else onClick

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { rowClick() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconPainter =
            rememberAppIconPainter(
                packageName = app.packageName,
                icon = app.icon,
                size = 40.dp,
            )

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
                // noop
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

        if (isHidden) {
            Text(
                text = "非表示中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
            )
        }
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
