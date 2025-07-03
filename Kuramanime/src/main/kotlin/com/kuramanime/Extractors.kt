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
import com.lagradost.cloudstream3.network.WebViewResolver 
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jsoup.Jsoup
import android.util.Log
import org.jsoup.nodes.Document

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

class KuramaMain : ExtractorApi() {
    override val name = "KuramaMain"
    override val mainUrl = "https://v8.kuramanime.run"
    override val requiresReferer = true
    // Baris 'usesWebView' dihapus karena tidak kompatibel

    override suspend fun getUrl(
        url: String, // URL Episode
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("KuramaDebug", "Starting getUrl with WebViewResolver for: $url")

        // Regex yang lebih fokus untuk menangkap pola URL embed video
        val iframeRegex = Regex("""(filemoon|streamwish|kuramadrive)\.(?:sx|to|com)/(?:e|v|f|embed)/([a-zA-Z0-9]+)""")

        // Memanggil app.get dengan interceptor.
        val response = app.get(url, referer = referer, interceptor = WebViewResolver(iframeRegex))
        val interceptedUrl = response.url

        // Periksa apakah URL yang ditangkap adalah URL embed yang valid
        if (interceptedUrl.matches(iframeRegex)) {
            Log.d("KuramaDebug", "SUCCESS! WebViewResolver intercepted valid URL: $interceptedUrl")
            loadExtractor(interceptedUrl, url, subtitleCallback, callback)
        } else {
            // Jika interceptor timeout, URL yang dikembalikan adalah URL asli.
            // Kita coba parse dokumen terakhir yang berhasil dimuat oleh WebView sebagai fallback.
            Log.d("KuramaDebug", "Interceptor timed out or failed. Parsing last loaded document as a fallback.")
            val document = response.document

            // 1. Cari iframe streaming
            document.select("div#animeVideoPlayer iframe[src]").forEach { iframe ->
                val iframeSrc = iframe.attr("src")
                if (iframeSrc.isNotBlank()) {
                    Log.d("KuramaDebug", "Fallback: Found streaming iframe: $iframeSrc")
                    loadExtractor(iframeSrc, url, subtitleCallback, callback)
                }
            }

            // 2. Cari link download
            document.select("div#animeDownloadLink a[href]").forEach { link ->
                val downloadUrl = link.attr("href")
                if (downloadUrl.isNotBlank()) {
                    Log.d("KuramaDebug", "Fallback: Found download link: $downloadUrl")
                    loadExtractor(downloadUrl, url, subtitleCallback, callback)
                }
            }
        }
    }
}