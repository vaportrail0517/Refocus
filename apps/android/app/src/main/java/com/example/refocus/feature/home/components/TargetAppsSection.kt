package com.example.refocus.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.refocus.core.util.displayLength
import com.example.refocus.feature.home.HomeViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TargetAppsSection(
    apps: List<HomeViewModel.TargetAppUiModel>,
    onAddClick: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "対象アプリ",
            style = MaterialTheme.typography.titleMedium,
        )

        TargetAppsGrid(
            apps = apps,
            onAddClick = onAddClick,
            onAppClick = onAppClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TargetAppsGrid(
    apps: List<HomeViewModel.TargetAppUiModel>,
    onAddClick: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = 2
        val horizontalSpacing = 8.dp
        val itemWidth = (maxWidth - horizontalSpacing * (columns - 1)) / columns

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            apps.forEach { app ->
                TargetAppCard(
                    app = app,
                    onClick = { onAppClick(app.packageName) },
                    modifier = Modifier.width(itemWidth),
                )
            }
            AddTargetAppCard(
                onClick = onAddClick,
                modifier = Modifier.width(itemWidth),
            )
        }
    }
}

@Composable
internal fun AddTargetAppCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = "追加",
                modifier = Modifier.size(40.dp),
            )

            Text(
                text = "追加",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
            )
        }
    }
}

@Composable
internal fun TargetAppCard(
    app: HomeViewModel.TargetAppUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLong = app.label.displayLength() > 6.0
    val textStyle =
        if (isLong) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconPainter =
                remember(app.packageName, app.icon) {
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
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "？",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = app.label,
                style = textStyle,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
            )
        }
    }
}
