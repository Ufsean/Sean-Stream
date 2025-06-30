package com.juraganfilm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jsoup.nodes.Element
import java.net.URI

class JuraganFilmProvider : MainAPI() {
    override var mainUrl = "https://tv24.juragan.film"
    private var serverUrl = "https://juragan.info"
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36"
    private val headers = mapOf("User-Agent" to userAgent)
    override var name = "JuraganFilm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
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
        val document = app.get(mainUrl, headers = headers).document
        val homePageList = ArrayList<HomePageList>()

        val carousels = document.select("div.gmr-owl-carousel .gmr-slider-content").mapNotNull {
            val title = it.selectFirst(".gmr-slide-title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            newMovieSearchResponse(title, href) {
                this.posterUrl = posterUrl
            }
        }
        homePageList.add(HomePageList("Featured", carousels))

        val newMovies = document.select("div#muvipro-posts-10 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("New Movies", newMovies))

        val boxOffice = document.select("div#muvipro-posts-2 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Film Box Office", boxOffice))

        val ongoing = document.select("div#muvipro-posts-8 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Film Seri Ongoing", ongoing))

        val china = document.select("div#muvipro-posts-5 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Film Seri China", china))

        val dramabox = document.select("div#muvipro-posts-9 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Film Serial Dramabox", dramabox))

        val drakor = document.select("div#muvipro-posts-7 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Film Seri Drakor", drakor))

        val marvel = document.select("div#muvipro-posts-6 div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Koleksi Film Marvel", marvel))

        val latest = document.select("div#gmr-main-load div.gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
        homePageList.add(HomePageList("Latest Movie", latest))

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
        val poster = fixUrlNull(document.selectFirst("div.gmr-movie-data img")?.attr("src"))
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
        val genres = document.select("div.gmr-movie-genre a").map { it.text() }
        val recommendations = document.select("div.gmr-related-post-item").mapNotNull {
            it.toSearchResponse()
        }
        
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
                this.tags = genres
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun invokeGetbk(
        name: String,
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
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

    }

    private suspend fun invokeGdrive(
        name: String,
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {

        val embedUrl = app.get(
            url,
            referer = "$serverUrl/"
        ).document.selectFirst("iframe")?.attr("src")?.let { fixUrl(it) } ?: return

        val req = app.get(embedUrl)
        val host = getBaseUrl(embedUrl)
        val token = req.document.selectFirst("div#token")?.text() ?: return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                "$host/hlsplaylist.php?idhls=${token.trim()}.m3u8",
            ) {
                this.referer = "$host/"
                this.quality = Qualities.Unknown.value
                this.type = ExtractorLinkType.M3U8
            }
        )

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val iframe = document.select("iframe[name=juraganfilm]").attr("src")
        app.get(iframe, referer = "$mainUrl/").document.select("div#header-slider ul li")
            .forEach { mLink ->
                val iLink = mLink.attr("onclick").substringAfter("frame('").substringBefore("')")
                serverUrl = getBaseUrl(iLink)
                val iMovie = iLink.substringAfter("movie=").substringBefore("&")
                val mIframe = iLink.substringAfter("iframe=")
                val serverName = fixTitle(mIframe)
                when (mIframe) {
                    "getbk" -> {
                        invokeGetbk(
                            serverName,
                            "$serverUrl/stream/$mIframe.php?movie=$iMovie",
                            callback
                        )
                    }
                    "gdrivehls", "gdriveplayer" -> {
                        invokeGdrive(serverName, iLink, callback)
                    }
                    else -> {}
                }
            }

        return true

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private data class Sources(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )
}
