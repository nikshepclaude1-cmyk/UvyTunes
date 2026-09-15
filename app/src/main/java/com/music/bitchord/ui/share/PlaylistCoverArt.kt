package com.music.bitchord.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.Matrix
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates a mosaic cover art for a playlist from up to 4 track thumbnails.
 *
 * Layout: 2x2 grid of album art tiles, or a single tile if fewer than 4 tracks,
 * or a gradient fallback with the playlist initial if no tracks.
 */
object PlaylistCoverArt {

    private const val TILE_SIZE = 540
    private const val COVER_PX = 540
    private const val GAP = 6f

    /**
     * Generate a mosaic bitmap from track thumbnails.
     * [tracks] should be the first few tracks in the playlist.
     * Returns a 1080x1080 bitmap.
     */
    suspend fun generate(
        context: Context,
        tracks: List<Song>,
        playlistName: String,
    ): Bitmap = withContext(Dispatchers.Default) {
        val thumbnails = tracks
            .mapNotNull { it.thumbnailUrl }
            .distinct()
            .take(4)

        if (thumbnails.isEmpty()) {
            return@withContext generateFallback(playlistName)
        }

        val bitmaps = thumbnails.mapNotNull { url ->
            loadBitmap(context, url)
        }.take(4)

        if (bitmaps.isEmpty()) {
            return@withContext generateFallback(playlistName)
        }

        val size = TILE_SIZE * 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (bitmaps.size) {
            1 -> {
                // Single tile centered
                val tile = bitmaps[0]
                drawTile(canvas, tile, 0f, 0f, size.toFloat(), size.toFloat())
            }
            2 -> {
                // Two tiles side by side
                val half = (size - GAP) / 2f
                drawTile(canvas, bitmaps[0], 0f, 0f, half, size.toFloat())
                drawTile(canvas, bitmaps[1], half + GAP, 0f, half, size.toFloat())
            }
            3 -> {
                // Two on top, one on bottom
                val half = (size - GAP) / 2f
                val full = size.toFloat()
                drawTile(canvas, bitmaps[0], 0f, 0f, half, half)
                drawTile(canvas, bitmaps[1], half + GAP, 0f, half, half)
                drawTile(canvas, bitmaps[2], 0f, half + GAP, full, half)
            }
            else -> {
                // 2x2 grid
                val half = (size - GAP) / 2f
                drawTile(canvas, bitmaps[0], 0f, 0f, half, half)
                drawTile(canvas, bitmaps[1], half + GAP, 0f, half, half)
                drawTile(canvas, bitmaps[2], 0f, half + GAP, half, half)
                drawTile(canvas, bitmaps[3], half + GAP, half + GAP, half, half)
            }
        }

        bitmap
    }

    private fun drawTile(canvas: Canvas, source: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scale = maxOf(w / source.width.toFloat(), h / source.height.toFloat())
        val dx = x + (w - source.width * scale) / 2f
        val dy = y + (h - source.height * scale) / 2f

        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        paint.shader = shader

        val radius = 16f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
    }

    private fun generateFallback(name: String): Bitmap {
        val size = TILE_SIZE * 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient background
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFF1A1A2E.toInt())

        // Initial letter
        val letter = name.firstOrNull()?.uppercase() ?: "?"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 280f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            alpha = 200
        }
        canvas.drawText(letter, size / 2f, size / 2f + 90f, textPaint)

        return bitmap
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url.artworkAt(COVER_PX))
            .allowHardware(false)
            .build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
            ?.image?.toBitmap()
    }.getOrNull()
}
