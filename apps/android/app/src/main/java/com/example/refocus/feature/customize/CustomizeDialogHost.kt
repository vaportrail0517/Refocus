package com.example.refocus.feature.customize

import androidx.compose.runtime.Composable
import com.example.refocus.feature.customize.dialogs.ColorModeDialog
import com.example.refocus.feature.customize.dialogs.FixedColorDialog
import com.example.refocus.feature.customize.dialogs.FontRangeDialog
import com.example.refocus.feature.customize.dialogs.GraceTimeDialog
import com.example.refocus.feature.customize.dialogs.GradientEndColorDialog
import com.example.refocus.feature.customize.dialogs.GradientMiddleColorDialog
import com.example.refocus.feature.customize.dialogs.GradientStartColorDialog
import com.example.refocus.feature.customize.dialogs.GrowthModeDialog
import com.example.refocus.feature.customize.dialogs.PollingIntervalDialog
import com.example.refocus.feature.customize.dialogs.SuggestionCooldownDialog
import com.example.refocus.feature.customize.dialogs.SuggestionForegroundStableDialog
import com.example.refocus.feature.customize.dialogs.SuggestionInteractionLockoutDialog
import com.example.refocus.feature.customize.dialogs.SuggestionTimeoutDialog
import com.example.refocus.feature.customize.dialogs.SuggestionTriggerTimeDialog
import com.example.refocus.feature.customize.dialogs.TimeToMaxDialog
import com.example.refocus.feature.customize.dialogs.TimerTimeModeDialog
import com.example.refocus.feature.customize.dialogs.TimerVisualTimeBasisDialog
import com.example.refocus.feature.customize.dialogs.MiniGameKindDialog
import com.example.refocus.feature.customize.dialogs.MiniGameOrderDialog

@Composable
internal fun CustomizeDialogHost(
    activeDialog: CustomizeDialogType?,
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    fontRange: ClosedFloatingPointRange<Float>,
    onDismiss: () -> Unit,
) {
    when (activeDialog) {
        CustomizeDialogType.GraceTime -> {
            GraceTimeDialog(
                currentMillis = uiState.customize.gracePeriodMillis,
                onConfirm = { newMillis ->
                    viewModel.updateGracePeriodMillis(newMillis)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.PollingInterval -> {
            PollingIntervalDialog(
                currentMillis = uiState.customize.pollingIntervalMillis,
                onConfirm = { newMs ->
                    viewModel.updatePollingIntervalMillis(newMs)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.FontRange -> {
            FontRangeDialog(
                initialRange = fontRange,
                onConfirm = { newRange ->
                    val minFontSpLimit = 8f
                    val maxFontSpLimit = 96f
                    val clampedMin = newRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)
                    val clampedMax = newRange.endInclusive.coerceIn(clampedMin, maxFontSpLimit)
                    viewModel.updateMinFontSizeSp(clampedMin)
                    viewModel.updateMaxFontSizeSp(clampedMax)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.TimeToMax -> {
            TimeToMaxDialog(
                currentSeconds = uiState.customize.timeToMaxSeconds,
                onConfirm = { seconds ->
                    viewModel.updateTimeToMaxSeconds(seconds)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.TimerTimeDisplayMode -> {
            TimerTimeModeDialog(
                current = uiState.customize.timerTimeMode,
                onConfirm = { mode ->
                    viewModel.updateTimerTimeMode(mode)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.TimerVisualTimeBasis -> {
            TimerVisualTimeBasisDialog(
                current = uiState.customize.timerVisualTimeBasis,
                onConfirm = { basis ->
                    viewModel.updateTimerVisualTimeBasis(basis)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.SuggestionTriggerTime -> {
            SuggestionTriggerTimeDialog(
                currentSeconds = uiState.customize.suggestionTriggerSeconds,
                onConfirm = { seconds ->
                    viewModel.updateSuggestionTriggerSeconds(seconds)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.SuggestionForegroundStable -> {
            SuggestionForegroundStableDialog(
                currentSeconds = uiState.customize.suggestionForegroundStableSeconds,
                onConfirm = { seconds ->
                    viewModel.updateSuggestionForegroundStableSeconds(seconds)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.SuggestionCooldown -> {
            SuggestionCooldownDialog(
                currentSeconds = uiState.customize.suggestionCooldownSeconds,
                onConfirm = { seconds ->
                    viewModel.updateSuggestionCooldownSeconds(seconds)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.SuggestionTimeout -> {
            SuggestionTimeoutDialog(
                currentSeconds = uiState.customize.suggestionTimeoutSeconds,
                onConfirm = { seconds ->
                    viewModel.updateSuggestionTimeoutSeconds(seconds)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.SuggestionInteractionLockout -> {
            SuggestionInteractionLockoutDialog(
                currentMillis = uiState.customize.suggestionInteractionLockoutMillis,
                onConfirm = { millis ->
                    viewModel.updateSuggestionInteractionLockoutMillis(millis)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }



        CustomizeDialogType.MiniGameOrder -> {
            MiniGameOrderDialog(
                current = uiState.customize.miniGameOrder,
                onConfirm = { order ->
                    viewModel.updateMiniGameOrder(order)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.MiniGameKind -> {
            MiniGameKindDialog(
                current = uiState.customize.miniGameKind,
                onConfirm = { kind ->
                    viewModel.updateMiniGameKind(kind)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        CustomizeDialogType.GrowthMode -> {
            GrowthModeDialog(
                current = uiState.customize.growthMode,
                onConfirm = { mode ->
                    viewModel.updateGrowthMode(mode)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.ColorMode -> {
            ColorModeDialog(
                current = uiState.customize.colorMode,
                onConfirm = { mode ->
                    viewModel.updateColorMode(mode)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.FixedColor -> {
            FixedColorDialog(
                currentColorArgb = uiState.customize.fixedColorArgb,
                onConfirm = { argb ->
                    viewModel.updateFixedColorArgb(argb)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.GradientStartColor -> {
            GradientStartColorDialog(
                currentColorArgb = uiState.customize.gradientStartColorArgb,
                onConfirm = { argb ->
                    viewModel.updateGradientStartColorArgb(argb)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.GradientMiddleColor -> {
            GradientMiddleColorDialog(
                currentColorArgb = uiState.customize.gradientMiddleColorArgb,
                onConfirm = { argb ->
                    viewModel.updateGradientMiddleColorArgb(argb)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        CustomizeDialogType.GradientEndColor -> {
            GradientEndColorDialog(
                currentColorArgb = uiState.customize.gradientEndColorArgb,
                onConfirm = { argb ->
                    viewModel.updateGradientEndColorArgb(argb)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        null -> Unit
    }
}