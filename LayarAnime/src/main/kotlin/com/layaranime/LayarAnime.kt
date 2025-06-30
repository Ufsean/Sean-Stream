package com.layaranime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarAnime : MainAPI() {
    override var mainUrl = "https://layaranime.com"
    override var name = "LayarAnime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    // Companion object ini berisi dua fungsi utilitas yang digunakan untuk menentukan tipe anime dan status penayangan berdasarkan string input.
    companion object {
        /**
         * Fungsi getType digunakan untuk mengidentifikasi tipe anime berdasarkan string yang diberikan.
         * Jika parameter t bernilai null, maka akan mengembalikan TvType.Anime.
         * Jika string mengandung kata "Series" (tidak case sensitive), maka dianggap sebagai TvType.Anime.
         * Jika string mengandung kata "Movie", maka dianggap sebagai TvType.AnimeMovie.
         * Jika string mengandung "OVA" atau "Special", maka dianggap sebagai TvType.OVA.
         * Jika tidak ada yang cocok, default-nya akan mengembalikan TvType.Anime.
         */
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Series", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        /**
         * Fungsi getStatus digunakan untuk menentukan status penayangan anime berdasarkan string yang diberikan.
         * Jika parameter t bernilai null, maka akan mengembalikan ShowStatus.Completed (selesai).
         * Jika string mengandung kata "Ongoing" (tidak case sensitive), maka statusnya dianggap masih berjalan (ShowStatus.Ongoing).
         * Jika tidak, maka statusnya dianggap sudah selesai (ShowStatus.Completed).
         */
        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Ongoing", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "anime-episode-terbaru/page/" to "Anime Episode Terbaru",
                    "donghua-episode-terbaru/page/" to "Donghua Episode Terbaru",
                    "page/" to "Anime Populer"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}$page"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("h4")?.text()?.trim() ?: return null
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

        val title = document.selectFirst("h1.font-bold")?.text()?.replace("Nonton ", "") ?: ""
        val poster = document.selectFirst("div.flex-col img")?.attr("src")
        val year = document.selectFirst("a[href*='/tahun/']")?.text()?.trim()?.toIntOrNull()
        
        val genres = document.select("div.col-span-5 a[href*='/genre/']").map { it.text() }
        val typeText = genres.firstOrNull { it.equals("Movie", ignoreCase = true) } ?: "Series"
        val type = getType(typeText)

        val statusText = document.selectFirst("div.col-span-5 > span")?.text()
        val status = getStatus(statusText)

        val plot = document.selectFirst("blockquote > div")?.text()

        val episodes = document.select("div.episode a").mapNotNull { el ->
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
        val downloadLinks = document.select("a[href*='itadakimasu.semacamcdn.site/download/']")
        
        downloadLinks.amap { link ->
            val videoId = link.attr("href").substringAfterLast("/")
            val filemoonUrl = "https://filemoon.in/embed/$videoId"
            loadExtractor(filemoonUrl, data, subtitleCallback, callback)
        }

        return true
    }
}
