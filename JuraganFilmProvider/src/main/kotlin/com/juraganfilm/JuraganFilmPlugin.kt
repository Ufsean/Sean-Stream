package com.juraganfilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JuraganFilmPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JuraganFilmProvider())
        registerExtractorAPI(GetBk())
        registerExtractorAPI(GdriveHls())
    }
}
