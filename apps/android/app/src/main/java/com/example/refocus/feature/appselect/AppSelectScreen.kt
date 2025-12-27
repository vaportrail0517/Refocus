package com.example.refocus.feature.appselect

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.refocus.feature.common.permissions.rememberPermissionStatusProvider
import com.example.refocus.feature.appselect.components.SearchBar

@Composable
fun AppSelectScreen(
    onFinished: () -> Unit,
    onFinishedWithoutPermission: () -> Unit
) {
    val permissionStatusProvider = rememberPermissionStatusProvider()
    val viewModel: AppListViewModel = hiltViewModel()
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val filtered = remember(apps, query) {
        val q = query.text.trim()
        if (q.isEmpty()) apps
        else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        val snapshot = permissionStatusProvider.readCurrentInstant()
                        val hasCorePermissions = snapshot.usageGranted && snapshot.overlayGranted
                        viewModel.save(
                            if (hasCorePermissions)
                                onFinished else onFinishedWithoutPermission
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text("完了")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            SearchBar(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                label = "検索"
            )
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filtered) { appItem ->
                    AppRow(
                        app = appItem,
                        onClick = { viewModel.toggleSelection(appItem.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppListViewModel.AppUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drawable → Painter 変換（パッケージ名ごとに remember）
        val iconPainter = remember(app.packageName) {
            app.icon?.let { drawable ->
                // サイズは Drawable 側に任せつつ、そのままBitmap化
                val bitmap = drawable.toBitmap()
                BitmapPainter(bitmap.asImageBitmap())
            }
        }
        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
            ) {
                //  Text(app.label.firstOrNull()?.toString() ?: "") などでもOK
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(app.label)
            Text(formatUsage(app.usageTimeMs), style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = app.isSelected,
            onCheckedChange = { onClick() }
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
