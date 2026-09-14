package com.music.bitchord.data.importer

import com.music.bitchord.data.TrackLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imports playlists from Spotify and JioSaavn into the app's YouTube Music library.
 *
 * - JioSaavn: fully supported via public API (no auth needed).
 * - Spotify: uses oEmbed for metadata + requires user to paste track list or
 *   provide a Client ID for full API access. For now we support URL-based
 *   JioSaavn import and a manual "paste Spotify track list" fallback.
 */
object PlaylistImporter {
    private const val TAG = "BitChord"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    // ── URL parsing ──────────────────────────────────────────────────────────

    enum class Service { SPOTIFY, JIOSAAVN, UNKNOWN }

    data class ParsedUrl(val service: Service, val playlistId: String, val displayName: String?)

    fun parseUrl(url: String): ParsedUrl? {
        val lower = url.lowercase().trim()

        // Spotify: open.spotify.com/playlist/ID or spotify:playlist:ID
        val spotifyId = Regex("spotify[:/]+playlist[:/]([a-zA-Z0-9]+)").find(lower)?.groupValues?.get(1)
        if (spotifyId != null) return ParsedUrl(Service.SPOTIFY, spotifyId, null)

        // JioSaavn: jiosaavn.com/playlist/ID/... or jiosaavn.com/s/playlist/ID
        val jioId = Regex("jiosaavn\\.com/(?:s/)?playlist/([a-zA-Z0-9_-]+)").find(lower)?.groupValues?.get(1)
        if (jioId != null) return ParsedUrl(Service.JIOSAAVN, jioId, null)

        return null
    }

    // ── Spotify ──────────────────────────────────────────────────────────────

    @Serializable
    private data class SpotifyOEmbed(val title: String? = null)

    /**
     * Spotify playlist metadata via oEmbed (no auth required).
     * The oEmbed endpoint gives us the playlist name but NOT the tracks.
     * For full track access the user would need to provide a Client ID,
     * which is beyond the scope of this initial implementation.
     */
    suspend fun spotifyPlaylistName(playlistId: String): String? = runCatching {
        val response = client.get("https://open.spotify.com/oembed") {
            header("User-Agent", "Mozilla/5.0")
            parameter("url", "https://open.spotify.com/playlist/$playlistId")
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        val body = json.decodeFromString<SpotifyOEmbed>(response.bodyAsText())
        body.title
    }.getOrElse {
        TrackLog.w(TAG, "Spotify oEmbed failed: ${it.message}")
        null
    }

    // ── JioSaavn ─────────────────────────────────────────────────────────────

    @Serializable
    private data class JioSaavnPlaylistResponse(
        val id: String = "",
        val title: String = "",
        val songs: List<JioSaavnPlaylistTrack> = emptyList(),
    )

    @Serializable
    private data class JioSaavnPlaylistTrack(
        val id: String = "",
        val title: String = "",
        val subtitle: String = "",
        @SerialName("image") val image: String = "",
    )

    /**
     * Fetches a JioSaavn playlist's tracks via their public API.
     */
    suspend fun fetchJioSaavnPlaylist(playlistId: String): Result<ImportPlaylist> = runCatching {
        val response = client.get("https://www.jiosaavn.com/api.php") {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            header("X-Forwarded-For", "49.36.0.1")
            parameter("__call", "playlist.getDetails")
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("pids", playlistId)
        }

        if (response.status != HttpStatusCode.OK) {
            error("HTTP ${response.status.value}")
        }

        val text = response.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject

        val title = root["title"]?.jsonPrimitive?.content ?: "JioSaavn Playlist"
        val songsArray = root["songs"]?.jsonArray ?: emptyList()

        val tracks = songsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val songTitle = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val subtitle = obj["subtitle"]?.jsonPrimitive?.content ?: ""
            ImportTrack(
                title = songTitle,
                artist = subtitle,
                sourceId = id,
                source = Service.JIOSAAVN,
            )
        }

        ImportPlaylist(
            name = title,
            tracks = tracks,
            source = Service.JIOSAAVN,
        )
    }.onFailure {
        TrackLog.w(TAG, "JioSaavn playlist fetch failed: ${it.message}")
    }

    // ── Data classes ─────────────────────────────────────────────────────────

    data class ImportPlaylist(
        val name: String,
        val tracks: List<ImportTrack>,
        val source: Service,
    )

    data class ImportTrack(
        val title: String,
        val artist: String,
        val sourceId: String,
        val source: Service,
    )
}
