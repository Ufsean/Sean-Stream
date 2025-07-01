package com.anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Base64

class AnichinProvider : MainAPI() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst("div.tt")?.text()?.trim() ?: return null
        var href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("div.limit img")?.attr("src")

        if (!href.contains("/seri/")) {
            val seriesSlug = href.substringAfter("$mainUrl/").substringBefore("-episode")
            href = "$mainUrl/seri/$seriesSlug/"
        }

        val type = when {
            title.contains("movie", ignoreCase = true) -> TvType.AnimeMovie
            title.contains("ova", ignoreCase = true) -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("div.bsx").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val description = document.selectFirst("div.entry-content[itemprop=description]")?.text()?.trim()
        val genres = document.select("div.genxed a, div.mgen a").map { it.text().trim() }

        val episodes = document.select("div.eplister ul li").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val episodeUrl = link.attr("href")
            val episodeTitle = link.selectFirst("div.epl-title")?.text()?.trim() ?: ""
            val episodeNum = link.selectFirst("div.epl-num")?.text()?.toIntOrNull()

            newEpisode(episodeUrl) {
                this.name = episodeTitle.ifBlank { "Episode ${episodeNum ?: ""}".trim() }
                episodeNum?.let { this.episode = it }
            }
        }.reversed()

        val type = when {
            title.contains("movie", ignoreCase = true) -> TvType.AnimeMovie
            title.contains("ova", ignoreCase = true) -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = poster
            this.plot = description
            if (genres.isNotEmpty()) {
                this.tags = genres.toMutableList()
            }
            if (episodes.isNotEmpty()) {
                addEpisodes(DubStatus.Subbed, episodes)
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
        val sources = mutableListOf<String>()

        // Handle the default player
        document.select("div.player-embed iframe").firstOrNull()?.attr("src")?.let {
            sources.add(it)
        }

        // Handle other servers from the dropdown
        document.select("select.mirror option").forEach { option ->
            val value = option.attr("value")
            if (value.isNotBlank()) {
                try {
                    val decodedValue = String(Base64.getDecoder().decode(value))
                    val iframe = Jsoup.parse(decodedValue).selectFirst("iframe")
                    iframe?.attr("src")?.let { src ->
                        if (!sources.contains(src)) {
                            sources.add(src)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore if base64 decoding fails
                }
            }
        }
        
        sources.forEach { url ->
            loadExtractor(url, data, subtitleCallback, callback)
        }

        return true
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (page > 1) {
            val url = "$mainUrl/page/$page/"
            val document = app.get(url).document
            val items = document.select("div.postbody .listupd .bsx").mapNotNull {
                it.toSearchResponse()
            }
            return newHomePageResponse(HomePageList("Latest Release", items))
        }

        // Page 1
        val homePageList = ArrayList<HomePageList>()
        val mainDocument = app.get(mainUrl).document

        // Featured (Slider)
        val sliderItems = mainDocument.select("#slidertwo .swiper-slide.item").mapNotNull { item ->
            val title = item.selectFirst(".info h2 a")?.attr("data-jtitle") ?: return@mapNotNull null
            val href = item.selectFirst(".info a.watch")?.attr("href") ?: return@mapNotNull null
            val posterUrl = item.selectFirst(".backdrop")?.attr("style")
                ?.substringAfter("background-image: url('")
                ?.substringBefore("');")
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
        if (sliderItems.isNotEmpty()) {
            homePageList.add(HomePageList("Featured", sliderItems, isHorizontalImages = true))
        }

        // Popular Today
        val popularItems = mainDocument.select("div.bixbox.hothome .listupd .bsx").mapNotNull {
            it.toSearchResponse()
        }
        if (popularItems.isNotEmpty()) {
            homePageList.add(HomePageList("Popular Today", popularItems))
        }

        // Latest Releases
        val latestItems = mainDocument.select(".releases.latesthome + .listupd.normal .bsx").mapNotNull {
            it.toSearchResponse()
        }
        if (latestItems.isNotEmpty()) {
            homePageList.add(HomePageList("Latest Release", latestItems))
        }

        return newHomePageResponse(homePageList)
    }
}
