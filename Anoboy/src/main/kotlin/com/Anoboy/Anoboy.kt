package com.anoboy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Anoboy : MainAPI() {

    override var mainUrl = "https://ww3.anoboy.app"
    override var name = "Anoboy"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Baru Update",
        "$mainUrl/category/anime-movie/page/" to "Movie",
        "$mainUrl/category/live-action-movie/page/" to "Live Action",
        "$mainUrl/category/anime/page/" to "Semua Anime",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page, headers = mapOf("User-Agent" to "Mozilla/5.0", "Referer" to mainUrl)).document
        val selector = when {
            request.name.contains("Movie", ignoreCase = true) -> "div.column-content > a"
            request.name.contains("Live Action", ignoreCase = true) -> "div.column-content > a"
            request.name.contains("Anime", ignoreCase = true) -> "div.column-content > a"
            else -> "div.home_index > a"
        }
        val home = document.select(selector).mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.amvj > h3.ibox1")?.text()?.trim() ?: return null
        var href = fixUrl(this.attr("href"))
        // Manipulate href to remove episode suffix to get detail page URL
        href = href.replace(Regex("-episode-\\d+"), "")
        return newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = fixUrlNull(this@toSearchResult.selectFirst("amp-img")?.attr("src"))
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.home_index > a").mapNotNull {
            val title = it.selectFirst("div.amvj > h3.ibox1")?.text()?.trim() ?: ""
            val href = fixUrl(it.attr("href"))
            val posterUrl = fixUrlNull(it.selectFirst("amp-img")?.attr("src"))
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = mainUrl).document
        val title = document.selectFirst("div.pagetitle > h1")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: return null
        
        val poster = document.selectFirst("amp-img[alt=\"Download FileAnime\"]")?.attr("src")
        val description = document.select("div.unduhan").first()?.ownText()?.trim()
        val year = document.selectFirst("time.updated")?.text()?.split(" ")?.getOrNull(2)?.replace(",", "")?.toIntOrNull()

        val tags = mutableListOf<String>()
        document.select("div.unduhan table tr").forEach { row ->
            val header = row.selectFirst("th")?.text()?.trim()?.lowercase()
            val value = row.selectFirst("td")?.text()?.trim()
            if (header == "genre" && value != null) {
                tags.addAll(value.split(",").map { it.trim() })
            }
        }

        val episodes = mutableListOf<Episode>()
        val seasonElements = document.select("div.hq")

        if (seasonElements.isNotEmpty()) {
            // Multi-season anime
            seasonElements.forEach { seasonElement ->
                val seasonName = seasonElement.text()
                val seasonNum = seasonName.filter { it.isDigit() }.toIntOrNull()

                // Find the next sibling which is the episode list
                var episodeListElement = seasonElement.nextElementSibling()
                while (episodeListElement != null && !episodeListElement.hasClass("singlelink")) {
                    episodeListElement = episodeListElement.nextElementSibling()
                }

                episodeListElement?.select("ul.lcp_catlist li a")?.filter { it.text().contains("Episode ", true) }?.forEach { el ->
                    val href = fixUrl(el.attr("href"))
                    val episodeTitle = el.text().trim()
                    // Extracts the first number after "Episode "
                    val episodeNum = episodeTitle.substringAfterLast("Episode ").split(Regex("\\s+"))[0].toIntOrNull()
                    episodes.add(
                        newEpisode(href) {
                            this.name = episodeTitle
                            this.episode = episodeNum
                            this.season = seasonNum
                        }
                    )
                }
            }
        } else {
            // Single-season anime
            document.select("div.singlelink ul.lcp_catlist li a").filter { it.text().contains("Episode ", true) }.forEach { el ->
                val href = fixUrl(el.attr("href"))
                val episodeTitle = el.text().trim()
                val episodeNum = episodeTitle.substringAfterLast("Episode ").split(Regex("\\s+"))[0].toIntOrNull()
                episodes.add(newEpisode(href) {
                    this.name = episodeTitle
                    this.episode = episodeNum
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val sources = mutableListOf<Pair<String, String>>()

        // Add player iframe and mirrors
        document.select("iframe#mediaplayer").attr("src").let {
            if (it.isNotBlank()) sources.add(it to "Player Utama")
        }
        document.select("div#fplay a#allmiror").forEach {
            val videoUrl = it.attr("data-video")
            if (videoUrl.isNotBlank()) {
                val quality = it.text()
                val providerName = it.parent()?.ownText()?.substringBefore("|")?.trim() ?: "Mirror"
                sources.add(videoUrl to "$providerName $quality")
            }
        }

        // Add download links
        document.select("div.download span.ud").forEach { udSpan ->
            val providerName = udSpan.selectFirst("span.udj")?.text()?.trim() ?: "Download"
            udSpan.select("a.udl").forEach { link ->
                val href = link.attr("href")
                // Ensure the link is not a placeholder and is visible
                if (href.isNotBlank() && "none" !in href && !link.attr("style").contains("display:none")) {
                    val quality = link.text().trim()
                    sources.add(href to "$providerName $quality")
                }
            }
        }

        // Load all found sources concurrently
        sources.apmap { (sourceUrl, name) ->
            loadExtractor(fixUrl(sourceUrl), data, subtitleCallback) { link ->
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = link.source,
                        name = name,
                        url = link.url,
                        referer = link.referer,
                        quality = name.fixQuality(),
                        isM3u8 = link.isM3u8,
                        headers = link.headers,
                        extractorData = link.extractorData
                    )
                )
            }
        }

        return true
    }

    private fun String.fixQuality(): Int {
        return Regex("(\\d{3,4})").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}
