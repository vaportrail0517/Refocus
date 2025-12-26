package com.example.refocus.domain.overlay

interface OverlayServiceController {
    /**
     * 必要な権限が揃っている場合のみ OverlayService を起動する．
     *
     * @return 起動した場合は true．起動しなかった場合は false．
     */
    fun startIfReady(source: String): Boolean
}
