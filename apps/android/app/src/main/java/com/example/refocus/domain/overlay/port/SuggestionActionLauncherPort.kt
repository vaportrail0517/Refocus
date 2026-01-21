package com.example.refocus.domain.overlay.port

import com.example.refocus.core.model.SuggestionAction

/**
 * ドメイン層から見た「Suggestion に紐づく Action の実行」の抽象。
 *
 * - URL / 他アプリ起動など，Android の Intent 操作は system 側に閉じ込める。
 * - domain からは「何を開くか」だけを渡す。
 */
interface SuggestionActionLauncherPort {
    /**
     * Action を実行する。
     *
     * 実装側は UI スレッドへの切り替えや，失敗時の通知（Toast 等）を担当する。
     */
    fun launch(action: SuggestionAction)
}
