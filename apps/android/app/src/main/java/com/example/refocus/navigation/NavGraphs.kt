package com.example.refocus.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.refocus.feature.onboarding.EntryScreen
import com.example.refocus.feature.onboarding.OnboardingIntroScreen
import com.example.refocus.feature.onboarding.PermissionFlowScreen
import com.example.refocus.feature.onboarding.OnboardingReadyScreen
import com.example.refocus.feature.onboarding.OnboardingFinishScreen
import com.example.refocus.feature.appselect.AppSelectScreen
import com.example.refocus.feature.home.HomeScreen
import com.example.refocus.feature.onboarding.OnboardingStartModeScreen
import com.example.refocus.feature.overlay.startOverlayService

object Destinations {
    const val ENTRY               = "entry"
    const val ONBOARDING_INTRO    = "onboarding_intro"
    const val PERMISSION_FLOW     = "permission_flow"
    const val PERMISSION_FLOW_FIX = "permission_flow_fix"
    const val ONBOARDING_READY    = "onboarding_ready"
    const val APP_SELECT          = "app_select"
    const val APP_SELECT_SETTINGS = "app_select_settings"
    const val ONBOARDING_START_MODE = "onboarding_start_mode"
    const val ONBOARDING_FINISH   = "onboarding_finish"
    const val HOME                = "home"
}

@Composable
fun RefocusNavHost(
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.ENTRY
    ) {
        composable(Destinations.ENTRY) {
            val context = LocalContext.current
            EntryScreen(
                onNeedFullOnboarding = {
                    navController.navigate(Destinations.ONBOARDING_INTRO) {
                        popUpTo(Destinations.ENTRY) { inclusive = true }
                    }
                },
                onAllReady = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.ENTRY) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.ONBOARDING_INTRO) {
            OnboardingIntroScreen(
                onStartSetup = {
                    navController.navigate(Destinations.PERMISSION_FLOW)
                }
            )
        }

        composable(Destinations.PERMISSION_FLOW) {
            PermissionFlowScreen(
                onFlowFinished = {
                    navController.navigate(Destinations.ONBOARDING_READY) {
                        popUpTo(Destinations.ONBOARDING_INTRO) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.PERMISSION_FLOW_FIX) {
            PermissionFlowScreen(
                onFlowFinished = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.ENTRY) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.ONBOARDING_READY) {
            OnboardingReadyScreen(
                onSelectApps = {
                    navController.navigate(Destinations.APP_SELECT)
                }
            )
        }

        composable(Destinations.APP_SELECT) {
            AppSelectScreen(
                onFinished = {
                    navController.navigate(Destinations.ONBOARDING_START_MODE) {
                        popUpTo(Destinations.ONBOARDING_READY) { inclusive = false }
                    }
                },
                onFinishedWithoutPermission = {
                    navController.navigate(Destinations.ONBOARDING_FINISH) {
                        popUpTo(Destinations.ONBOARDING_READY) {inclusive = false}
                    }
                }
            )
        }

        composable(Destinations.APP_SELECT_SETTINGS) {
            AppSelectScreen(
                onFinished = {
                    navController.popBackStack()
                },
                onFinishedWithoutPermission = {
                    navController.popBackStack()
                }
            )
        }

        composable(Destinations.ONBOARDING_START_MODE) {
            OnboardingStartModeScreen(
                onDecide = {
                    navController.navigate(Destinations.ONBOARDING_FINISH) {
                        popUpTo(Destinations.APP_SELECT) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.ONBOARDING_FINISH) {
            val context = LocalContext.current
            val activity = context as? Activity
            OnboardingFinishScreen(
                onCloseApp = {
                    activity?.finishAffinity()
                },
                onOpenApp = {
                    Log.d("NavGraphs", "ONBOARDING_FINISH onOpenApp → startOverlayService")
                    context.startOverlayService()
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.APP_SELECT) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            HomeScreen(
                onOpenAppSelect = {
                    navController.navigate(Destinations.APP_SELECT_SETTINGS)
                }
            )
        }
    }
}

