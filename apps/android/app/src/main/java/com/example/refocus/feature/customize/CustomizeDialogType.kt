package com.example.refocus.feature.customize

sealed interface CustomizeDialogType {
    data object GraceTime : CustomizeDialogType

    data object PollingInterval : CustomizeDialogType

    data object FontRange : CustomizeDialogType

    data object TimeToMax : CustomizeDialogType

    data object EffectInterval : CustomizeDialogType

    data object TimerTimeDisplayMode : CustomizeDialogType

    data object TimerVisualTimeBasis : CustomizeDialogType

    data object SuggestionTriggerTime : CustomizeDialogType

    data object SuggestionForegroundStable : CustomizeDialogType

    data object SuggestionCooldown : CustomizeDialogType

    data object SuggestionTimeout : CustomizeDialogType

    data object SuggestionInteractionLockout : CustomizeDialogType

    data object MiniGameOrder : CustomizeDialogType

    data object GrowthMode : CustomizeDialogType

    data object ColorMode : CustomizeDialogType

    data object FixedColor : CustomizeDialogType

    data object GradientStartColor : CustomizeDialogType

    data object GradientMiddleColor : CustomizeDialogType

    data object GradientEndColor : CustomizeDialogType
}
