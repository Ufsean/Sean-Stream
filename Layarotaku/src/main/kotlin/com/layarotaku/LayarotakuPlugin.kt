package com.layarotaku

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarotakuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Layarotaku())
    }
}
