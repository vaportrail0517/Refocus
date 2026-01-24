package com.example.refocus.ui.minigame

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.theme.RefocusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniGameHostOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun introShownInitially_gameNotComposedUntilStart() {
        composeRule.setContent {
            RefocusTheme {
                MiniGameHostOverlay(
                    kind = MiniGameKind.FlashAnzan,
                    seed = 1L,
                    onFinished = {},
                )
            }
        }

        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(MiniGameTestTags.GAME_ROOT).assertDoesNotExist()
    }

    @Test
    fun startTransitionsToPlaying() {
        composeRule.setContent {
            RefocusTheme {
                MiniGameHostOverlay(
                    kind = MiniGameKind.FlashAnzan,
                    seed = 1L,
                    onFinished = {},
                )
            }
        }

        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_START_BUTTON).performClick()

        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_ROOT).assertDoesNotExist()
        composeRule.onNodeWithTag(MiniGameTestTags.GAME_ROOT).assertIsDisplayed()
    }

    @Test
    fun skipCallsOnFinished() {
        val finished = mutableStateOf(false)

        composeRule.setContent {
            RefocusTheme {
                MiniGameHostOverlay(
                    kind = MiniGameKind.FlashAnzan,
                    seed = 1L,
                    onFinished = { finished.value = true },
                )
            }
        }

        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_SKIP_BUTTON).performClick()

        composeRule.runOnIdle {
            assertTrue(finished.value)
        }
    }

    @Test
    fun seedChangeResetsToIntro() {
        val seed = mutableStateOf(1L)

        composeRule.setContent {
            RefocusTheme {
                MiniGameHostOverlay(
                    kind = MiniGameKind.FlashAnzan,
                    seed = seed.value,
                    onFinished = {},
                )
            }
        }

        // Intro -> Playing
        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_START_BUTTON).performClick()
        composeRule.onNodeWithTag(MiniGameTestTags.GAME_ROOT).assertIsDisplayed()

        // seed が変わったら別ゲームとして Intro に戻る
        composeRule.runOnIdle {
            seed.value = 2L
        }

        composeRule.onNodeWithTag(MiniGameTestTags.INTRO_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(MiniGameTestTags.GAME_ROOT).assertDoesNotExist()
    }
}
