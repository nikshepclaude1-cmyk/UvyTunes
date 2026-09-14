package com.music.bitchord.data.podcasts

import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses podcast RSS feeds and extracts episode data.
 *
 * Uses XmlPullParser for efficient streaming parsing of RSS feeds.
 */
object RssParser {
    private const val TAG = "RssParser"

    /**
     * Fetch and parse an RSS feed URL, returning a list of episodes.
     */
    suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            parseRss(body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch/parse RSS feed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse RSS XML content and extract episodes.
     */
    fun parseRss(xml: String): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inItem = false
        var title = ""
        var description = ""
        var audioUrl = ""
        var audioType = ""
        var pubDate = ""
        var duration = ""
        var imageUrl = ""
        var episodeNumber = 0
        var seasonNumber = 0
        var link = ""
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        // Reset fields for new item
                        title = ""
                        description = ""
                        audioUrl = ""
                        audioType = ""
                        pubDate = ""
                        duration = ""
                        imageUrl = ""
                        episodeNumber = 0
                        seasonNumber = 0
                        link = ""
                    }
                    // Handle enclosure tag (has url and type attributes)
                    if (currentTag == "enclosure" && inItem) {
                        audioUrl = parser.getAttributeValue(null, "url") ?: ""
                        audioType = parser.getAttributeValue(null, "type") ?: ""
                    }
                    // Handle itunes:image tag
                    if (currentTag == "image" && inItem) {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) imageUrl = href
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title = text
                            "description", "summary" -> if (description.isEmpty()) description = text
                            "pubDate" -> pubDate = text
                            "link" -> link = text
                            "itunes:duration", "duration" -> if (duration.isEmpty()) duration = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        // Only add episodes with audio URLs
                        if (audioUrl.isNotBlank()) {
                            episodes.add(
                                PodcastEpisode(
                                    title = title.ifBlank { "Untitled Episode" },
                                    description = description.take(500),
                                    audioUrl = audioUrl,
                                    audioType = audioType,
                                    pubDate = pubDate,
                                    pubDateMillis = parseDate(pubDate),
                                    duration = duration,
                                    imageUrl = imageUrl,
                                    episodeNumber = episodeNumber,
                                    seasonNumber = seasonNumber,
                                    link = link,
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return episodes
    }

    /**
     * Parse an RFC-2822 date string to milliseconds.
     */
    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val formats = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            )
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    return sdf.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) {
                    // Try next format
                }
            }
            0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateStr")
            0L
        }
    }
}

data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val audioType: String = "",
    val pubDate: String = "",
    val pubDateMillis: Long = 0L,
    val duration: String = "",
    val imageUrl: String = "",
    val episodeNumber: Int = 0,
    val seasonNumber: Int = 0,
    val link: String = "",
) {
    val displayDuration: String
        get() = duration.ifBlank {
            // Try to estimate from description length
            ""
        }

    val isExplicit: Boolean
        get() = false // RSS doesn't always have this
}
