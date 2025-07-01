package com.anichin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnichinProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // Register the main provider
        registerMainAPI(AnichinProvider())
        registerExtractorAPI(AnichinExtractor())
        registerExtractorAPI(OkCdn())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(ShortIcu())
        registerExtractorAPI(RubyVidHub())
    }
}