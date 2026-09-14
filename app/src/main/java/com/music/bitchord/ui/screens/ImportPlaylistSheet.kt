package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.importer.PlaylistImporter
import com.music.bitchord.data.importer.PlaylistImporter.ImportPlaylist
import kotlinx.coroutines.launch

/**
 * Sheet for importing playlists from Spotify and JioSaavn.
 *
 * Paste a playlist URL, fetch its tracks, and create a new YouTube Music
 * playlist with the matched tracks.
 */
@Composable
fun ImportPlaylistSheet(
    onImport: (name: String, trackQueries: List<Pair<String, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<ImportPlaylist?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = "Import playlist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Paste a Spotify or JioSaavn playlist URL",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Playlist URL") },
            leadingIcon = { Icon(Icons.Rounded.Link, null) },
            singleLine = true,
            enabled = !loading,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                if (url.isNotBlank() && !loading) {
                    scope.launch {
                        loading = true
                        error = null
                        playlist = null
                        val parsed = PlaylistImporter.parseUrl(url)
                        if (parsed == null) {
                            error = "Couldn't parse URL — use a Spotify or JioSaavn playlist link"
                            loading = false
                            return@launch
                        }
                        val result = when (parsed.service) {
                            PlaylistImporter.Service.JIOSAAVN ->
                                PlaylistImporter.fetchJioSaavnPlaylist(parsed.playlistId)
                            PlaylistImporter.Service.SPOTIFY -> {
                                // Spotify requires auth for track listing —
                                // show a message for now
                                error = "Spotify import requires a Spotify Client ID. " +
                                    "JioSaavn playlists work directly."
                                loading = false
                                return@launch
                            }
                            else -> {
                                error = "Unsupported service"
                                loading = false
                                return@launch
                            }
                        }
                        result.onSuccess { playlist = it }
                            .onFailure { error = it.message ?: "Failed to fetch playlist" }
                        loading = false
                    }
                }
            }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )

        if (loading) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        playlist?.let { pl ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = pl.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${pl.tracks.size} tracks from ${pl.source.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) {
                itemsIndexed(pl.tracks) { index, track ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val queries = pl.tracks.map { it.title to it.artist }
                    onImport(pl.name, queries)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Create playlist with ${pl.tracks.size} tracks")
            }
        }

        if (playlist == null && !loading && error == null) {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Supported services:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "JioSaavn (full support)\nSpotify (requires Client ID — coming soon)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
