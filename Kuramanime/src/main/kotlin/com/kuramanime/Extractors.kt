package com.kuramanime

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.Jsoup

class Nyomo : StreamSB() {
    override var name: String = "Nyomo"
    override var mainUrl = "https://nyomo.my.id"
}

class FileMoon : Filesim() {
    override var name: String = "FileMoon"
    override var mainUrl: String = "https://filemoon.in"
    override val requiresReferer = true
}

class Mega : ExtractorApi() {
    override val name = "Mega"
    override val mainUrl = "https://mega.nz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val iframe = doc.select("iframe").attr("src")
        if (iframe.isNotBlank()) {
            loadExtractor(iframe, referer, subtitleCallback, callback)
        }
    }
}

class StreamWish : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val iframe = doc.select("iframe").attr("src")
        if (iframe.isNotBlank()) {
            loadExtractor(iframe, referer, subtitleCallback, callback)
        }
    }
}

class VidGuard : ExtractorApi() {
    override val name = "VidGuard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val iframe = doc.select("iframe").attr("src")
        if (iframe.isNotBlank()) {
            loadExtractor(iframe, referer, subtitleCallback, callback)
        }
    }
}

class KuramaMain : ExtractorApi() {
    override val name = "KuramaMain"
    override val mainUrl = "https://v8.kuramanime.run"
    override val requiresReferer = true

    private data class CheckEpisodeResponse(
        @JsonProperty("player") val player: String?,
        @JsonProperty("download") val download: String?
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Step 1: Get the main page to extract the CSRF token
        val mainPageDoc = app.get(url, referer = referer).document
        val csrfToken = mainPageDoc.selectFirst("meta[name=csrf-token]")?.attr("content")
        if (csrfToken.isNullOrBlank()) return

        // Step 2: Call the check-episode API
        val apiUrl = "$url/check-episode"
        val apiResponse = app.get(
            apiUrl,
            headers = mapOf(
                "X-CSRF-TOKEN" to csrfToken,
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url
            )
        ).parsedSafe<CheckEpisodeResponse>() ?: return

        // Step 3: Process the player HTML from the API response
        apiResponse.player?.let { playerHtml ->
            val playerDoc = Jsoup.parse(playerHtml)
            playerDoc.select("video#player source").forEach { source ->
                val src = source.attr("src")
                val quality = source.attr("size")
                if (src.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(this.name, src, this.mainUrl, ExtractorLinkType.VIDEO) {
                            this.quality = getQualityFromName(quality)
                        }
                    )
                }
            }
        }

        // Step 4: Process the download links HTML from the API response
        apiResponse.download?.let { downloadHtml ->
            val downloadDoc = Jsoup.parse(downloadHtml)
            downloadDoc.select("a").forEach { link ->
                val downloadUrl = link.attr("href")
                if (downloadUrl.isNotBlank()) {
                    loadExtractor(downloadUrl, url, subtitleCallback, callback)
                }
            }
        }
    }
}
