package com.kuramanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Kuramanime : MainAPI() {
    override var mainUrl = "https://v8.kuramanime.run"
    override var name = "Kuramanime"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA, TvType.TvSeries)

    companion object {
        fun getType(t: String, s: Int): TvType {
            return when {
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                t.contains("Movie", true) && s == 1 -> TvType.AnimeMovie
                t.contains("Drama", true) -> TvType.TvSeries
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when (t.lowercase()) {
                "selesai", "completed" -> ShowStatus.Completed
                "ongoing", "sedang tayang" -> ShowStatus.Ongoing
                "not yet aired", "coming soon" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/quick/ongoing?order_by=updated" to "Sedang Tayang",
        "$mainUrl/quick/finished?order_by=updated" to "Selesai Tayang",
        "$mainUrl/quick/upcoming?order_by=popular" to "Segera Tayang",
        "$mainUrl/quick/movie?order_by=updated" to "Film Layar Lebar",
        "$mainUrl/properties/genre" to "Daftar Genre",
        "$mainUrl/quick/donghua?order_by=text" to "Donghua"
    )

    // Helper function to extract anime ID from URL
    private fun extractAnimeId(url: String): String {
        return url.trim('/').substringAfterLast('/')
    }

    // Helper function to extract episode number from title
    private fun extractEpisodeNumber(title: String): Int? {
        return Regex("Episode\\s*(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}&page=$page" else request.data
        val document = app.get(url).document
        
        val isSearchPage = request.data.contains("s=")
        val selector = if (isSearchPage) "div.anime-card" else "div.anime-card"
        
        val home = document.select(selector).mapNotNull { it.toSearchResult() }
        
        // Handle pagination
        val hasNextPage = document.select("a[rel=next]").isNotEmpty()
        
        return newHomePageResponse(
            name = request.name,
            home,
            hasNext = hasNextPage
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = this.selectFirst("h5.anime-card__title")?.text()?.trim() ?: return null
        
        // Extract poster image
        val posterUrl = this.selectFirst("img.anime-card__poster")?.attr("src")?.let { fixUrl(it) }
        
        // Extract episode info if available
        val episodeInfo = this.selectFirst("div.anime-card__episode")?.text()
        val episode = episodeInfo?.let { 
            Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }
        
        // Determine type based on URL or other indicators
        val type = when {
            href.contains("/movie/") -> TvType.AnimeMovie
            else -> TvType.Anime
        }
        
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addSub(episode)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val link = "$mainUrl/search?s=$encodedQuery"
        val document = app.get(link).document

        return document.select("div.anime-card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Extract basic information
        val title = document.selectFirst("h1.anime-detail__title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("div.anime-detail__poster img")?.attr("src")?.let { fixUrl(it) }
        
        // Extract metadata
        val metadata = document.select("div.anime-detail__meta div.meta-item").associate {
            val label = it.select("span.meta-label").text().lowercase().trim()
            val value = it.select("span.meta-value").text().trim()
            label to value
        }
        
        // Extract genres
        val tags = document.select("div.anime-detail__genres a.genre-tag")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        
        // Extract year from season info if available
        val year = metadata["tahun"]?.toIntOrNull() ?: 
            Regex("(\\d{4})").find(metadata["musim"] ?: "")?.groupValues?.get(1)?.toIntOrNull()
        
        // Extract status
        val status = getStatus(metadata["status"] ?: "")
        
        // Extract description
        val description = document.select("div.anime-detail__description").text().trim()
        
        // Extract episodes
        val episodes = mutableListOf<Episode>()
        val episodeElements = document.select("div.episode-list a.episode-item")
        
        episodeElements.forEach { element ->
            val episodeUrl = fixUrl(element.attr("href"))
            val episodeTitle = element.select("div.episode-title").text().trim()
            val episodeNumber = extractEpisodeNumber(episodeTitle) ?: episodes.size + 1
            val thumbnail = element.select("div.episode-thumb img").attr("src").let { fixUrl(it) }
            
            episodes.add(
                newEpisode(episodeUrl) {
                    this.name = episodeTitle
                    this.episode = episodeNumber
                    this.posterUrl = thumbnail
                }
            )
        }
        
        // Determine anime type
        val type = getType(metadata["tipe"] ?: "TV", episodes.size)
        
        // Extract recommendations
        val recommendations = document.select("div.recommendation-item").mapNotNull { rec ->
            val recUrl = fixUrl(rec.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val recTitle = rec.selectFirst("h5")?.text()?.trim() ?: return@mapNotNull null
            val recPoster = rec.selectFirst("img")?.attr("src")?.let { fixUrl(it) }
            
            newAnimeSearchResponse(recTitle, recUrl, TvType.Anime) {
                this.posterUrl = recPoster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
        
        // Try to get tracker info
        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)
        
        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover ?: poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations.distinctBy { it.url }
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
            
            // Add additional metadata
            metadata["skor"]?.toFloatOrNull()?.let { rating ->
                // Convert 10-point scale to 5-point scale and round to nearest integer
                this.rating = (rating / 2f).toInt()
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
        
        // Extract available video servers
        val serverElements = document.select("div.server-list button.server-item")
        if (serverElements.isEmpty()) {
            // Fallback to embedded video player
            document.select("div.video-player video source").forEach { source ->
                val videoUrl = fixUrl(source.attr("src"))
                if (videoUrl.isNotBlank()) {
                    val quality = when (source.attr("label").lowercase()) {
                        "hd" -> Qualities.P720.value
                        "full hd" -> Qualities.P1080.value
                        "sd" -> Qualities.P480.value
                        else -> Qualities.P360.value // Default to 360p if unknown
                    }
                    
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Direct",
                            url = videoUrl,
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                            this.quality = quality
                            this.headers = mapOf("Referer" to mainUrl)
                        }
                    )
                }
            }
            return true
        }
        
        // Process each server
        serverElements.forEach { server ->
            try {
                val serverName = server.text().trim()
                val serverId = server.attr("data-id") ?: return@forEach
                
                // Get video URL from API
                val videoData = app.post(
                    "$mainUrl/ajax/video",
                    data = mapOf(
                        "id" to serverId,
                        "episode" to data.substringAfterLast("episode/").substringBefore("-")
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<Map<String, String>>()
                
                val videoUrl = videoData["url"] ?: return@forEach
                
                // Handle different server types
                when {
                    videoUrl.contains("youtube") -> {
                        // Handle YouTube links
                        loadExtractor(videoUrl, data, subtitleCallback, callback)
                    }
                    videoUrl.contains("dailymotion") -> {
                        // Handle Dailymotion links
                        loadExtractor(videoUrl, data, subtitleCallback, callback)
                    }
                    videoUrl.contains("kuramadrive") -> {
                        // Handle KuramaDrive
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "KuramaDrive",
                                url = videoUrl,
                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P720.value
                                this.headers = mapOf("Referer" to mainUrl)
                            }
                        )
                    }
                    else -> {
                        // Try to extract using available extractors
                        loadExtractor(videoUrl, data, subtitleCallback, callback)
                    }
                }
                
            } catch (e: Exception) {
                // Log error and continue with next server
                e.printStackTrace()
            }
        }
        
        // Extract subtitles if available
        document.select("track").forEach { track ->
            val kind = track.attr("kind")
            if (kind.equals("subtitles", ignoreCase = true) || kind.equals("captions", ignoreCase = true)) {
                val lang = track.attr("srclang").ifBlank { "id" }
                val label = track.attr("label").ifBlank { lang }
                val subUrl = track.attr("src").takeIf { it.isNotBlank() }?.let { fixUrl(it) } ?: return@forEach
                
                subtitleCallback.invoke(
                    SubtitleFile(
                        label,
                        subUrl
                    )
                )
            }
        }
        
        return true
    }
}
