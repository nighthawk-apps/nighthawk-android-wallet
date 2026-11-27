package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import kotlin.math.PI
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()
private const val WAVE_SAMPLES = 44

/**
 * Lightweight `flow`-inspired mirrored sync waveform (upper activity crest, dashed midpoint axis,
 * softer mirrored lower reflection, and active scan cursor). Stops ticking when [isActive] is false.
 */
@Composable
fun FlowSyncWave(
    progress: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    upperColor: Color = MaterialTheme.colorScheme.primary,
    lowerColor: Color = WalletTheme.colors.secondaryTitleText,
) {
    val phaseState: State<Float>? =
        if (isActive) {
            val transition = rememberInfiniteTransition(label = "flowSyncWave")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = TWO_PI,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 2400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "flowPhase",
            )
        } else {
            null
        }

    val clamped = progress.coerceIn(0f, 1f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) }
    val upperFill = remember { Path() }
    val upperStroke = remember { Path() }
    val lowerFill = remember { Path() }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(34.dp),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val midY = h * 0.5f

        drawLine(
            color = lowerColor.copy(alpha = 0.28f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect,
        )

        if (!isActive) {
            if (clamped > 0f) {
                drawLine(
                    color = upperColor.copy(alpha = 0.72f),
                    start = Offset(0f, midY),
                    end = Offset(w * clamped, midY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            return@Canvas
        }

        val phase = phaseState?.value ?: 0f
        val maxAmp = (h * 0.42f).coerceAtLeast(4f)
        upperFill.reset()
        upperFill.moveTo(0f, midY)
        upperStroke.reset()
        lowerFill.reset()
        lowerFill.moveTo(0f, midY)

        val activeWidth = (w * clamped.coerceAtLeast(0.18f)).coerceAtMost(w)
        for (i in 0..WAVE_SAMPLES) {
            val u = i.toFloat() / WAVE_SAMPLES
            val x = w * u
            val envelope =
                when {
                    u <= (clamped.coerceAtLeast(0.18f)) -> 0.35f + 0.65f * sin(PI * u / clamped.coerceAtLeast(0.18f)).toFloat().coerceAtLeast(0f)
                    else -> 0.14f
                }
            val rawUp =
                0.5f +
                    0.30f * sin(u * 10.5f - phase * 2f) +
                    0.20f * sin(u * 21.0f + phase * 3f)
            val rawDown =
                0.42f +
                    0.28f * sin(u * 8.5f + phase * 1.5f + 1.1f) +
                    0.18f * sin(u * 17.0f - phase * 2.5f)
            val dyUp = (rawUp.coerceIn(0.05f, 1f)) * maxAmp * envelope
            val dyDown = (rawDown.coerceIn(0.05f, 1f)) * maxAmp * 0.72f * envelope
            val yUp = midY - dyUp
            val yDown = midY + dyDown

            upperFill.lineTo(x, yUp)
            lowerFill.lineTo(x, yDown)
            if (i == 0) {
                upperStroke.moveTo(x, yUp)
            } else {
                upperStroke.lineTo(x, yUp)
            }
        }
        upperFill.lineTo(w, midY)
        upperFill.close()
        lowerFill.lineTo(w, midY)
        lowerFill.close()

        drawPath(
            path = upperFill,
            brush =
                Brush.verticalGradient(
                    colors = listOf(upperColor.copy(alpha = 0.42f), upperColor.copy(alpha = 0.04f)),
                    startY = midY - maxAmp,
                    endY = midY,
                ),
        )
        drawPath(
            path = lowerFill,
            brush =
                Brush.verticalGradient(
                    colors = listOf(lowerColor.copy(alpha = 0.04f), lowerColor.copy(alpha = 0.24f)),
                    startY = midY,
                    endY = midY + maxAmp,
                ),
        )
        drawPath(
            path = upperStroke,
            color = upperColor.copy(alpha = 0.92f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )

        val tickX = activeWidth.coerceIn(0f, w)
        drawLine(
            color = upperColor,
            start = Offset(tickX, midY - maxAmp * 0.65f),
            end = Offset(tickX, midY + maxAmp * 0.45f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
