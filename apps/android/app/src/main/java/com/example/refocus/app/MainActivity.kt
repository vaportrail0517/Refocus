package com.example.refocus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.refocus.app.navigation.RefocusNavHost
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.system.overlay.service.OverlayRunReconciler
import com.example.refocus.system.permissions.PermissionStateWatcher
import com.example.refocus.ui.theme.RefocusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionStateWatcher: PermissionStateWatcher

    @Inject
    lateinit var overlayRunReconciler: OverlayRunReconciler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RefocusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RefocusNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 設定画面から戻ってきた直後などに権限状態が変わっている可能性がある
        lifecycleScope.launch {
            try {
                permissionStateWatcher.checkAndRecord()
                // 期待状態（overlayEnabled + 権限）と実稼働状態がズレていれば自動修復する
                overlayRunReconciler.ensureConsistent(source = "activity_onResume")
            } catch (e: Exception) {
                RefocusLog.e(
                    "MainActivity",
                    e,
                ) { "Failed to check/record permission state onResume" }
            }
        }
    }
}
