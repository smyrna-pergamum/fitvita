package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

private data class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Int, val w: Float)

/** A short burst of falling confetti in the goal colours; calls [onDone] when it has fallen. */
@Composable
fun Confetti(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val colors = listOf(Accent.Steps, Accent.Water, Accent.Move, Accent.Mind, Accent.Xp, Accent.Route)
    val pieces = remember {
        List(90) { Piece(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.7f + Random.nextFloat() * 0.6f, Random.nextFloat() * 0.2f - 0.1f,
            Random.nextFloat() * 720f, Random.nextInt(colors.size), 6f + Random.nextFloat() * 8f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(2600, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier) {
        pieces.forEach { p ->
            val k = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (k <= 0f) return@forEach
            val y = -40f + k * p.speed * (size.height + 80f)
            val x = (p.x + p.drift * k) * size.width
            rotate(p.spin * k, Offset(x, y)) {
                drawRect(colors[p.color], Offset(x - p.w / 2, y - p.w), Size(p.w, p.w * 1.8f), alpha = 1f - k * 0.3f)
            }
        }
    }
}
