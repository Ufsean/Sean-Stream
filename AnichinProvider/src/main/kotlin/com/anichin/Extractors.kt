package com.anichin

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class OkCdn : Odnoklassniki() {
    override var name = "OkCdn"
    override var mainUrl = "https://okcdn.ru"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = java.net.URL(url).host
        this.mainUrl = "https://$host"
        super.getUrl(url, referer, subtitleCallback, callback)
    }
}

open class AnichinExtractor : ExtractorApi() {
    override val name = "Anichin"
    override var mainUrl = "https://anichin.cafe"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = response.document.selectFirst("script:containsData(player.src)")?.data()
            ?: return
        
        val videoUrl = script.substringAfter("src: '").substringBefore("'")
        if (videoUrl.isNotBlank()) {
            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.referer = url
                    this.quality = getQualityFromName("")
                }
            )
        }
    }
}

class Rumble : ExtractorApi() {
    override val name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val ldJsonScript = doc.selectFirst("script[type=\"application/ld+json\"]")?.data() ?: return
        
        val embedUrl = AppUtils.tryParseJson<Map<String, String>>(ldJsonScript)?.get("embedUrl") ?: return

        val embedPageText = app.get(embedUrl, referer = url).text
        
        val videoUrlRegex = Regex(""""url":"(https:[^"]+?\.mp4)","meta":\{"w":\d+,"h":(\d+)""")
        videoUrlRegex.findAll(embedPageText).forEach { match ->
            val videoUrl = match.groupValues[1]
            val height = match.groupValues[2]
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name ${height}p",
                    url = videoUrl,
                    type = INFER_TYPE,
                ) {
                    this.referer = mainUrl
                    this.quality = getQualityFromName("${height}p")
                }
            )
        }
    }
}

class ShortIcu : ExtractorApi() {
    override val name = "Short.icu"
    override var mainUrl = "https://short.icu"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer, allowRedirects = false)
        val redirectedUrl = response.headers["Location"]
        if (redirectedUrl != null) {
            loadExtractor(redirectedUrl, url, subtitleCallback, callback)
        }
    }
}

class RubyVidHub : ExtractorApi() {
    override val name = "RubyVidHub"
    override var mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text
        val m3u8Regex = Regex("""file:\s*'([^']+.m3u8)'""")
        val match = m3u8Regex.find(response)
        val m3u8Url = match?.groupValues?.get(1)
        
        if (m3u8Url != null) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = INFER_TYPE,
                ) {
                     this.referer = mainUrl
                }
            )
        }
    }
}