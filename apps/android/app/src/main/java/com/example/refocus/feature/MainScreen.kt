package com.example.refocus.feature

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.refocus.feature.home.HomeRoute
import com.example.refocus.feature.settings.SettingsScreen
import com.example.refocus.feature.stats.StatsDetailSection
import com.example.refocus.feature.stats.StatsRoute
import com.example.refocus.feature.suggestions.SuggestionsRoute
import kotlinx.coroutines.launch

enum class MainTab {
    Home,
    Suggestions,
    Stats,
    Settings
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onOpenAppSelect: () -> Unit,
    onOpenPermissionFixFlow: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStatsDetail: (StatsDetailSection) -> Unit,
) {
    val tabs = MainTab.entries
    val initialPage = tabs.indexOf(MainTab.Home).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { tabs.size }
    )
    val scope = rememberCoroutineScope()
    val currentTab = tabs[pagerState.currentPage]
    Scaffold(
        bottomBar = {
            HomeBottomBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    val index = tabs.indexOf(tab).coerceAtLeast(0)
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (tabs[page]) {
                MainTab.Home -> HomeRoute(
                    onOpenHistory = onOpenHistory,
                    onOpenStatsDetail = onOpenStatsDetail,
                    onOpenPermissionFixFlow = onOpenPermissionFixFlow,
                    onOpenSettings = {
                        val index = tabs.indexOf(MainTab.Settings).coerceAtLeast(0)
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    onOpenAppSelect = onOpenAppSelect,
                )

                MainTab.Suggestions -> SuggestionsRoute()
                MainTab.Stats -> StatsRoute(onOpenHistory = onOpenHistory)
                MainTab.Settings -> SettingsScreen(
                    onOpenAppSelect = onOpenAppSelect,
                    onOpenPermissionFixFlow = onOpenPermissionFixFlow,
                )
            }
        }
    }
}


@Composable
private fun HomeBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == MainTab.Home,
            onClick = { onTabSelected(MainTab.Home) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "ホーム") },
            label = { Text("ホーム") }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.Suggestions,
            onClick = { onTabSelected(MainTab.Suggestions) },
//            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = "提案") },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "提案") },
            label = { Text("提案") }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.Stats,
            onClick = { onTabSelected(MainTab.Stats) },
//            icon = { Icon(Icons.Filled.Insights, contentDescription = "統計") },
            icon = { Icon(Icons.Filled.DataUsage, contentDescription = "統計") },
            label = { Text("統計") }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.Settings,
            onClick = { onTabSelected(MainTab.Settings) },
//            icon = { Icon(Icons.Filled.Settings, contentDescription = "設定") },
            icon = { Icon(Icons.Filled.Tune, contentDescription = "カスタマイズ") },
            label = { Text("カスタマイズ") }
        )
    }
}