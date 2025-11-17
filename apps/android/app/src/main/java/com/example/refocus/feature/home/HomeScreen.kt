package com.example.refocus.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.refocus.feature.history.SessionHistoryScreen
import com.example.refocus.feature.settings.SettingsScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

enum class HomeTab {
    Suggestions,
    Stats,
    Settings
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenAppSelect: () -> Unit
) {
    val tabs = HomeTab.entries
    val initialPage = tabs.indexOf(HomeTab.Settings).coerceAtLeast(0)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (tabs[page]) {
                HomeTab.Suggestions -> SuggestionsTab()
                HomeTab.Stats       -> StatsTab()
                HomeTab.Settings    -> SettingsScreen(onOpenAppSelect = onOpenAppSelect)
            }
        }
    }
}


@Composable
private fun HomeBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == HomeTab.Suggestions,
            onClick = { onTabSelected(HomeTab.Suggestions) },
            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = "提案") },
            label = { Text("提案") }
        )
        NavigationBarItem(
            selected = selectedTab == HomeTab.Stats,
            onClick = { onTabSelected(HomeTab.Stats) },
            icon = { Icon(Icons.Filled.Insights, contentDescription = "統計") },
            label = { Text("統計") }
        )
        NavigationBarItem(
            selected = selectedTab == HomeTab.Settings,
            onClick = { onTabSelected(HomeTab.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "設定") },
            label = { Text("設定") }
        )
    }
}

@Composable
private fun SuggestionsTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("提案タブ（将来ここに提案UIを実装）")
    }
}

@Composable
private fun StatsTab() {
    SessionHistoryScreen()
}
