package com.layarotaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Layarotaku : MainAPI() {
    override var mainUrl = "https://layarotaku.com"
    override var name = "Layarotaku"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Series", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "anime-terbaru/page/" to "Anime Terbaru",
        "anime-populer/page/" to "Anime Populer",
        "movie/page/" to "Anime Movie"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}$page"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        if (title.isBlank()) return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: ""
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val year = document.selectFirst("span.date a")?.text()?.trim()?.toIntOrNull()
        
        val genres = document.select("span.cat-links a").map { it.text() }
        val typeText = genres.firstOrNull { it.equals("Movie", ignoreCase = true) } ?: "Series"
        val type = getType(typeText)

        val statusText = document.selectFirst("span.status")?.text()
        val status = getStatus(statusText)

        val plot = document.selectFirst("div.entry-content")?.text()

        val episodes = document.select("div.episode-list a").mapNotNull { el ->
            val epHref = el.attr("href")
            val epName = el.text().trim()
            if (epHref.isBlank() || epName.isBlank()) null
            else newEpisode(epHref) { this.name = epName }
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            this.plot = plot
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe")?.attr("src") ?: return false
        loadExtractor(iframe, data, subtitleCallback, callback)
        return true
    }
}
