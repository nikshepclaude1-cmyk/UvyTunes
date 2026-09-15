package com.music.bitchord.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import com.music.bitchord.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Share sheet for playlists: shows cover art, QR code, and share/save buttons.
 *
 * Generates a QR code that deep-links to the YouTube Music playlist,
 * and offers save-to-gallery and system share.
 */
@Composable
fun PlaylistShareSheet(
    playlistId: String,
    playlistName: String,
    coverArt: Bitmap?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }
    var shareBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val playlistUrl = remember(playlistId) {
        "https://music.youtube.com/playlist?list=$playlistId"
    }

    // Generate combined share image (cover art + QR code)
    LaunchedEffect(playlistId, coverArt) {
        shareBitmap = withContext(Dispatchers.Default) {
            generateShareImage(coverArt, playlistName, playlistUrl)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = "Share playlist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = playlistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))

        // Cover art + QR side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover art
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val image = coverArt
                when {
                    image != null -> Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Playlist cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> CircularProgressIndicator()
                }
            }

            // QR code
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val qr = remember(playlistUrl) { generateQrCode(playlistUrl) }
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                } else {
                    Text(
                        text = "QR",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // URL preview
        Text(
            text = playlistUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction(
                label = if (saved) "Saved" else "Save",
                icon = Icons.Rounded.Download,
                accent = false,
                enabled = !saved,
                modifier = Modifier.weight(1f),
            ) {
                val image = shareBitmap ?: return@ShareAction
                scope.launch { saved = saveToGallery(context, image, playlistName) }
            }
            ShareAction(
                label = stringResource(R.string.share),
                icon = Icons.Rounded.IosShare,
                accent = true,
                enabled = true,
                modifier = Modifier.weight(1f),
            ) {
                scope.launch {
                    val image = shareBitmap
                    if (image != null) {
                        val uri = cacheForSharing(context, image) ?: return@launch
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("image/png")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                "Share $playlistName",
                            ),
                        )
                    } else {
                        // Fallback: share just the URL
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, playlistUrl),
                                "Share $playlistName",
                            ),
                        )
                    }
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ShareAction(
    label: String,
    icon: ImageVector,
    accent: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        accent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Image generation ─────────────────────────────────────────────────────────

private fun generateShareImage(
    coverArt: Bitmap?,
    playlistName: String,
    url: String,
): Bitmap {
    val w = 1080
    val h = 1080
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Dark background
    canvas.drawColor(0xFF1A1A2E.toInt())

    // Cover art on the left half
    val artSize = 480f
    val artX = 60f
    val artY = (h - artSize) / 2f
    if (coverArt != null) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val scale = artSize / minOf(coverArt.width, coverArt.height).toFloat()
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                artX - (coverArt.width * scale - artSize) / 2f,
                artY - (coverArt.height * scale - artSize) / 2f,
            )
        }
        val shader = android.graphics.BitmapShader(
            coverArt,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        ).apply { setLocalMatrix(matrix) }
        paint.shader = shader
        canvas.drawRoundRect(
            RectF(artX, artY, artX + artSize, artY + artSize),
            24f, 24f, paint,
        )
    }

    // QR code on the right
    val qr = generateQrCode(url)
    if (qr != null) {
        val qrSize = 360f
        val qrX = w - qrSize - 60f
        val qrY = (h - qrSize) / 2f
        val qrPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val qrShader = android.graphics.BitmapShader(
            qr,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        )
        val qrScale = qrSize / qr.width.toFloat()
        val qrMatrix = android.graphics.Matrix().apply {
            setScale(qrScale, qrScale)
            postTranslate(qrX, qrY)
        }
        qrShader.setLocalMatrix(qrMatrix)
        qrPaint.shader = qrShader
        canvas.drawRoundRect(
            RectF(qrX, qrY, qrX + qrSize, qrY + qrSize),
            16f, 16f, qrPaint,
        )
    }

    // Playlist name at the bottom
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val maxW = w - 120f
    val displayName = if (textPaint.measureText(playlistName) > maxW) {
        playlistName.take(maxW.toInt() / 3) + "\u2026"
    } else playlistName
    canvas.drawText(displayName, w / 2f, h - 80f, textPaint)

    // "uvytunes" tagline
    val tagPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("scan to open", w / 2f, h - 30f, tagPaint)

    return bitmap
}

private fun generateQrCode(content: String): Bitmap? = try {
    val writer = QRCodeWriter()
    val bits: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
    val w = bits.width
    val h = bits.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (x in 0 until w) {
        for (y in 0 until h) {
            bmp.setPixel(x, y, if (bits[x, y]) Color.WHITE else Color.TRANSPARENT)
        }
    }
    bmp
} catch (e: Exception) {
    null
}

// ── File operations ──────────────────────────────────────────────────────────

private suspend fun cacheForSharing(context: Context, bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(folder, "playlist-share.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

private suspend fun saveToGallery(
    context: Context,
    bitmap: Bitmap,
    label: String,
): Boolean = withContext(Dispatchers.IO) {
    val name = "uvytunes-playlist-${label.replace(' ', '-').lowercase(Locale.ROOT)}.png"
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/UvyTunes",
                )
            }
        }
        val uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("no row")
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } ?: error("no stream")
        true
    }.getOrDefault(false)
}
