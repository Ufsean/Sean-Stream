package com.winbu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element

class Winbu : MainAPI() {
    override var name = "Winbu"
    override var mainUrl = "https://winbu.tv"
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Movie,
        TvType.TvSeries
    )

    override val hasMainPage = true

    private fun toSearchResponse(element: Element): SearchResponse? {
        val title = element.selectFirst("div.judul")?.text()?.trim() ?: return null
        val href = element.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = element.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.list-title h2, h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".mli-thumb-box img, div.thumb img")?.attr("src")
        val description = document.selectFirst(".mli-desc p, div.entry-content[itemprop=description]")?.text()?.trim()
        val genres = document.select(".mli-mvi:contains(Genre) a, div.genxed a, div.mgen a").map { it.text().trim() }

        val episodes = document.select("div.les-content a, div.eplister ul li a").mapNotNull {
            val episodeUrl = it.attr("href")
            val episodeTitle = it.selectFirst(".epl-title")?.text() ?: it.text()
            val episodeNum = it.selectFirst(".epl-num")?.text()?.toIntOrNull()

            newEpisode(episodeUrl) {
                this.name = episodeTitle.ifBlank { "Episode ${episodeNum ?: ""}".trim() }
                episodeNum?.let { this.episode = it }
            }
        }.reversed()

        val type = when {
            genres.any { it.equals("Movie", ignoreCase = true) } -> TvType.Movie
            genres.any { it.equals("Series", ignoreCase = true) } -> TvType.TvSeries
            else -> TvType.Anime
        }

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster
            this.plot = description
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

        // Handle AJAX player links
        document.select("div.east_player_option").forEach {
            val post = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            val qualityName = it.text()

            val response = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "player_ajax",
                    "post" to post,
                    "nume" to nume,
                    "type" to type
                ),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).document

            response.select("iframe").forEach { iframe ->
                val url = iframe.attr("src")
                if (url.isNotBlank()) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        // Handle direct download links
        document.select("div.download-eps li").forEach {
            val quality = it.selectFirst("strong")?.text()
            it.select("a").forEach { link ->
                val url = link.attr("href")
                if (url.contains("buzzheavier.com")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document

        val homePageList = mutableListOf<HomePageList>()

        val topSeries = document.select("div.movies-list-wrap").first()?.select("div.ml-item-potrait")?.mapNotNull {
            toSearchResponse(it)
        }
        if (!topSeries.isNullOrEmpty()) {
            homePageList.add(HomePageList("Top 10 Series", topSeries, isHorizontalImages = true))
        }

        val newAnime = document.select("div.list-title:contains(Anime Donghua Terbaru) + div.movies-list div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
        if (!newAnime.isNullOrEmpty()) {
            homePageList.add(HomePageList("Anime Donghua Terbaru", newAnime))
        }
        
        val topMovies = document.select("div.movies-list-wrap:contains(Top 10 Film)").first()?.select("div.ml-item-potrait")?.mapNotNull {
            toSearchResponse(it)
        }
        if (!topMovies.isNullOrEmpty()) {
            homePageList.add(HomePageList("Top 10 Film", topMovies, isHorizontalImages = true))
        }

        val newMovies = document.select("div.list-title:contains(Film Terbaru) + div.movies-list div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
        if (!newMovies.isNullOrEmpty()) {
            homePageList.add(HomePageList("Film Terbaru", newMovies))
        }

        val others = document.select("div.list-title:contains(Jepang Korea China Barat) + div.movies-list div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
        if (!others.isNullOrEmpty()) {
            homePageList.add(HomePageList("Jepang Korea China Barat", others))
        }

        val tvShow = document.select("div.list-title:contains(TV Show) + div.movies-list div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
        if (!tvShow.isNullOrEmpty()) {
            homePageList.add(HomePageList("TV Show", tvShow))
        }

        return newHomePageResponse(homePageList)
    }
}
