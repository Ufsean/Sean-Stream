package com.layaranime

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLDecoder

class SemacamCdn : ExtractorApi() {
    override val name = "SemacamCdn"
    override val mainUrl = "https://itadakimasu.semacamcdn.site"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val decodedUrl = URLDecoder.decode(url.substringAfter("?url="), "UTF-8")
        app.get(decodedUrl, referer = referer).document.let { document ->
            val m3u8Link = document.selectFirst("video source")?.attr("src")
            if (m3u8Link != null) {
                M3u8Helper.generateM3u8(
                    name,
                    m3u8Link,
                    decodedUrl
                ).forEach(callback)
            }
        }
    }
}
