package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import okhttp3.Request
import java.net.URLEncoder

/**
 * Fetches Canvas clips via the Jammify proxy — a simpler path than the
 * direct Spotify protobuf endpoint: just a GET with track name and artist,
 * returns a JSON with a `url` field pointing to the clip.
 *
 * Used as a lightweight alternative when the full Spotify auth flow isn't
 * available or as a fallback.
 */
object JammifyCanvas {

    private const val TAG = "JammifyCanvas"
    private const val PROXY_URL = "https://jammify-music.vercel.app/api/proxy/spotify-canvas"

    suspend fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val encodedTrack = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val url = "$PROXY_URL?trackName=$encodedTrack&artistName=$encodedArtist"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "proxy returned ${response.code} for '$title' by '$artist'")
                    return null
                }
                response.body?.string() ?: return null
            }

            // Parse the JSON response for the canvas URL
            val canvasUrl = extractCanvasUrl(body)
            if (canvasUrl == null) {
                Log.d(TAG, "no canvas URL in proxy response for '$title' by '$artist'")
                return null
            }

            Log.d(TAG, "canvas via Jammify for '$title' by '$artist'")
            CanvasArtwork(
                url = canvasUrl,
                title = title,
                artist = artist,
                album = album,
                source = CanvasSource.OTHER,
            )
        } catch (e: Exception) {
            Log.d(TAG, "Jammify proxy request failed: ${e.message}")
            null
        }
    }

    /**
     * Extracts the canvas video URL from the proxy JSON response.
     * Expected format: { "url": "https://..." } or { "canvasUrl": "https://..." }
     */
    private fun extractCanvasUrl(json: String): String? {
        // Simple JSON extraction without pulling in a full JSON parser
        // for a single field — the proxy returns a small object.
        val patterns = listOf(
            Regex(""""url"\s*:\s*"(https?://[^"]+)""""),
            Regex(""""canvasUrl"\s*:\s*"(https?://[^"]+)""""),
            Regex(""""video_url"\s*:\s*"(https?://[^"]+)""""),
        )
        for (pattern in patterns) {
            val match = pattern.find(json)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
