package com.example.refocus.feature.settings

import androidx.compose.runtime.Composable

@Composable
internal fun SettingsDialogHost(
    activeDialog: SettingsDialogType?,
    onResetAllData: () -> Unit,
    onStartPermissionFixFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (activeDialog) {
        SettingsDialogType.AppDataReset -> {
            AppDataResetDialog(
                onResetAllData = {
                    onResetAllData()
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        SettingsDialogType.CorePermissionRequired -> {
            CorePermissionRequiredDialog(
                onStartPermissionFixFlow = {
                    onDismiss()
                    onStartPermissionFixFlow()
                },
                onDismiss = onDismiss,
            )
        }

        SettingsDialogType.SuggestionFeatureRequired -> {
            SuggestionFeatureRequiredDialog(
                onDismiss = onDismiss,
            )
        }

        null -> Unit
    }
}
