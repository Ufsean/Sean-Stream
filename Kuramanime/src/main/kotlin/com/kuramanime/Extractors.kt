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

class Nyomo : StreamSB() {
    override var name: String = "Nyomo"
    override var mainUrl = "https://nyomo.my.id"
}

class Streamhide : Filesim() {
    override var name: String = "Streamhide"
    override var mainUrl: String = "https://streamhide.to"
}

open class Lbx : ExtractorApi() {
    override val name = "Linkbox"
    override val mainUrl = "https://lbx.to"
    private val realUrl = "https://www.linkbox.to"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val token = Regex("""(?:/f/|/file/|\?id=)(\w+)""").find(url)?.groupValues?.get(1)
        val id =
                app.get(
                                "$realUrl/api/file/share_out_list/?sortField=utime&sortAsc=0&pageNo=1&pageSize=50&shareToken=$token"
                        )
                        .parsedSafe<Responses>()
                        ?.data
                        ?.itemId
        app.get("$realUrl/api/file/detail?itemId=$id", referer = url)
                .parsedSafe<Responses>()
                ?.data
                ?.itemInfo
                ?.resolutionList
                ?.map { link ->
                    callback.invoke(
                            newExtractorLink(
                                    name,
                                    name,
                                    link.url ?: return@map null,
                            ) {
                                this.referer = "$realUrl/"
                                this.quality = getQualityFromName(link.resolution)
                            }
                    )
                }
    }

    data class Resolutions(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("resolution") val resolution: String? = null,
    )

    data class ItemInfo(
            @JsonProperty("resolutionList")
            val resolutionList: ArrayList<Resolutions>? = arrayListOf(),
    )

    data class Data(
            @JsonProperty("itemInfo") val itemInfo: ItemInfo? = null,
            @JsonProperty("itemId") val itemId: String? = null,
    )

    data class Responses(
            @JsonProperty("data") val data: Data? = null,
    )
}

open class Kuramadrive : ExtractorApi() {
    override val name = "DriveKurama"
    override val mainUrl = "https://kuramadrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val req = app.get(url, referer = referer)
        val doc = req.document

        val title = doc.select("title").text()
        val token = doc.select("meta[name=csrf-token]").attr("content")
        val routeCheckAvl = doc.select("input#routeCheckAvl").attr("value")

        val json =
                app.get(
                                routeCheckAvl,
                                headers =
                                        mapOf(
                                                "X-Requested-With" to "XMLHttpRequest",
                                                "X-CSRF-TOKEN" to token
                                        ),
                                referer = url,
                                cookies = req.cookies
                        )
                        .parsedSafe<Source>()

        callback.invoke(
                newExtractorLink(
                        name,
                        name,
                        json?.url ?: return,
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = getIndexQuality(title)
                }
        )
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
    }

    private data class Source(
            @JsonProperty("url") val url: String,
    )
}

class FilemoonExtractor : ExtractorApi() {
    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.in"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val req = app.get(url, referer = referer)
        val doc = req.document

        // The m3u8 URL is usually in a script tag or source tag; parse accordingly
        val scriptText = doc.select("script:containsData(m3u8)").joinToString(" ") { it.data() }
        val m3u8Regex = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
        val m3u8Url = m3u8Regex.find(scriptText)?.value

        if (m3u8Url != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8Url,
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class MegaNzExtractor : ExtractorApi() {
    override val name = "Mega.nz"
    override val mainUrl = "https://mega.nz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Fetch the embed page and extract the video URL(s)
        val req = app.get(url, referer = referer)
        val doc = req.document

        // The video URL might be in a script tag or iframe; parse accordingly
        val iframeSrc = doc.selectFirst("iframe")?.attr("src")
        if (iframeSrc != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    iframeSrc,
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }

        // Fallback: try to find m3u8 URL in scripts
        val scriptText = doc.select("script:containsData(m3u8)").joinToString(" ") { it.data() }
        val m3u8Regex = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
        val m3u8Url = m3u8Regex.find(scriptText)?.value

        if (m3u8Url != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8Url,
                ) {
                    this.referer = referer ?: mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
