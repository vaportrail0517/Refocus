package com.example.refocus.system.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Service とは独立した Lifecycle を持つ、SavedStateRegistryOwner のダミー実装。
 *
 * - Compose 側は ViewTreeSavedStateRegistryOwner が存在することだけを要求する
 * - 実際の状態保存/復元は行わない（Bundle は常に null）
 */
class OverlaySavedStateOwner : SavedStateRegistryOwner {
    // 自前の LifecycleRegistry を持つ
    private val lifecycleRegistry = LifecycleRegistry(this)

    // SavedStateRegistryController を自前の Lifecycle 上に構成
    private val controller = SavedStateRegistryController.Companion.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    init {
        // INITIALIZED の状態で attach/restore を実行する
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        controller.performRestore(null)
        // 最低限 CREATED に遷移させておく（以降は特に進めなくても問題なし）
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
}
