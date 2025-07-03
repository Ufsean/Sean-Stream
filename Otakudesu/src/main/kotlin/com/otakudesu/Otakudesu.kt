package com.otakudesu

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.extractors.JWPlayer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Otakudesu : MainAPI() {
    override var mainUrl = "https://otakudesu.cloud"
    override var name = "Otakudesu"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        const val acefile = "https://acefile.co"
        val mirrorBlackList =
            arrayOf(
                "Mega",
                "MegaUp",
                "Otakufiles",
            )
        fun getType(t: String): TvType {
            return if (t.contains("OVA", true) || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when {
                t.contains("Completed", true) || t.contains("Tamat", true) -> ShowStatus.Completed
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
        
        fun getQuality(str: String?): Int {
            return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
        }
    }

    override val mainPage =
        mainPageOf(
            "$mainUrl/ongoing-anime/page/" to "Anime Ongoing",
            "$mainUrl/complete-anime/page/" to "Anime Completed",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.venz > ul > li").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val title = this.selectFirst("h2.jdlflm")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = this.select("div.thumbz > img").attr("src").toString()
        val epNum =
            this.selectFirst("div.epz")
                ?.ownText()
                ?.replace(Regex("\\D"), "")
                ?.trim()
                ?.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query&post_type=anime")
            .document
            .select("ul.chivsrc > li")
            .map {
                val title = it.selectFirst("h2 > a")!!.ownText().trim()
                val href = it.selectFirst("h2 > a")!!.attr("href")
                val posterUrl = it.selectFirst("img")!!.attr("src").toString()
                newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        var title = ""
        var status: ShowStatus = ShowStatus.Completed
        var type: TvType = TvType.Anime
        var year: Int? = null
        val tags = mutableListOf<String>()

        val poster = document.selectFirst("div.fotoanime > img")?.attr("src")
        val description = document.select("div.sinopc > p").text()

        document.select("div.infozingle > p").forEach { element ->
            val label = element.selectFirst("span")?.text()?.lowercase() ?: ""
            val value = element.ownText().replace(":", "").trim()

            when {
                label.contains("judul") -> title = value
                label.contains("status") -> status = getStatus(value)
                label.contains("tipe") -> type = getType(value)
                label.contains("tanggal rilis") -> year = Regex("\\d{4}").find(value)?.value?.toIntOrNull()
                label.contains("genre") -> {
                    tags.addAll(element.select("a").map { it.text() })
                }
            }
        }
        
        if (title.isBlank()) {
            title = document.selectFirst(".jdlrx, .posttl")?.text() ?: ""
        }

        val episodes =
            document.select("div.episodelist")[1]
                .select("ul > li")
                .mapNotNull {
                    val name = it.selectFirst("a")?.text() ?: return@mapNotNull null
                    val episodeNum = Regex("Episode\\s?(\\d+)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val link = fixUrl(it.selectFirst("a")!!.attr("href"))
                    newEpisode(link) {
                        this.name = name
                        this.episode = episodeNum
                    }
                }
                .reversed()

        val recommendations =
            document.select("div.isi-recommend-anime-series > div.isi-konten").map {
                val recName = it.selectFirst("span.judul-anime > a")!!.text()
                val recHref = it.selectFirst("a")!!.attr("href")
                val recPosterUrl = it.selectFirst("a > img")?.attr("src").toString()
                newAnimeSearchResponse(recName, recHref, TvType.Anime) {
                    this.posterUrl = recPosterUrl
                }
            }

        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
    }

    data class ResponseSources(
        @JsonProperty("id") val id: String,
        @JsonProperty("i") val i: String,
        @JsonProperty("q") val q: String,
    )

    data class ResponseData(@JsonProperty("data") val data: String)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        try {
            val scriptData = document.select("script:containsData(action:)").lastOrNull()?.data()
            val token = scriptData?.substringAfter("{action:\"")?.substringBefore("\"}") ?: ""
            val nonce = app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf("action" to token)).parsed<ResponseData>().data ?: ""
            val action = scriptData?.substringAfter(",action:\"")?.substringBefore("\"}") ?: ""

            if (nonce.isNotBlank() && action.isNotBlank()) {
                val mirrorData = document.select("div.mirrorstream > ul > li").mapNotNull { base64Decode(it.select("a").attr("data-content")) }.toString()

                tryParseJson<List<ResponseSources>>(mirrorData)?.forEach { res ->
                    val postData = mapOf("id" to res.id, "i" to res.i, "q" to res.q, "nonce" to nonce, "action" to action)
                    val sources = Jsoup.parse(
                        base64Decode(app.post("${mainUrl}/wp-admin/admin-ajax.php", data = postData).parsed<ResponseData>().data)
                    ).select("iframe").attr("src")

                    loadCustomExtractor(sources, data, subtitleCallback, callback, getQuality(res.q))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            document.select("div.download li").forEach { ele ->
                val quality = getQuality(ele.select("strong").text())
                ele.select("a")
                    .filter { !inBlacklist(it.text()) && quality != Qualities.P360.value }
                    .forEach { link ->
                        val url = app.get(link.attr("href"), referer = "$mainUrl/").url
                        loadCustomExtractor(fixedIframe(url), data, subtitleCallback, callback, quality)
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int = Qualities.Unknown.value,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            kotlinx.coroutines.runBlocking {
                callback.invoke(
                    newExtractorLink(link.name, link.name, link.url, link.type) {
                        this.referer = link.referer
                        this.quality = quality
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }

    private fun fixedIframe(url: String): String {
        return when {
            url.startsWith(acefile) -> {
                val id = Regex("""(?:/f/|/file/)(\w+)""").find(url)?.groupValues?.getOrNull(1)
                "${acefile}/player/$id"
            }
            else -> fixUrl(url)
        }
    }

    private fun inBlacklist(host: String?): Boolean {
        return mirrorBlackList.any { it.equals(host, true) }
    }
}

class Moedesu : JWPlayer() {
    override val name = "Moedesu"
    override val mainUrl = "https://desustream.me/moedesu/"
}

class DesuBeta : JWPlayer() {
    override val name = "DesuBeta"
    override val mainUrl = "https://desustream.me/beta/"
}

class Desudesuhd : JWPlayer() {
    override val name = "Desudesuhd"
    override val mainUrl = "https://desustream.me/desudesuhd/"
}
