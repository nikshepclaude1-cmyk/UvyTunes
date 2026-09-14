package com.music.bitchord.data.podcasts

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * iTunes Search API client for podcasts.
 *
 * Searches the iTunes podcast catalogue and returns results for display.
 */
object PodcastApi {
    private const val TAG = "PodcastApi"
    private const val BASE_URL = "https://itunes.apple.com/search"
    private const val LOOKUP_URL = "https://itunes.apple.com/lookup"
    private const val ENTITY_PODCAST = "podcast"
    private const val SEARCH_LIMIT = 20

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Search iTunes for podcasts matching a query.
     */
    suspend fun search(term: String): List<PodcastResult> {
        val url = Uri.parse(BASE_URL).buildUpon()
            .appendQueryParameter("term", term)
            .appendQueryParameter("entity", ENTITY_PODCAST)
            .appendQueryParameter("limit", SEARCH_LIMIT.toString())
            .build()
            .toString()

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val searchResponse = json.decodeFromString<PodcastSearchResponse>(body)
            searchResponse.results.map { it.toPodcast() }
        } catch (e: Exception) {
            Log.e(TAG, "Podcast search failed for '$term': ${e.message}")
            emptyList()
        }
    }

    /**
     * Look up a specific podcast by its iTunes ID.
     */
    suspend fun lookup(itunesId: String): PodcastResult? {
        val url = Uri.parse(LOOKUP_URL).buildUpon()
            .appendQueryParameter("id", itunesId)
            .build()
            .toString()

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val lookupResponse = json.decodeFromString<PodcastSearchResponse>(body)
            lookupResponse.results.firstOrNull()?.toPodcast()
        } catch (e: Exception) {
            Log.e(TAG, "Podcast lookup failed for $itunesId: ${e.message}")
            null
        }
    }

    /**
     * Get top podcasts (trending).
     * Uses the iTunes top-podcasts RSS feed parsed as JSON, or falls back
     * to a popular search.
     */
    suspend fun topPodcasts(): List<PodcastResult> {
        // iTunes top podcasts via the RSS feed is XML; use a search fallback
        // with popular terms to get a reasonable list.
        return search("podcast")
    }

    /**
     * Fetch episodes for a podcast from its RSS feed.
     */
    suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode> {
        return RssParser.fetchEpisodes(feedUrl)
    }
}

@Serializable
private data class PodcastSearchResponse(
    val resultCount: Int = 0,
    val results: List<RawPodcastResult> = emptyList(),
)

@Serializable
private data class RawPodcastResult(
    val collectionId: Long = 0,
    val collectionName: String = "",
    val artistName: String = "",
    val artworkUrl600: String? = null,
    val feedUrl: String? = null,
    val trackViewUrl: String? = null,
    val primaryGenreName: String? = null,
    val trackCount: Int = 0,
) {
    fun toPodcast() = PodcastResult(
        itunesId = collectionId.toString(),
        title = collectionName,
        author = artistName,
        artworkUrl = artworkUrl600,
        feedUrl = feedUrl,
        webUrl = trackViewUrl,
        genre = primaryGenreName,
        episodeCount = trackCount,
    )
}

data class PodcastResult(
    val itunesId: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    val webUrl: String?,
    val genre: String?,
    val episodeCount: Int,
) {
    fun artworkAt(px: Int): String? = artworkUrl?.let { url ->
        url.replace("600x600", "${px}x${px}")
            .replace("100x100", "${px}x${px}")
    }
}
