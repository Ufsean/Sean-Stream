package com.animefreak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimeFreakProvider : MainAPI() {
    override var mainUrl = "https://animefreak.biz"
    override var name = "AnimeFreak"
    override val supportedTypes = setOf(TvType.Anime)
    override var lang = "en"
    override val hasMainPage = true

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst(".name a")?.text() ?: this.selectFirst(".name")?.text() ?: return null
        
        // Handle lazy-loaded images by checking for common attributes.
        val posterUrl = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { null } ?:
            it.attr("data-lazy-src").ifBlank { null } ?:
            it.attr("data-original").ifBlank { null } ?:
            it.attr("src").ifBlank { null }
        }
        
        // The href is already the correct page to load, no transformation needed.
        return newAnimeSearchResponse(title, "$mainUrl$href") {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf("Referer" to "$mainUrl/")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl, referer = "$mainUrl/").document
        val homePageList = ArrayList<HomePageList>()

        val newOnAnimeFreak = document.select("section:has(h2:contains(New on AnimeFreak)) div.unit").mapNotNull {
            it.toSearchResponse()
        }
        if (newOnAnimeFreak.isNotEmpty()) {
            homePageList.add(HomePageList("New on AnimeFreak", newOnAnimeFreak))
        }

        val mostViewed = document.select("section:has(h2:contains(Most viewed)) a.unit").mapNotNull {
            it.toSearchResponse()
        }
        if (mostViewed.isNotEmpty()) {
            homePageList.add(HomePageList("Most Viewed", mostViewed))
        }

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Implement search functionality later
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = "$mainUrl/").document

        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.description div[data-name=full] div")?.text()?.trim() 
            ?: document.selectFirst("div.description div")?.text()?.trim()
        
        val statusText = document.select("div.meta > div:has(div:contains(Status:)) span")
            ?.text()?.trim()
        val showStatus = when {
            statusText.equals("Currently Airing", ignoreCase = true) -> ShowStatus.Ongoing
            statusText.equals("Finished Airing", ignoreCase = true) -> ShowStatus.Completed
            else -> null
        }

        val genres = document.select("div.meta > div:has(div:contains(Genre:)) span a").map { it.text() }

        val episodes = document.select("#episodes .below a").mapNotNull {
            val epUrl = it.attr("href")
            val epNum = it.attr("data-num").toIntOrNull()
            newEpisode("$mainUrl$epUrl") {
                name = "Episode $epNum"
                episode = epNum
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = genres
            this.posterHeaders = mapOf("Referer" to "$mainUrl/")
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = data).document

        // Iterate through servers sequentially to avoid race conditions and crashes from apmap
        for (server in document.select(".sv-box .server")) {
            try {
                val url = server.attr("data-url")
                if (url.isNotBlank()) {
                    val fullUrl = if (url.startsWith("http")) url else "$mainUrl$url"
                    
                    val success = if (fullUrl.contains("/player/streaming.php")) {
                        val playerDoc = app.get(fullUrl, referer = data).document
                        val iframeSrc = playerDoc.select("iframe").firstOrNull()?.attr("src")
                        if (iframeSrc != null) {
                            loadExtractor(iframeSrc, data, subtitleCallback, callback)
                        } else {
                            false
                        }
                    } else {
                        loadExtractor(fullUrl, data, subtitleCallback, callback)
                    }
                    // If links are found, stop searching and return true
                    if (success) return true
                }
            } catch (e: Exception) {
                // Ignore exceptions and try the next server
            }
        }
        
        return false
    }
}
