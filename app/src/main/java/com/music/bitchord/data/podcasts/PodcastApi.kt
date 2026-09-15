package com.music.bitchord.data.podcasts

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * iTunes Search API client for podcasts.
 *
 * Searches the iTunes podcast catalogue and returns results for display.
 */
object PodcastApi {
    private const val TAG = "PodcastApi"
    private const val BASE_URL = "https://itunes.apple.com/search"
    private const val LOOKUP_URL = "https://itunes.apple.com/lookup"
    private const val TOP_PODCASTS_URL = "https://rss.applemarketingtools.com/api/v2/us/podcasts/top/25/podcasts.json"
    private const val SEARCH_LIMIT = 50

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
            .appendQueryParameter("media", "podcast")
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
            Log.d(TAG, "Search response length: ${body.length}")
            val searchResponse = json.decodeFromString<PodcastSearchResponse>(body)
            Log.d(TAG, "Search results: ${searchResponse.resultCount}")
            searchResponse.results.map { it.toPodcast() }
        } catch (e: Exception) {
            Log.e(TAG, "Podcast search failed for '$term': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Look up a specific podcast by its iTunes ID.
     */
    suspend fun lookup(itunesId: String): PodcastResult? {
        val url = Uri.parse(LOOKUP_URL).buildUpon()
            .appendQueryParameter("id", itunesId)
            .appendQueryParameter("entity", "podcastEpisode")
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
     * Get top podcasts from Apple's top podcasts RSS feed.
     * Falls back to genre-specific searches if the feed fails.
     */
    suspend fun topPodcasts(): List<PodcastResult> = withContext(Dispatchers.IO) {
        // Try Apple's official top podcasts JSON feed first
        val feedResult = runCatching {
            val request = Request.Builder()
                .url(TOP_PODCASTS_URL)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@runCatching emptyList()
            val feedResponse = json.decodeFromString<TopPodcastsFeed>(body)
            feedResponse.feed.results.map { it.toPodcast() }
        }.getOrNull()

        if (!feedResult.isNullOrEmpty()) {
            Log.d(TAG, "Top podcasts from feed: ${feedResult.size}")
            return@withContext feedResult
        }

        // Fallback: search popular podcast genres
        Log.d(TAG, "Feed failed, falling back to genre searches")
        val genres = listOf("news", "comedy", "true crime", "technology", "history", "business")
        val allResults = mutableListOf<PodcastResult>()
        for (genre in genres) {
            val results = search(genre)
            allResults.addAll(results)
            if (allResults.size >= 25) break
        }
        allResults.distinctBy { it.itunesId }.take(25)
    }

    /**
     * Fetch episodes for a podcast from its RSS feed.
     */
    suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode> {
        return RssParser.fetchEpisodes(feedUrl)
    }

    /**
     * Fetch the top podcasts RSS XML and parse it for podcast IDs + artwork.
     * This is used as a backup to get actual top/trending podcasts.
     */
    suspend fun topPodcastsFromRss(): List<PodcastResult> = withContext(Dispatchers.IO) {
        try {
            val rssUrl = "https://itunes.apple.com/us/rss/toppodcasts/limit=25/genre=1310/json"
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val feedResponse = json.decodeFromString<RssTopFeed>(body)
            feedResponse.feed.results.map { it.toPodcast() }
        } catch (e: Exception) {
            Log.e(TAG, "RSS top podcasts failed: ${e.message}")
            emptyList()
        }
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

@Serializable
private data class TopPodcastsFeed(
    val feed: TopPodcastsFeedInner = TopPodcastsFeedInner(),
)

@Serializable
private data class TopPodcastsFeedInner(
    val results: List<TopPodcastResult> = emptyList(),
)

@Serializable
private data class TopPodcastResult(
    val id: String = "",
    val name: String = "",
    val artistName: String = "",
    val artworkUrl100: String? = null,
    val genres: List<TopPodcastGenre> = emptyList(),
    @kotlinx.serialization.SerialName("url")
    val webUrl: String? = null,
) {
    fun toPodcast(): PodcastResult {
        val art = artworkUrl100?.replace("100x100bb", "600x600bb")
        return PodcastResult(
            itunesId = id,
            title = name,
            author = artistName,
            artworkUrl = art,
            feedUrl = null, // Will need lookup for feedUrl
            webUrl = webUrl,
            genre = genres.firstOrNull()?.name,
            episodeCount = 0,
        )
    }
}

@Serializable
private data class TopPodcastGenre(
    val id: String = "",
    val name: String = "",
)

@Serializable
private data class RssTopFeed(
    val feed: RssTopFeedInner = RssTopFeedInner(),
)

@Serializable
private data class RssTopFeedInner(
    val entry: List<RssTopEntry> = emptyList(),
)

@Serializable
private data class RssTopEntry(
    @kotlinx.serialization.SerialName("im:name")
    val name: RssLabel? = null,
    @kotlinx.serialization.SerialName("im:artist")
    val artist: RssLabel? = null,
    @kotlinx.serialization.SerialName("im:image")
    val images: List<RssImage> = emptyList(),
    @kotlinx.serialization.SerialName("id")
    val id: RssId? = null,
    val link: RssLink? = null,
    val category: RssCategory? = null,
) {
    fun toPodcast(): PodcastResult {
        val art = images.lastOrNull()?.label?.replace("100x100bb", "600x600bb")
        return PodcastResult(
            itunesId = id?.attributes?.get("im:id") ?: "",
            title = name?.label ?: "",
            author = artist?.label ?: "",
            artworkUrl = art,
            feedUrl = null,
            webUrl = link?.attributes?.get("href"),
            genre = category?.attributes?.get("label"),
            episodeCount = 0,
        )
    }
}

@Serializable
private data class RssLabel(
    val label: String = "",
)

@Serializable
private data class RssImage(
    val label: String = "",
)

@Serializable
private data class RssId(
    val attributes: Map<String, String>? = null,
)

@Serializable
private data class RssLink(
    val attributes: Map<String, String>? = null,
)

@Serializable
private data class RssCategory(
    val attributes: Map<String, String>? = null,
)

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
