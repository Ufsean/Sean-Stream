package com.animefreak

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeFreakPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeFreakProvider())
        registerExtractorAPI(KotoStreamExtractor())
    }
}
