package io.github.smyrna_pergamum.exercisetracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Activity
import io.github.smyrna_pergamum.exercisetracker.domain.Geo
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import java.io.File
import kotlin.math.cos

/**
 * Builds a 1080 px wide picture: the whole map as shown on screen (or, without one, the bare
 * route) on top and the key numbers below. Map data is credited to OpenStreetMap as its licence
 * requires.
 */
object ShareImage {
    private const val W = 1080
    private const val PANEL = 370
    private const val GREEN = 0xFF2E7D32.toInt()

    fun render(context: Context, a: Activity, route: List<Pt>, map: Bitmap?): Bitmap {
        // Scaled, never cropped, so the route keeps the margins the map view gave it.
        val mapH = if (map != null) (W.toLong() * map.height / map.width).toInt().coerceIn(500, 1400) else 900
        val h = mapH + PANEL
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        if (map != null) {
            c.drawBitmap(map, Rect(0, 0, map.width, map.height), Rect(0, 0, W, mapH), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawRect(0f, 0f, W.toFloat(), mapH.toFloat(), Paint().apply { color = 0xFFF2F4F3.toInt() })
            drawRoute(c, route, RectF(80f, 80f, W - 80f, mapH - 80f))
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202422.toInt() }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B716E.toInt(); textSize = 34f }
        text.textSize = 46f
        text.typeface = Typeface.DEFAULT_BOLD
        c.drawText(context.getString(activityLabel(a.type)), 60f, mapH + 80f, text)
        c.drawText(formatDateTime(a.startedAt) + (a.steps?.let { " · " + formatSteps(it.toLong()) + " " + context.getString(R.string.steps_unit) } ?: ""), 60f, mapH + 128f, muted)

        val stats = listOf(
            km(a.distanceM) to context.getString(R.string.distance),
            formatDuration(a.durationMs) to context.getString(R.string.duration),
            Geo.formatPace(Geo.paceSecPerKm(a.distanceM, a.durationMs)) + " /km" to context.getString(R.string.pace),
        )
        val col = (W - 120f) / 3
        text.textSize = 58f
        stats.forEachIndexed { i, (v, label) ->
            val x = 60f + i * col
            c.drawText(v, x, mapH + 236f, text)
            c.drawText(label, x, mapH + 282f, muted)
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B716E.toInt(); textSize = 26f }
        c.drawText("© OpenStreetMap contributors · " + context.getString(R.string.app_name), 60f, h - 24f, small)
        c.drawRect(0f, mapH.toFloat(), W.toFloat(), mapH + 8f, Paint().apply { color = GREEN })
        return bmp
    }

    /** Equirectangular projection fitted into [box]; good enough for routes of a few kilometres. */
    fun drawRoute(c: Canvas, route: List<Pt>, box: RectF) {
        if (route.size < 2) return
        val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
        val minLon = route.minOf { it.lon }; val maxLon = route.maxOf { it.lon }
        val k = cos(Math.toRadians((minLat + maxLat) / 2))
        val w = ((maxLon - minLon) * k).coerceAtLeast(1e-6)
        val h = (maxLat - minLat).coerceAtLeast(1e-6)
        val s = minOf(box.width() / w, box.height() / h)
        val ox = box.left + (box.width() - w * s).toFloat() / 2
        val oy = box.top + (box.height() - h * s).toFloat() / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN; style = Paint.Style.STROKE; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        route.groupBy { it.s }.values.forEach { seg ->
            val path = Path()
            seg.forEachIndexed { i, p ->
                val x = ox + ((p.lon - minLon) * k * s).toFloat()
                val y = oy + ((maxLat - p.lat) * s).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, paint)
        }
    }

    fun share(context: Context, bmp: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "activity.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }
}
