package com.anichin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class AnichinProvider : MainAPI() {
    // URL utama provider
    override var mainUrl = "https://anichin.cafe"
    
    // Nama provider yang akan ditampilkan di aplikasi
    override var name = "Anichin"
    
    // Tipe konten yang didukung
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    // Bahasa default
    override var lang = "id"
    
    // Apakah provider memiliki halaman utama
    override val hasMainPage = true
    
    // Fungsi untuk melakukan pencarian
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        
        return doc.select("div.bsx").mapNotNull { element ->
            val title = element.selectFirst("div.tt")?.text()?.trim() ?: return@mapNotNull null
            var href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("div.limit img")?.attr("src")

            // Transform URL if it's an episode link to ensure it points to the series page
            if (!href.contains("/seri/")) {
                val seriesSlug = href.substringAfter("$mainUrl/").substringBefore("-episode")
                href = "$mainUrl/seri/$seriesSlug/"
            }
            
            // Tentukan tipe konten berdasarkan judul atau metadata lainnya
            val type = when {
                title.contains("movie", ignoreCase = true) -> TvType.AnimeMovie
                title.contains("ova", ignoreCase = true) -> TvType.OVA
                else -> TvType.Anime
            }
            
            newAnimeSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    // Fungsi untuk memuat detail anime dari halaman seri
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // Ambil judul
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        
        // Ambil poster
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        
        // Ambil deskripsi/sinopsis dari div.entry-content
        //val description = document.selectFirst("div.bixbox.synp div.entry-content")?.text()?.trim()
        val description = document.selectFirst("div.entry-content[itemprop=description]")?.text()?.trim()

        // Ambil status dari teks yang mengandung "Status:"
        val statusText = document.select("div.spe").find { it.text().contains("Status:", ignoreCase = true) }
            ?.text()?.substringAfter("Status:")?.trim()
        
        // Tentukan status
        val showStatus = when (statusText?.lowercase()) {
            "ongoing" -> ShowStatus.Ongoing
            "completed" -> ShowStatus.Completed
            else -> null
        }
        
        // Ambil genre
        val genres = document.select("div.genxed a, div.mgen a").map { it.text().trim() }
        
        // Ambil daftar episode dari eplister
        val episodes = document.select("div.eplister ul li").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val episodeUrl = link.attr("href")
            val episodeTitle = link.selectFirst("div.epl-title")?.text()?.trim() ?: ""
            val episodeNum = link.selectFirst("div.epl-num")?.text()?.toIntOrNull()
            
            newEpisode(episodeUrl) {
                this.name = episodeTitle.ifBlank { "Episode ${episodeNum ?: ""}".trim() }
                episodeNum?.let { this.episode = it }
            }
        }.reversed() // Urutkan dari episode terbaru
        
        // Tentukan tipe berdasarkan judul
        val type = when {
            title.contains("movie", ignoreCase = true) -> TvType.AnimeMovie
            title.contains("ova", ignoreCase = true) -> TvType.OVA
            else -> TvType.Anime
        }
        
        // Buat response
        return newAnimeLoadResponse(title, url, type) {
            this.posterUrl = poster
            this.plot = description
            
            // Set genre/tags
            if (genres.isNotEmpty()) {
                this.tags = genres.toMutableList()
            }
            
            // Tambahkan episode jika ada
            if (episodes.isNotEmpty()) {
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }
    
    // Fungsi untuk memuat link streaming
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // Cari semua link streaming
        doc.select("div.player-embed iframe").forEach { iframe ->
            val url = iframe.attr("src")
            if (url.isNotBlank()) {
                loadExtractor(url, data, subtitleCallback, callback)
            }
        }
        
        return true
    }
    
    // Fungsi untuk memuat halaman utama
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        
        val homePageList = ArrayList<HomePageList>()
        
        // Parse slider items (Featured)
        val sliderItems = document.select("#slidertwo .swiper-slide.item").mapNotNull { item ->
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
            homePageList.add(
                HomePageList("Featured", sliderItems, isHorizontalImages = true)
            )
        }
        
        // Parse Popular Today
        val popularItems = document.select("div.listupd.normal .bsx").take(10).mapNotNull { element ->
            val title = element.selectFirst(".tt")?.text()?.trim() ?: return@mapNotNull null
            var href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img")?.attr("src")

            // Transform URL if it's an episode link
            if (!href.contains("/seri/")) {
                val seriesSlug = href.substringAfter("$mainUrl/").substringBefore("-episode")
                href = "$mainUrl/seri/$seriesSlug/"
            }
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
        
        if (popularItems.isNotEmpty()) {
            homePageList.add(
                HomePageList("Popular Today", popularItems)
            )
        }
        
        // Parse Latest Releases
        val latestItems = document.select("div.latesthome + .listupd.normal .bsx").take(10).mapNotNull { element ->
            val title = element.selectFirst(".tt")?.text()?.trim() ?: return@mapNotNull null
            var href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img")?.attr("src")

            // Transform URL if it's an episode link
            if (!href.contains("/seri/")) {
                val seriesSlug = href.substringAfter("$mainUrl/").substringBefore("-episode")
                href = "$mainUrl/seri/$seriesSlug/"
            }
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
        
        if (latestItems.isNotEmpty()) {
            homePageList.add(
                HomePageList("Latest Releases", latestItems)
            )
        }
        
        return newHomePageResponse(homePageList)
    }
}
