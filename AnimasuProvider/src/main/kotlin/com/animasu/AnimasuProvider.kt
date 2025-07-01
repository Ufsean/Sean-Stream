package com.animasu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Base64

class AnimasuProvider : MainAPI() {
    override var mainUrl = "https://v1.animasu.top"
    override var name = "Animasu"
    override val supportedTypes = setOf(TvType.Anime)
    override var lang = "id"
    override val hasMainPage = true

    private fun Element.toSearchResponse(): SearchResponse {
        val title = this.select("div.tt").text()
        val href = this.select("a").attr("href")
        val posterUrl = this.select("img").attr("src")
        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/anime-sedang-tayang-terbaru/?halaman=" to "Sedang Tayang",
        "$mainUrl/?urutan=baru&page=" to "Baru Ditambah",
        "$mainUrl/genre/donghua/page/" to "Donghua",
        "$mainUrl/anime-movie/?halaman=" to "Movie",
        "$mainUrl/pencarian/?urutan=baru&halaman=" to "Semua Anime",
        "$mainUrl/genre/aksi/page/" to "Aksi",
        "$mainUrl/genre/anak-anak/page/" to "Anak-anak",
        "$mainUrl/genre/luar-angkasa/page/" to "Luar Angkasa",
        "$mainUrl/genre/super-power/page/" to "Superpower",
        "$mainUrl/genre/dimensi/page/" to "Dimensi",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/ecchi/page/" to "Ecchi",
        "$mainUrl/genre/fantasi/page/" to "Fantasi",
        "$mainUrl/genre/fantasi-urban/page/" to "Fantasi Urban",
        "$mainUrl/genre/game/page/" to "Game",
        "$mainUrl/genre/gourment/page/" to "Gourment",
        "$mainUrl/genre/harem/page/" to "Harem",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/iblis/page/" to "Iblis",
        "$mainUrl/genre/isekai/page/" to "Isekai",
        "$mainUrl/genre/josei/page/" to "Josei",
        "$mainUrl/genre/suspense/page/" to "Suspense",
        "$mainUrl/genre/komedi/page/" to "Komedi",
        "$mainUrl/genre/live-action/page/" to "Live Action",
        "$mainUrl/genre/makanan/page/" to "Makanan",
        "$mainUrl/genre/martial-arts/page/" to "Martial Arts",
        "$mainUrl/genre/medical/page/" to "Medical",
        "$mainUrl/genre/militer/page/" to "Militer",
        "$mainUrl/genre/misteri/page/" to "Misteri",
        "$mainUrl/genre/mobil/page/" to "Mobil",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        val home = document.select("div.bs").map {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        return document.select("div.bs").map {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, h1[itemprop=headline]")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.thumb img, div.bigcover img")?.attr("src")
        val plot = document.selectFirst("div.sinopsis p, div[itemprop=articleBody] p")?.text()?.trim()

        val genres = document.select("div.spe span:has(b:contains(Genre)) a").map { it.text() }
        
        val episodes = document.select("ul#daftarepisode li").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val href = link.attr("href")
            val name = link.text()
            newEpisode(href) {
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

        document.select("select.mirror option").forEach {
            val encodedIframe = it.attr("value")
            if (encodedIframe.isNotBlank()) {
                try {
                    val decodedIframe = String(Base64.getDecoder().decode(encodedIframe))
                    val iframeSrc = Jsoup.parse(decodedIframe).select("iframe").attr("src")
                    if (iframeSrc.isNotBlank()) {
                        linksLoaded = loadExtractor(iframeSrc, data, subtitleCallback, callback) || linksLoaded
                    }
                } catch (e: Exception) {
                    // Ignore errors from decoding or parsing
                }
            }
        }
        return linksLoaded
    }
}
