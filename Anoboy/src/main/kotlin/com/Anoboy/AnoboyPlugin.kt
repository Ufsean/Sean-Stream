package com.anoboy

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnoboyPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Anoboy())
        // Register extractors here if needed in the future
    }
}
