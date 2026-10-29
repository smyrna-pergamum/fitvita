package io.github.smyrna_pergamum.exercisetracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/** Lets a screen grab the current map picture for sharing. */
class MapHandle {
    internal var view: MapView? = null

    fun snapshot(): Bitmap? {
        val v = view ?: return null
        if (v.width == 0 || v.height == 0) return null
        return runCatching {
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }.getOrNull()
    }
}

private const val ROUTE = 0xFF2E7D32.toInt()

/** Draws the current position as a green dot with a white ring. */
private class HereOverlay : Overlay() {
    var here: GeoPoint? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROUTE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val p = here ?: return
        val px = map.projection.toPixels(p, null)
        val d = map.resources.displayMetrics.density
        c.drawCircle(px.x.toFloat(), px.y.toFloat(), 10 * d, ring)
        c.drawCircle(px.x.toFloat(), px.y.toFloat(), 7 * d, fill)
    }
}

/**
 * OpenStreetMap with the route. Tiles are fetched only while this view is on screen. With [follow]
 * the map keeps the current position centred; otherwise it fits the whole route once.
 */
@Composable
fun RouteMap(points: List<Pt>, here: Pt?, follow: Boolean, modifier: Modifier = Modifier, handle: MapHandle? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { object { var fitted = false; var lastCount = -1 } }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(16.0)
            overlays.add(CopyrightOverlay(context))
            overlays.add(HereOverlay())
        }
    }
    DisposableEffect(lifecycle) {
        handle?.view = map
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
            handle?.view = null
        }
    }
    AndroidView({ map }, modifier) { m ->
        if (state.lastCount != points.size) {
            state.lastCount = points.size
            m.overlays.removeAll { it is Polyline }
            points.groupBy { it.s }.values.forEach { seg ->
                if (seg.size < 2) return@forEach
                m.overlays.add(0, Polyline(m).apply {
                    setPoints(seg.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = ROUTE
                    outlinePaint.strokeWidth = 7 * m.resources.displayMetrics.density
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                })
            }
        }
        (m.overlays.firstOrNull { it is HereOverlay } as? HereOverlay)?.here = here?.let { GeoPoint(it.lat, it.lon) }
        if (follow) {
            here?.let { m.controller.animateTo(GeoPoint(it.lat, it.lon)) }
        } else if (!state.fitted && points.size >= 2) {
            state.fitted = true
            val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) })
            m.addOnFirstLayoutListener { _, _, _, _, _ -> m.zoomToBoundingBox(box.increaseByScale(1.3f), false) }
            if (m.width > 0) m.zoomToBoundingBox(box.increaseByScale(1.3f), false)
        }
        m.invalidate()
    }
}
