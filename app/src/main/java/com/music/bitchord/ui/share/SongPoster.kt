package com.music.bitchord.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a share poster for a single song.
 *
 * Layout (1080 × 1920, 9:16):
 *  - Blurred album-art backdrop (palette-derived radial gradients)
 *  - Centered 640 × 640 album-art card with rounded corners
 *  - Song title
 *  - Artist name
 *  - "listen ad-free on uvytunes!" + logo
 */
suspend fun renderSongPoster(
    context: Context,
    song: Song,
): Bitmap = withContext(Dispatchers.Default) {
    val cover = song.thumbnailUrl?.let { loadBitmap(context, it) }

    val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val type = Fonts(context)

    drawBackdrop(canvas, cover)
    drawCover(canvas, cover, song.title)
    drawText(canvas, context, type, song)
    drawFooter(canvas, context, type)
    bitmap
}

private fun drawBackdrop(canvas: Canvas, cover: Bitmap?) {
    val colors = paletteOf(cover)
    canvas.drawColor(dimmed(colors.first()))

    val anchors = listOf(
        0.20f to 0.16f,
        0.84f to 0.22f,
        0.76f to 0.66f,
        0.18f to 0.78f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    colors.forEachIndexed { index, color ->
        val (fx, fy) = anchors[index]
        val cx = POSTER_W * fx
        val cy = POSTER_H * fy
        val radius = POSTER_W * 0.95f
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 210),
                ColorUtils.setAlphaComponent(color, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    paint.shader = null

    // Dark scrim
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, POSTER_H.toFloat(),
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, POSTER_W.toFloat(), POSTER_H.toFloat(), paint)
    paint.shader = null
}

private fun drawCover(canvas: Canvas, cover: Bitmap?, title: String) {
    val size = 640f
    val x = (POSTER_W - size) / 2f
    val y = 540f
    val bounds = RectF(x, y, x + size, y + size)
    val radius = size * 0.12f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (cover != null) {
        val scale = size / minOf(cover.width, cover.height).toFloat()
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                x - (cover.width * scale - size) / 2f,
                y - (cover.height * scale - size) / 2f,
            )
        }
        paint.shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    } else {
        // Coloured fallback when artwork is missing
        val hue = (title.hashCode().toFloat() % 360f + 360f) % 360f
        paint.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

private fun drawText(canvas: Canvas, context: Context, type: Fonts, song: Song) {
    val centerX = POSTER_W / 2f
    val textTop = 1260f
    val maxW = POSTER_W - MARGIN * 2

    // Song title — centred, large
    val titlePaint = type.heading(64f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    val titleText = ellipsised(song.title, titlePaint, maxW)
    canvas.drawText(titleText, centerX, textTop + 60f, titlePaint)

    // Artist — centred, lighter
    val artistPaint = type.body(44f, 0xB3FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    val artistText = ellipsised(song.artist, artistPaint, maxW)
    canvas.drawText(artistText, centerX, textTop + 140f, artistPaint)
}

private fun drawFooter(canvas: Canvas, context: Context, type: Fonts) {
    val centerX = POSTER_W / 2f

    // "listen ad-free on uvytunes!"
    val tagline = type.body(30f, 0x99FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText("listen ad-free on uvytunes!", centerX, POSTER_H - 220f, tagline)

    // Logo + "UvyTunes"
    val logo = runCatching {
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null)
    }.getOrNull()
    val brandText = "UvyTunes"
    val brandPaint = type.heading(38f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    val brandW = brandPaint.measureText(brandText)
    val totalW = LOGO_W + LOGO_GAP + brandW
    val logoX = centerX - totalW / 2f

    if (logo != null) {
        logo.setTint(0xE6FFFFFF.toInt())
        val logoY = POSTER_H - 170f
        logo.setBounds(
            logoX.toInt(),
            logoY.toInt(),
            (logoX + LOGO_W).toInt(),
            (logoY + LOGO_H).toInt(),
        )
        logo.draw(canvas)
    }
    canvas.drawText(
        brandText,
        logoX + LOGO_W + LOGO_GAP + brandW / 2f,
        POSTER_H - 130f,
        brandPaint,
    )
}

// ── Palette helpers ──────────────────────────────────────────────────────────

private fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        Palette.from(source).maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
    }.getOrNull().orEmpty()
    if (swatches.isEmpty()) return fallback
    return (swatches + fallback).take(4).map(::tuned)
}

private fun tuned(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return ColorUtils.HSLToColor(hsl)
}

private fun dimmed(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = 0.10f
    return ColorUtils.HSLToColor(hsl)
}

// ── Text helpers ─────────────────────────────────────────────────────────────

private fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

// ── Fonts ────────────────────────────────────────────────────────────────────

internal class Fonts(context: Context) {
    private val heavy = font(context, R.font.sf_pro_display_heavy) ?: Typeface.DEFAULT_BOLD
    private val semibold = font(context, R.font.sf_pro_display_semibold) ?: Typeface.DEFAULT_BOLD
    private val regular = font(context, R.font.sf_pro_display_regular) ?: Typeface.DEFAULT

    fun heading(size: Float, color: Int) = paint(heavy, size, color)
    fun body(size: Float, color: Int, bold: Boolean = false) =
        paint(if (bold) semibold else regular, size, color)

    private fun paint(face: Typeface, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

    private fun font(context: Context, id: Int): Typeface? =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
}

internal suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url.artworkAt(POSTER_COVER_PX))
        .allowHardware(false)
        .build()
    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}.getOrNull()

internal const val POSTER_W = 1080
internal const val POSTER_H = 1920
private const val POSTER_COVER_PX = 1080

private const val MARGIN = 72f
internal const val LOGO_W = 66f
internal const val LOGO_H = 44f
internal const val LOGO_GAP = 20f
