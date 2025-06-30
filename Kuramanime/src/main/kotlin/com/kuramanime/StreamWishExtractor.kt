package com.kuramanime

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class StreamWishExtractor : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<ExtractorLink>? {
        val doc = app.get(url, referer = referer).document
        val script = doc.select("script:containsData(sources:)")?.firstOrNull()?.data()
            ?: return null

        val masterUrl = Regex("""file:\s*"(https://[^"]+index\.m3u8)"""").find(script)?.groupValues?.get(1)
            ?: return null

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = masterUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P360.value
                this.headers = mapOf("Referer" to (referer ?: ""))
            }
        )
    }
}
