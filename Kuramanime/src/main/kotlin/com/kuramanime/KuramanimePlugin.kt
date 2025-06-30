package com.kuramanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KuramanimePlugin: Plugin() {
    override fun load(context: Context) {
        // Register the main provider
        registerMainAPI(Kuramanime())
        
        // You can register additional extractors here if needed
        registerExtractorAPI(Nyomo())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(KuramaMain())
    }
}
