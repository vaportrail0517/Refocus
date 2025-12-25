package com.example.refocus.system.appinfo

import com.example.refocus.domain.gateway.AppLabelProvider

/**
 * domain.gateway.AppLabelProvider を system 実装（AppLabelResolver）にアダプトする．
 */
class AppLabelProviderImpl(
    private val resolver: AppLabelResolver,
) : AppLabelProvider {
    override fun labelOf(packageName: String): String = resolver.labelOf(packageName)
}
