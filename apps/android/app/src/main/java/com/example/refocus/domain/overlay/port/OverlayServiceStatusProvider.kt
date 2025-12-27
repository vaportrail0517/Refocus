package com.example.refocus.domain.overlay.port

/**
 * OverlayService の稼働状態を参照するための境界．
 *
 * feature 層が system の具象（OverlayService）へ直接依存しないようにするために用意する．
 */
interface OverlayServiceStatusProvider {
    fun isRunning(): Boolean
}