package com.leapauto.app.ui

internal data class HomeLocationPill(val label: String, val opensConfiguration: Boolean)

internal fun homeLocationPill(hasConfiguredKey: Boolean, address: String?): HomeLocationPill? =
    if (!hasConfiguredKey) {
        HomeLocationPill("位置服务待配置", opensConfiguration = true)
    } else {
        address?.takeIf { it.isNotBlank() }?.let { HomeLocationPill(it, opensConfiguration = false) }
    }
