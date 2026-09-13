package com.music.bitchord.data

import android.net.Uri
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * iTunes Search API client for SHORTS mode.
 *
 * Searches the iTunes catalogue for a matching track and returns its
 * 30-second preview URL for playback. Results are cached in memory
 * so repeated plays of the same song do not hit the network.
 */
object ITunesSearchApi {

    private const val TAG = "ITunesSearchApi"
    private const val BASE_URL = "https://itunes.apple.com/search"
    private const val ENTITY_SONG = "song"
    private const val SEARCH_LIMIT = 10

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * In-memory cache of search results keyed by normalized query.
     * Key: lowercase "artist|title" string.
     * Value: the best matching [ITunesResult], or null if no match was found.
     */
    private val cache = ConcurrentHashMap<String, ITunesResult?>()

    /**
     * Search iTunes for a track matching the given metadata.
     *
     * @param title  track title
     * @param artist artist name
     * @param album  album name (optional, used for disambiguation)
     * @return the best matching [ITunesResult], or null if nothing matches
     */
    suspend fun search(title: String, artist: String, album: String? = null): ITunesResult? {
        val query = normalizeQuery(artist, title)
        cache[query]?.let { return it }

        val term = buildSearchTerm(artist, title)
        val url = Uri.parse(BASE_URL).buildUpon()
            .appendQueryParameter("term", term)
            .appendQueryParameter("entity", ENTITY_SONG)
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
            val body = response.body?.string() ?: return null

            val searchResponse = json.decodeFromString<ITunesSearchResponse>(body)
            val result = pickBestMatch(searchResponse.results, title, artist, album)
            cache[query] = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "iTunes search failed for '$term': ${e.message}")
            null
        }
    }

    /**
     * Resolve a preview URL for the given song metadata.
     *
     * @return the preview URL string, or null if no match
     */
    suspend fun resolvePreviewUrl(title: String, artist: String, album: String? = null): String? {
        return search(title, artist, album)?.previewUrl
    }

    /**
     * Build the iTunes search term from artist and title.
     * iTunes treats the entire term as a single search query.
     */
    private fun buildSearchTerm(artist: String, title: String): String {
        return "$artist $title"
    }

    /**
     * Normalize a query string for cache keying.
     */
    private fun normalizeQuery(artist: String, title: String): String {
        return "${artist.lowercase().trim()}|${title.lowercase().trim()}"
    }

    /**
     * Pick the best matching result from the iTunes response.
     *
     * Scoring:
     * - Exact title + artist match: highest score
     * - Title contains match + artist match: high score
     * - Fuzzy match on both: moderate score
     * - No match: skip
     */
    private fun pickBestMatch(
        results: List<ITunesResult>,
        title: String,
        artist: String,
        album: String?,
    ): ITunesResult? {
        if (results.isEmpty()) return null

        val normalizedTitle = title.lowercase().trim()
        val normalizedArtist = artist.lowercase().trim()
        val normalizedAlbum = album?.lowercase()?.trim()

        data class ScoredResult(val result: ITunesResult, val score: Double)

        val scored = results.mapNotNull { result ->
            val resultTitle = result.trackName.lowercase().trim()
            val resultArtist = result.artistName.lowercase().trim()
            val resultAlbum = result.collectionName?.lowercase()?.trim()

            var score = 0.0

            // Title matching
            when {
                resultTitle == normalizedTitle -> score += 100.0
                resultTitle.contains(normalizedTitle) -> score += 80.0
                normalizedTitle.contains(resultTitle) -> score += 70.0
                // Fuzzy: Levenshtein-like check for typos
                resultTitle.levenshtein(normalizedTitle) <= 2 -> score += 50.0
            }

            // Artist matching
            when {
                resultArtist == normalizedArtist -> score += 100.0
                resultArtist.contains(normalizedArtist) -> score += 70.0
                normalizedArtist.contains(resultArtist) -> score += 60.0
                resultArtist.levenshtein(normalizedArtist) <= 2 -> score += 40.0
            }

            // Album bonus (when provided and available)
            if (normalizedAlbum != null && resultAlbum != null) {
                when {
                    resultAlbum == normalizedAlbum -> score += 30.0
                    resultAlbum.contains(normalizedAlbum) -> score += 15.0
                    normalizedAlbum.contains(resultAlbum) -> score += 10.0
                }
            }

            // Must have a preview URL
            if (result.previewUrl.isNullOrBlank()) {
                score = 0.0
            }

            // Minimum threshold: need at least a decent title or artist match
            if (score >= 80.0) ScoredResult(result, score) else null
        }

        return scored.maxByOrNull { it.score }?.result
    }

    /**
     * Simple Levenshtein distance for fuzzy matching.
     */
    private fun String.levenshtein(other: String): Int {
        val len1 = this.length
        val len2 = other.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (this[i - 1] == other[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[len1][len2]
    }

    /** Clear the in-memory cache. */
    fun clearCache() {
        cache.clear()
    }
}

@Serializable
data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesResult> = emptyList(),
)

@Serializable
data class ITunesResult(
    val trackId: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val collectionName: String? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val trackViewUrl: String? = null,
    val trackTimeMillis: Long = 0,
    val releaseDate: String? = null,
    val primaryGenreName: String? = null,
) {
    /** Convert artwork URL to a higher resolution by replacing the size hint. */
    fun artworkAt(px: Int): String? = artworkUrl100?.let { url ->
        url.replace("100x100", "${px}x${px}")
            .replace("100bb", "${px}x${px}bb")
    }
}
