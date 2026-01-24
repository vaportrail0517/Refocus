package com.example.refocus.feature.customize

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.ui.minigame.MiniGameHostOverlay
import kotlinx.coroutines.launch

private enum class CustomizeTab(
    val title: String,
) {
    Basic("基本"),
    Advanced("詳細"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomizeScreen(modifier: Modifier = Modifier) {
    val viewModel: CustomizeViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<CustomizeDialogType?>(null) }
    var fontRange by remember(
        uiState.customize.minFontSizeSp,
        uiState.customize.maxFontSizeSp,
    ) {
        mutableStateOf(
            uiState.customize.minFontSizeSp..uiState.customize.maxFontSizeSp,
        )
    }

    val overlayServiceStatusProvider = rememberOverlayServiceStatusProvider()
    val overlayServiceController = rememberOverlayServiceController()
    var isServiceRunning by remember {
        mutableStateOf(overlayServiceStatusProvider.isRunning())
    }

    val tabs = CustomizeTab.entries
    val pagerState =
        rememberPagerState(
            initialPage = tabs.indexOf(CustomizeTab.Basic).coerceAtLeast(0),
            pageCount = { tabs.size },
        )
    val scope = rememberCoroutineScope()
    val basicScrollState = rememberScrollState()
    val advancedScrollState = rememberScrollState()

    var debugMiniGameKind by remember { mutableStateOf<MiniGameKind?>(null) }
    var debugMiniGameSeed by remember { mutableStateOf(0L) }

    // 画面復帰（ON_RESUME）で権限状態を再評価し，権限が欠けていれば安全側に倒す．
    rememberPermissionUiState(
        onRefreshed = { latest ->
            isServiceRunning = overlayServiceStatusProvider.isRunning()

            if (!latest.hasCorePermissions) {
                val latestState = viewModel.uiState.value
                // 起動設定 or 実行中サービスが残っていたら OFF に揃える
                if (latestState.customize.overlayEnabled || isServiceRunning) {
                    viewModel.updateOverlayEnabled(false)
                    overlayServiceController.stop(source = "customize_permission_refresh")
                    isServiceRunning = false
                }
                // 自動起動も OFF に揃える
                if (latestState.customize.autoStartOnBoot) {
                    viewModel.updateAutoStartOnBoot(false)
                }
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "カスタマイズ") },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tab.title) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val scrollState =
                        if (tabs[page] == CustomizeTab.Basic) {
                            basicScrollState
                        } else {
                            advancedScrollState
                        }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (tabs[page]) {
                            CustomizeTab.Basic -> {
                                BasicCustomizeContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onOpenTimerTimeModeDialog = {
                                        activeDialog = CustomizeDialogType.TimerTimeDisplayMode
                                    },
                                    onOpenTimerVisualTimeBasisDialog = {
                                        activeDialog = CustomizeDialogType.TimerVisualTimeBasis
                                    },
                                    onOpenEffectIntervalDialog = {
                                        activeDialog = CustomizeDialogType.EffectInterval
                                    },
                                    onOpenMiniGameOrderDialog = {
                                        activeDialog = CustomizeDialogType.MiniGameOrder
                                    },
                                    onOpenMiniGameSelectionDialog = {
                                        activeDialog = CustomizeDialogType.MiniGameSelection
                                    },
                                    onOpenPresetManager = {
                                        scope.launch {
                                            val target = tabs.indexOf(CustomizeTab.Advanced).coerceAtLeast(0)
                                            pagerState.animateScrollToPage(target)
                                            advancedScrollState.animateScrollTo(0)
                                        }
                                    },
                                    onDebugPlayMiniGame = { kind ->
                                        debugMiniGameSeed = System.currentTimeMillis()
                                        debugMiniGameKind = kind
                                    },
                                )
                            }

                            CustomizeTab.Advanced -> {
                                AdvancedCustomizeContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onOpenGraceDialog = {
                                        activeDialog = CustomizeDialogType.GraceTime
                                    },
                                    onOpenFontDialog = {
                                        fontRange = uiState.customize.minFontSizeSp..uiState.customize.maxFontSizeSp
                                        activeDialog = CustomizeDialogType.FontRange
                                    },
                                    onOpenTimeToMaxDialog = {
                                        activeDialog = CustomizeDialogType.TimeToMax
                                    },
                                    onOpenSuggestionTriggerDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionTriggerTime
                                    },
                                    onOpenSuggestionForegroundStableDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionForegroundStable
                                    },
                                    onOpenSuggestionCooldownDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionCooldown
                                    },
                                    onOpenSuggestionTimeoutDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionTimeout
                                    },
                                    onOpenSuggestionInteractionLockoutDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionInteractionLockout
                                    },
                                    onOpenGrowthModeDialog = {
                                        activeDialog = CustomizeDialogType.GrowthMode
                                    },
                                    onOpenColorModeDialog = {
                                        activeDialog = CustomizeDialogType.ColorMode
                                    },
                                    onOpenFixedColorDialog = {
                                        activeDialog = CustomizeDialogType.FixedColor
                                    },
                                    onOpenGradientStartColorDialog = {
                                        activeDialog = CustomizeDialogType.GradientStartColor
                                    },
                                    onOpenGradientMiddleColorDialog = {
                                        activeDialog = CustomizeDialogType.GradientMiddleColor
                                    },
                                    onOpenGradientEndColorDialog = {
                                        activeDialog = CustomizeDialogType.GradientEndColor
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Dialog は Pager の外で 1 回だけ描画する（隣ページの事前合成で多重表示されないようにする）
            CustomizeDialogHost(
                activeDialog = activeDialog,
                uiState = uiState,
                viewModel = viewModel,
                fontRange = fontRange,
                onDismiss = { activeDialog = null },
            )

            debugMiniGameKind?.let { kind ->
                Dialog(
                    onDismissRequest = { debugMiniGameKind = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    // Dialog 内では画面全体の制約が渡るため，カスタマイズ画面のレイアウト制約に影響されず
                    // ミニゲームをフルスクリーンで表示できる．
                    Box(modifier = Modifier.fillMaxSize()) {
                        MiniGameHostOverlay(
                            kind = kind,
                            seed = debugMiniGameSeed,
                            onFinished = { debugMiniGameKind = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
