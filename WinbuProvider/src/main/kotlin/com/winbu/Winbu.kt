package com.winbu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
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
            it.select("a").forEach { link ->
                val url = link.attr("href")
                if (url.contains("buzzheavier.com")) {
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }

    override val mainPage = mainPageOf(
        "$mainUrl/animedonghua/page/" to "Anime Donghua Terbaru",
        "$mainUrl/film/page/" to "Film Terbaru",
        "$mainUrl/jepangkoreachina/page/" to "Jepang Korea China Barat",
        "$mainUrl/tvshow/page/" to "TV Show"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.movies-list div.ml-item").mapNotNull {
            toSearchResponse(it)
        }
        return newHomePageResponse(request.name, home)
    }
}
