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
        PlaylistQuery("english+top", "English Charts"),
        PlaylistQuery("english+pop", "English Charts"),
        PlaylistQuery("english+rock", "English Charts"),
        PlaylistQuery("hindi+top", "Trending in India"),
        PlaylistQuery("bollywood+top", "Trending in India"),
        PlaylistQuery("tamil+top", "Trending in India"),
        PlaylistQuery("telugu+top", "Trending in India"),
        PlaylistQuery("english+hip+hop", "English Vibes"),
        PlaylistQuery("english+R%26B", "English Vibes"),
        PlaylistQuery("english+chill", "English Vibes"),
    )

    suspend fun fetchHomeShelves(): Result<List<HomeShelf>> = coroutineScope {
        val jiosaavnDeferred = async(Dispatchers.IO) { fetchJiosaavnPlaylists() }
        val itunesDeferred = async(Dispatchers.IO) { fetchITunesTopSongs() }

        val jiosaavnResult = runCatching { jiosaavnDeferred.await() }
        val itunesResult = runCatching { itunesDeferred.await() }

        val jiosaavnShelves = jiosaavnResult.getOrElse {
            Log.e(TAG, "JioSaavn playlist fetch failed", it)
            emptyList()
        }
        val itunesShelves = itunesResult.getOrElse {
            Log.e(TAG, "iTunes RSS fetch failed", it)
            emptyList()
        }

        val allShelves = jiosaavnShelves + itunesShelves
        if (allShelves.isEmpty()) {
            val error = jiosaavnResult.exceptionOrNull() ?: itunesResult.exceptionOrNull()
            Result.failure(error ?: Exception("All home data sources failed"))
        } else {
            Result.success(allShelves)
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

    private fun fetchITunesTopSongs(): List<HomeShelf> {
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
            else listOf(HomeShelf(title = "Top Songs India", items = items))
        } catch (e: Exception) {
            Log.e(TAG, "iTunes RSS fetch failed (gracefully skipping): ${e.message}")
            emptyList()
        }
    }
}
