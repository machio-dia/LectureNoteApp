package com.example.lecturenote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Material Icons に無い「消しゴム」「投げ縄」「レーザーポインター」などを
 * 一目で分かる形で描くための自前アイコン。
 */

@Composable
fun EraserGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        rotate(-42f, pivot = Offset(w / 2f, h * 0.46f)) {
            val bw = w * 0.82f
            val bh = w * 0.42f
            val left = (w - bw) / 2f
            val top = h * 0.46f - bh / 2f
            val radius = CornerRadius(bh * 0.2f)
            drawRoundRect(tint, Offset(left, top), Size(bw * 0.44f, bh), radius)
            drawRoundRect(tint, Offset(left, top), Size(bw, bh), radius, style = Stroke(sw))
        }
        drawLine(
            tint,
            Offset(w * 0.34f, h * 0.93f),
            Offset(w * 0.94f, h * 0.93f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LassoGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        drawOval(
            tint,
            topLeft = Offset(w * 0.07f, h * 0.10f),
            size = Size(w * 0.86f, h * 0.58f),
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.15f, w * 0.12f))
            )
        )
        val tail = Path().apply {
            moveTo(w * 0.36f, h * 0.66f)
            quadraticTo(w * 0.18f, h * 0.82f, w * 0.42f, h * 0.95f)
        }
        drawPath(tail, tint, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun LaserGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, this.size.height / 2f)
        val sw = w * 0.085f
        drawCircle(tint, radius = w * 0.15f, center = c)
        drawCircle(tint.copy(alpha = 0.55f), radius = w * 0.29f, center = c, style = Stroke(sw))
        for (i in 0 until 4) {
            rotate(i * 90f + 45f, pivot = c) {
                drawLine(
                    tint,
                    Offset(c.x, c.y - w * 0.40f),
                    Offset(c.x, c.y - w * 0.50f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** 図形ツール：四角と円の重なり */
@Composable
fun ShapesGlyph(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        drawRoundRect(
            tint,
            Offset(w * 0.08f, h * 0.10f),
            Size(w * 0.52f, h * 0.52f),
            CornerRadius(w * 0.06f),
            style = Stroke(sw)
        )
        drawCircle(tint, radius = w * 0.25f, center = Offset(w * 0.66f, h * 0.66f), style = Stroke(sw))
    }
}

/** 実線 / 点線の見本 */
@Composable
fun LineStyleGlyph(dashed: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 28.dp, height = 14.dp)) {
        val y = size.height / 2f
        drawLine(
            tint,
            Offset(2f, y),
            Offset(size.width - 2f, y),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
            } else {
                null
            }
        )
    }
}

/** XY軸の見本（原点の位置つき） */
@Composable
fun AxesGlyph(fx: Float, fy: Float, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = (w * 0.07f).coerceAtLeast(1.5f)
        val ox = w * fx
        val oy = h * fy
        drawLine(tint, Offset(0f, oy), Offset(w, oy), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(ox, 0f), Offset(ox, h), strokeWidth = sw, cap = StrokeCap.Round)
        drawCircle(Color(0xFFF59E0B), radius = w * 0.11f, center = Offset(ox, oy))
    }
}
