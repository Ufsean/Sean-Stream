package com.winbu

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName

class Buzzheavier : ExtractorApi() {
    override val name = "Buzzheavier"
    override val mainUrl = "https://buzzheavier.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val previewUrl = "$url/preview"
        val document = app.get(previewUrl, referer = referer).document
        val sourceUrl = document.selectFirst("video source")?.attr("src")

        if (sourceUrl != null) {
            callback(
                newExtractorLink(name, name, sourceUrl, type = ExtractorLinkType.VIDEO) {
                    this.quality = getQualityFromName("480p")
                    this.referer = mainUrl
                }
            )
        }
    }
}
