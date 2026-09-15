package com.music.bitchord.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a shareable lyrics poster for a single song.
 *
 * Layout (1080 x 1920, 9:16):
 *  - Album art backdrop with palette-derived gradients
 *  - Glassmorphism card with selected lyrics text
 *  - Song title + artist
 *  - "uvytunes" branding
 */
suspend fun renderLyricsPoster(
    context: Context,
    song: Song,
    lyrics: List<LyricLine>,
    currentLineIndex: Int,
): Bitmap = withContext(Dispatchers.Default) {
    val cover = song.thumbnailUrl?.let { loadBitmap(context, it) }
    val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val type = Fonts(context)

    drawBackdrop(canvas, cover)
    drawLyricsCard(canvas, type, lyrics, currentLineIndex)
    drawSongInfo(canvas, type, song)
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
        paint.shader = LinearGradient(
            cx - radius, cy - radius, cx + radius, cy + radius,
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
    paint.shader = LinearGradient(
        0f, 0f, 0f, POSTER_H.toFloat(),
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, POSTER_W.toFloat(), POSTER_H.toFloat(), paint)
    paint.shader = null
}

private fun drawLyricsCard(
    canvas: Canvas,
    type: Fonts,
    lyrics: List<LyricLine>,
    currentIndex: Int,
) {
    val cardX = 72f
    val cardY = 480f
    val cardW = POSTER_W - cardX * 2
    val cardH = 780f
    val cardRadius = 32f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Glass card background
    paint.color = 0x33FFFFFF
    canvas.drawRoundRect(RectF(cardX, cardY, cardX + cardW, cardY + cardH), cardRadius, cardRadius, paint)

    // Card border
    paint.color = 0x22FFFFFF
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRoundRect(RectF(cardX, cardY, cardX + cardW, cardY + cardH), cardRadius, cardRadius, paint)
    paint.style = Paint.Style.FILL

    // Lyrics text — pick up to 5 lines around the current line
    val startIdx = (currentIndex - 2).coerceAtLeast(0)
    val endIdx = (currentIndex + 3).coerceAtMost(lyrics.size)
    val visibleLines = lyrics.subList(startIdx, endIdx)

    val textPaint = type.body(38f, 0xE6FFFFFF.toInt())
    val activePaint = type.body(42f, Color.WHITE).apply {
        isFakeBoldText = true
    }

    var textY = cardY + 64f
    val textX = cardX + 40f
    val maxTextW = cardW - 80f

    for ((i, line) in visibleLines.withIndex()) {
        val globalIdx = startIdx + i
        val paint = if (globalIdx == currentIndex) activePaint else textPaint
        val alpha = if (globalIdx == currentIndex) 1.0f else 0.5f

        paint.alpha = (alpha * 255).toInt()

        val lines = breakText(line.text, paint, maxTextW)
        for (lineText in lines) {
            canvas.drawText(lineText, textX, textY, paint)
            textY += 50f
        }
        textY += 12f
    }
}

private fun drawSongInfo(canvas: Canvas, type: Fonts, song: Song) {
    val centerX = POSTER_W / 2f
    val infoTop = 1340f
    val maxW = POSTER_W - 144f

    // Song title
    val titlePaint = type.heading(56f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    val titleText = ellipsised(song.title, titlePaint, maxW)
    canvas.drawText(titleText, centerX, infoTop + 60f, titlePaint)

    // Artist
    val artistPaint = type.body(40f, 0xB3FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    val artistText = ellipsised(song.artist, artistPaint, maxW)
    canvas.drawText(artistText, centerX, infoTop + 130f, artistPaint)
}

private fun drawFooter(canvas: Canvas, context: Context, type: Fonts) {
    val centerX = POSTER_W / 2f

    // "uvytunes" tagline
    val tagline = type.body(28f, 0x99FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText("uvytunes", centerX, POSTER_H - 220f, tagline)

    // Logo + "UvyTunes"
    val logo = runCatching {
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null)
    }.getOrNull()
    val brandText = "UvyTunes"
    val brandPaint = type.heading(36f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
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

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun breakText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isEmpty()) return listOf("")
    if (paint.measureText(text) <= maxWidth) return listOf(text)

    val words = text.split(" ")
    val result = mutableListOf<String>()
    var current = StringBuilder()

    for (word in words) {
        val test = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(test) > maxWidth && current.isNotEmpty()) {
            result.add(current.toString())
            current = StringBuilder(word)
        } else {
            if (current.isNotEmpty()) current.append(" ")
            current.append(word)
        }
    }
    if (current.isNotEmpty()) result.add(current.toString())
    return result.ifEmpty { listOf(text) }
}

private fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "\u2026") > width) end--
    return text.take(end).trimEnd() + "\u2026"
}

private fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        android.graphics.Palette.from(source).maximumColorCount(24).generate().swatches
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

private class Fonts(context: Context) {
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
