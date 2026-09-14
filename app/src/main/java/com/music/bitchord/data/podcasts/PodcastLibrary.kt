package com.music.bitchord.data.podcasts

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists saved podcasts (by iTunes ID) so the user can return to them.
 * Uses the same plain SharedPreferences file as other app settings — no
 * secrets are stored here.
 */
object PodcastLibrary {
    private const val TAG = "PodcastLibrary"
    private const val PREFS_NAME = "bitchord_settings"
    private const val KEY_SAVED_PODCASTS = "saved_podcasts"

    private lateinit var prefs: SharedPreferences

    private val _savedPodcasts = MutableStateFlow<List<SavedPodcast>>(emptyList())
    val savedPodcasts: StateFlow<List<SavedPodcast>> = _savedPodcasts.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun isSaved(itunesId: String): Boolean =
        _savedPodcasts.value.any { it.itunesId == itunesId }

    fun save(podcast: PodcastResult) {
        if (isSaved(podcast.itunesId)) return
        val entry = SavedPodcast(
            itunesId = podcast.itunesId,
            title = podcast.title,
            author = podcast.author,
            artworkUrl = podcast.artworkUrl,
            feedUrl = podcast.feedUrl,
            genre = podcast.genre,
        )
        _savedPodcasts.value = _savedPodcasts.value + entry
        persistToPrefs()
    }

    fun remove(itunesId: String) {
        _savedPodcasts.value = _savedPodcasts.value.filter { it.itunesId != itunesId }
        persistToPrefs()
    }

    fun toggle(podcast: PodcastResult) {
        if (isSaved(podcast.itunesId)) remove(podcast.itunesId) else save(podcast)
    }

    private fun loadFromPrefs() {
        try {
            val raw = prefs.getString(KEY_SAVED_PODCASTS, null) ?: return
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedPodcast>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedPodcast(
                        itunesId = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.getString("author"),
                        artworkUrl = obj.optString("artwork", null),
                        feedUrl = obj.optString("feed", null),
                        genre = obj.optString("genre", null),
                    )
                )
            }
            _savedPodcasts.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load saved podcasts: ${e.message}")
        }
    }

    private fun persistToPrefs() {
        try {
            val arr = JSONArray()
            for (p in _savedPodcasts.value) {
                arr.put(
                    JSONObject().apply {
                        put("id", p.itunesId)
                        put("title", p.title)
                        put("author", p.author)
                        put("artwork", p.artworkUrl ?: "")
                        put("feed", p.feedUrl ?: "")
                        put("genre", p.genre ?: "")
                    }
                )
            }
            prefs.edit().putString(KEY_SAVED_PODCASTS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save podcasts: ${e.message}")
        }
    }
}

data class SavedPodcast(
    val itunesId: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    val genre: String?,
) {
    fun toPodcastResult() = PodcastResult(
        itunesId = itunesId,
        title = title,
        author = author,
        artworkUrl = artworkUrl,
        feedUrl = feedUrl,
        webUrl = null,
        genre = genre,
        episodeCount = 0,
    )
}
