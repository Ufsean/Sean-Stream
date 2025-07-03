package com.juraganfilm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import java.net.URI

internal data class Sources(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
)

abstract class JuraganFilmExtractor : ExtractorApi() {
    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val serverUrl = getBaseUrl(url)
        val mIframe = url.substringAfter("iframe=", "")

        if (mIframe.contains("getbk")) {
            val script = app.get(
                url,
                referer = "$serverUrl/"
            ).document.selectFirst("script:containsData(sources)")?.data() ?: return

            val json = "sources:\\s*\\[(.*)]".toRegex().find(script)?.groupValues?.get(1)
            AppUtils.tryParseJson<ArrayList<Sources>>("[$json]")?.map {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        it.file ?: return@map,
                    ) {
                        this.referer = "$serverUrl/"
                        this.quality = getQualityFromName(it.label)
                        this.type = INFER_TYPE!!
                    }
                )
            }
        } else if (mIframe.contains("gdrivehls") || mIframe.contains("gdriveplayer")) {
            val document = app.get(url, referer = referer).document
            val scriptData = document.select("script").find { it.data().contains("jwplayer") }?.data() ?: return

            val fileRegex = """"file":"([^"]+)"""".toRegex()
            fileRegex.findAll(scriptData).forEach { match ->
                val fileUrl = match.groupValues[1].replace("\\/", "/")
                if (fileUrl.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            name,
                            name,
                            fileUrl,
                        ) {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                            this.type = if (fileUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE!!
                        }
                    )
                }
            }
        }
    }
}

class GetBk : JuraganFilmExtractor() {
    override var name = "GetBk"
    override var mainUrl = "https://juragan.movie"
    override val requiresReferer = true
}

class GdriveHls : JuraganFilmExtractor() {
    override var name = "GdriveHls"
    override var mainUrl = "https://juragan.movie"
    override val requiresReferer = true
}

class GdrivePlayer : JuraganFilmExtractor() {
    override var name = "GdrivePlayer"
    override var mainUrl = "https://juragan.movie"
    override val requiresReferer = true
}
