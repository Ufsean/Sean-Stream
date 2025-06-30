package com.animasu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimasuPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimasuProvider())
    }
}
