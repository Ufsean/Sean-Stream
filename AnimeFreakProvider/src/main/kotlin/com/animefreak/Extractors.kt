package com.animefreak

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.util.Base64

class KotoStreamExtractor : ExtractorApi() {
    override val name = "KotoStream"
    override val mainUrl = "https://kotostream.online"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        return when {
            // Handles URLs like: https://kotostream.online/player/plyr.php#BASE64STRING
            url.contains("kotostream.online") -> {
                try {
                    val decodedUrl = String(Base64.getDecoder().decode(url.substringAfter("#").substringBefore("?")))
                    M3u8Helper.generateM3u8(
                        this.name,
                        decodedUrl,
                        referer ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }
            // Handles URLs like: https://plyr.zorox.live/stream/...
            url.contains("plyr.zorox.live") -> {
                try {
                    val parsedUrl = java.net.URL(url)
                    val domain = parsedUrl.query?.substringAfter("domain=")
                    val path = parsedUrl.path
                    if (domain != null) {
                        val finalUrl = "https://$domain$path"
                        M3u8Helper.generateM3u8(
                            this.name,
                            finalUrl,
                            referer ?: ""
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
