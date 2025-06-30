package com.animeindo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimeIndoProvider : MainAPI() {
    override var mainUrl = "https://anime-indo.lol"
    override var name = "AnimeIndo"
    override val supportedTypes = setOf(TvType.Anime)
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.select("p, a").text()
        val href = this.select("a").attr("href")
        if (href.isBlank()) return null
        val posterUrl = this.select("img")?.attr("data-original")
        return newAnimeSearchResponse(title, "$mainUrl$href") {
            posterUrl?.let { this.posterUrl = "$mainUrl$it" }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val homePageList = ArrayList<HomePageList>()

        val updates = document.select("div.list-anime").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Update Terbaru", updates))

        try {
            val movieDocument = app.get("$mainUrl/movie/").document
            val movies = movieDocument.select("table.otable tr").mapNotNull {
                val title = it.selectFirst("a")?.text() ?: return@mapNotNull null
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val posterUrl = it.selectFirst("img")?.attr("src")
                newAnimeSearchResponse(title, "$mainUrl$href") {
                    posterUrl?.let { this.posterUrl = "$mainUrl$it" }
                }
            }
            homePageList.add(HomePageList("Movie", movies))
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val allAnimeDocument = app.get("$mainUrl/anime-list/").document
            val allAnime = allAnimeDocument.select("div.anime-list li").mapNotNull {
                val title = it.selectFirst("a")?.text() ?: return@mapNotNull null
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newAnimeSearchResponse(title, "$mainUrl$href")
            }
            homePageList.add(HomePageList("Semua Anime", allAnime))
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val genreDocument = app.get("$mainUrl/list-genre/").document
            val genres = genreDocument.select("div.list-genre a").mapNotNull {
                val title = it.text()
                val href = it.attr("href")
                newAnimeSearchResponse(title, href) // Genre links are absolute
            }
            homePageList.add(HomePageList("Daftar Genre", genres))
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val jadwalDocument = app.get("$mainUrl/jadwal/").document
            val jadwal = jadwalDocument.select("table.ztable tr").mapNotNull {
                val title = it.selectFirst("a")?.text() ?: return@mapNotNull null
                val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newAnimeSearchResponse(title, "$mainUrl$href")
            }
            homePageList.add(HomePageList("Jadwal Rilis", jadwal))
        } catch (e: Exception) {
            // Ignore
        }

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val document = app.get(url).document

        val results = mutableListOf<SearchResponse>()
        document.select("div.list-anime").forEach { element ->
            element.toSearchResponse()?.let {
                results.add(it)
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.detail img")?.attr("src")?.let { "$mainUrl$it" }
        val plot = document.selectFirst("div.detail p")?.text()?.trim()
        val genres = document.select("div.detail li a").map { it.text() }

        val episodes = document.select("div.ep a").mapNotNull {
            val href = it.attr("href")
            val name = "Episode ${it.text().trim()}"
            newEpisode("$mainUrl$href") {
                this.name = name
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = genres
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        var linksLoaded = false

        document.select("a.server").forEach {
            val url = it.attr("data-video")
            if (url.isNotBlank()) {
                val fullUrl = if (url.startsWith("http")) url else "$mainUrl$url"
                linksLoaded = loadExtractor(fullUrl, data, subtitleCallback, callback) || linksLoaded
            }
        }
        return linksLoaded
    }
}
