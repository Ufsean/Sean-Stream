package com.juraganfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class JuraganFilmProvider : MainAPI() {
    override var mainUrl = "https://tv24.juragan.film"
    private var serverUrl = "https://juragan.movie"
    private val userAgent =
        "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36"
    private val headers = mapOf("User-Agent" to userAgent)
    override var name = "JuraganFilm"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (page > 1) return null
        val document = app.get(mainUrl, headers = headers).document
        val homePageList = ArrayList<HomePageList>()

        document.select("div.home-widget")?.forEach {
            val title = it.select("h3.homemodule-title").text()
            val movies = it.select("div.gmr-item-modulepost").mapNotNull { movie ->
                movie.toSearchResponse()
            }
            if (movies.isNotEmpty()) {
                homePageList.add(HomePageList(title, movies))
            }
        }

        val latest = document.select("div#gmr-main-load article.item").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Latest", latest))


        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url, headers = headers).document

        return document.select("div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = headers).document

        val title = document.selectFirst("h1.entry-title, h3.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.gmr-movie-data img, img.attachment-post-thumbnail")?.attr("src"))
        val plot = document.selectFirst("div.entry-content-single")?.text()?.trim()
        val tags = document.select("div.gmr-movie-genre a, .gmr-movie-on a[rel=tag]").map { it.text() }
        val year = document.selectFirst("div.gmr-moviedata:contains(Tahun) a")?.text()?.toIntOrNull()
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val actors = document.select("div.gmr-moviedata-item:contains(Bintang Film) a, span[itemprop=actors] a").map { it.text() }
        val recommendations = document.select("div.gmr-related-post-item").mapNotNull {
            it.toSearchResponse()
        }
        val trailer = document.selectFirst("a.gmr-trailer-popup")?.attr("href")

        val episodes = document.select("div.gmr-list-episode li").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href")
            val name = link.text()
            newEpisode(href) {
                this.name = name
            }
        }.reversed()

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                this.year = year
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                this.year = year
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val iframe = document.select("iframe[name=juraganfilm]").attr("src")
        app.get(iframe, referer = "$mainUrl/").document.select("li[onclick^=frame]")
            .forEach { mLink ->
                val iLink = mLink.attr("onclick").substringAfter("frame('").substringBefore("')")
                loadExtractor(iLink, serverUrl, subtitleCallback, callback)
            }

        return true
    }
}
