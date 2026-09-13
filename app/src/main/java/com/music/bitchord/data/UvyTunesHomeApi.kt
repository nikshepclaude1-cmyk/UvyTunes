package com.music.bitchord.data

import android.util.Log
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

object UvyTunesHomeApi {

    private const val TAG = "UvyTunesHomeApi"
    private const val BASE_URL = "https://uvytunesapi.vercel.app/api/search/playlists"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class PlaylistImage(
        val quality: String = "",
        val url: String = "",
    )

    @Serializable
    data class PlaylistResult(
        val id: String = "",
        val name: String = "",
        val type: String = "",
        val image: List<PlaylistImage> = emptyList(),
        val url: String = "",
        @SerialName("songCount") val songCount: Int = 0,
    )

    @Serializable
    data class PlaylistData(
        val total: Int = 0,
        val results: List<PlaylistResult> = emptyList(),
    )

    @Serializable
    data class PlaylistResponse(
        val success: Boolean = false,
        val data: PlaylistData = PlaylistData(),
    )

    @Serializable
    data class ITunesLabelWrapper(val label: String = "")

    @Serializable
    data class ITunesIdAttributes(
        @SerialName("im:id") val imId: String = "",
    )

    @Serializable
    data class ITunesId(
        val attributes: ITunesIdAttributes = ITunesIdAttributes(),
    )

    @Serializable
    data class ITunesEntry(
        @SerialName("im:name") val name: ITunesLabelWrapper = ITunesLabelWrapper(),
        @SerialName("im:artist") val artist: ITunesLabelWrapper = ITunesLabelWrapper(),
        @SerialName("im:image") val image: List<ITunesLabelWrapper> = emptyList(),
        val id: ITunesId = ITunesId(),
    )

    @Serializable
    data class ITunesFeed(
        val entry: List<ITunesEntry> = emptyList(),
    )

    @Serializable
    data class ITunesTopSongsResponse(
        val feed: ITunesFeed = ITunesFeed(),
    )

    private data class PlaylistQuery(
        val query: String,
        val shelfTitle: String,
    )

    private val playlistQueries = listOf(
        PlaylistQuery("hindi+top", "Hindi Top Charts"),
        PlaylistQuery("bollywood+top", "Bollywood Hits"),
        PlaylistQuery("hindi+romantic", "Hindi Romantic"),
        PlaylistQuery("hindi+90s", "90s Bollywood"),
        PlaylistQuery("hindi+party", "Hindi Party"),
        PlaylistQuery("hindi+indie", "Hindi Indie"),
        PlaylistQuery("tamil+top", "Tamil Top Charts"),
        PlaylistQuery("telugu+top", "Telugu Top Charts"),
        PlaylistQuery("punjabi+top", "Punjabi Hits"),
        PlaylistQuery("kannada+top", "Kannada Top Charts"),
        PlaylistQuery("malayalam+top", "Malayalam Top Charts"),
        PlaylistQuery("marathi+top", "Marathi Top Charts"),
        PlaylistQuery("bhojpuri+top", "Bhojpuri Top Charts"),
        PlaylistQuery("indian+classical", "Indian Classical"),
        PlaylistQuery("indian+devotional", "Indian Devotional"),
    )

    suspend fun fetchIndianPlaylists(): List<HomeShelf> = coroutineScope {
        val deferred = async(Dispatchers.IO) { fetchJiosaavnPlaylists() }
        runCatching { deferred.await() }.getOrElse {
            Log.e(TAG, "JioSaavn playlist fetch failed", it)
            emptyList()
        }
    }

    suspend fun fetchITunesTopSongs(): List<HomeShelf> = coroutineScope {
        val deferred = async(Dispatchers.IO) { fetchITunesTopSongsFeed() }
        runCatching { deferred.await() }.getOrElse {
            Log.e(TAG, "iTunes RSS fetch failed", it)
            emptyList()
        }
    }

    private fun fetchJiosaavnPlaylists(): List<HomeShelf> {
        val grouped = mutableMapOf<String, MutableList<ShelfItem>>()

        for (pq in playlistQueries) {
            val items = fetchPlaylistCategory(pq.query)
            grouped.getOrPut(pq.shelfTitle) { mutableListOf() }.addAll(items)
        }

        return grouped.map { (title, items) ->
            HomeShelf(title = title, items = items)
        }
    }

    private fun fetchPlaylistCategory(query: String): List<ShelfItem> {
        return try {
            val url = "$BASE_URL?query=$query&limit=10"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val parsed = json.decodeFromString<PlaylistResponse>(body)
            if (!parsed.success) return emptyList()

            parsed.data.results.map { result ->
                ShelfItem(
                    title = result.name,
                    subtitle = "${result.songCount} songs",
                    thumbnailUrl = result.image.lastOrNull { it.quality == "500x500" }
                        ?.url ?: result.image.lastOrNull()?.url,
                    videoId = null,
                    browseId = "jiosaavn:playlist:${result.id}",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch playlist category '$query': ${e.message}")
            emptyList()
        }
    }

    private fun fetchITunesTopSongsFeed(): List<HomeShelf> {
        return try {
            val url = "https://itunes.apple.com/in/rss/topsongs/limit=25/json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val parsed = json.decodeFromString<ITunesTopSongsResponse>(body)
            val items = parsed.feed.entry.map { entry ->
                val itunesId = entry.id.attributes.imId
                ShelfItem(
                    title = entry.name.label,
                    subtitle = entry.artist.label,
                    thumbnailUrl = entry.image.lastOrNull()?.label,
                    videoId = null,
                    browseId = "itunes:song:$itunesId",
                )
            }

            if (items.isEmpty()) emptyList()
            else listOf(HomeShelf(title = "Top Songs India - iTunes", items = items))
        } catch (e: Exception) {
            Log.e(TAG, "iTunes RSS fetch failed (gracefully skipping): ${e.message}")
            emptyList()
        }
    }
}
