package com.example.refocus.domain.overlay.port

interface OverlayServiceController {
    /**
     * 必要な権限が揃っている場合のみ OverlayService を起動する．
     *
     * @return 起動した場合は true．起動しなかった場合は false．
     */
    fun startIfReady(source: String): Boolean

    /**
     * OverlayService を停止する．
     *
     * 権限が失効した場合や，ユーザが「一時停止」を選んだ場合などに使う．
     */
    fun stop(source: String)
}
