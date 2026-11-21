package com.example.lecturenote.model

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 家庭教師・授業ノート用データモデル (v3.2: 5度刻み直線スナップ・選択オブジェクトの拡大縮小回転対応)
 */

enum class ToolType(val label: String) {
    PEN("ペン"),
    HIGHLIGHTER("マーカー"),
    LINE_SHAPE("図形"),
    XY_AXES("XY軸"),
    LASER_POINTER("ポインター"),
    STROKE_ERASER("消しゴム"),
    LASSO("投げ縄"),
    COVER("目隠し")
}

enum class EraserMode(val label: String) {
    PARTIAL("部分"),
    STROKE("一筆")
}

enum class ShapeMode(val label: String) {
    LINE("直線"),
    ARROW("矢印"),
    RECTANGLE("四角"),
    CIRCLE("円"),
    PARABOLA("二次関数"),
    XY_AXES_SHAPE("XY軸"),
    TRIANGLE("三角形"),
    RIGHT_TRIANGLE("直角三角形"),
    NUMBER_LINE("数直線"),
    CROSSED_RECT("四角に×"),

    /** 目隠し（塗りつぶしの四角）。図形メニューには出さず、目隠しツールから作る */
    MASK("目隠し")
}

enum class BackgroundTemplate(val label: String, val shortLabel: String) {
    RULED_7MM("横罫線 (A罫・7mm)", "A罫 7mm"),
    RULED_6MM("横罫線 (B罫・6mm)", "B罫 6mm"),
    GRID_5MM("方眼 (5mm)", "方眼"),
    CORNELL("コーネル式ノート", "コーネル式"),
    BLANK("無地", "無地")
}

data class PenColor(
    val name: String,
    val colorInt: Int
) {
    companion object {
        val BLACK = PenColor("黒", Color.parseColor("#1E293B"))
        val BLUE = PenColor("青", Color.parseColor("#1D4ED8"))
        val RED = PenColor("赤", Color.parseColor("#DC2626"))
        val GREEN = PenColor("緑", Color.parseColor("#16A34A"))
        val ORANGE = PenColor("オレンジ", Color.parseColor("#EA580C"))
        val PURPLE = PenColor("紫", Color.parseColor("#7C3AED"))

        val HL_YELLOW = PenColor("黄マーカー", Color.parseColor("#FBBF24"))
        val HL_PINK = PenColor("桃マーカー", Color.parseColor("#F472B6"))
        val HL_CYAN = PenColor("水色マーカー", Color.parseColor("#38BDF8"))
        val HL_GREEN = PenColor("緑マーカー", Color.parseColor("#4ADE80"))

        val ALL_PEN_COLORS = listOf(BLACK, RED, BLUE, GREEN, ORANGE, PURPLE)
        val ALL_HL_COLORS = listOf(HL_YELLOW, HL_PINK, HL_CYAN, HL_GREEN)
    }
}

data class PenPreset(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val toolType: ToolType,
    val colorInt: Int,
    val strokeWidth: Float,
    val shapeMode: ShapeMode = ShapeMode.LINE,
    val isDashed: Boolean = false
) {
    companion object {
        fun defaultPresets(): List<PenPreset> = listOf(
            PenPreset(
                id = "preset_black_med",
                title = "黒・標準",
                toolType = ToolType.PEN,
                colorInt = PenColor.BLACK.colorInt,
                strokeWidth = 4.2f,
                isDashed = false
            ),
            PenPreset(
                id = "preset_red_med",
                title = "赤・添削",
                toolType = ToolType.PEN,
                colorInt = PenColor.RED.colorInt,
                strokeWidth = 4.2f,
                isDashed = false
            ),
            PenPreset(
                id = "preset_blue_fine",
                title = "青・補足(細)",
                toolType = ToolType.PEN,
                colorInt = PenColor.BLUE.colorInt,
                strokeWidth = 2.6f,
                isDashed = false
            ),
            PenPreset(
                id = "preset_black_dashed",
                title = "黒・点線",
                toolType = ToolType.PEN,
                colorInt = PenColor.BLACK.colorInt,
                strokeWidth = 3.6f,
                isDashed = true
            ),
            PenPreset(
                id = "preset_hl_yellow",
                title = "黄マーカー",
                toolType = ToolType.HIGHLIGHTER,
                colorInt = PenColor.HL_YELLOW.colorInt,
                strokeWidth = 24.0f,
                isDashed = false
            ),
            PenPreset(
                id = "preset_xy_axes",
                title = "XY軸",
                toolType = ToolType.XY_AXES,
                colorInt = PenColor.BLACK.colorInt,
                strokeWidth = 3.6f,
                shapeMode = ShapeMode.XY_AXES_SHAPE,
                isDashed = false
            )
        )
    }
}

data class NotePoint(val x: Float, val y: Float)

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<NotePoint>,
    val color: Int,
    val strokeWidth: Float,
    val toolType: ToolType,
    val shapeMode: ShapeMode? = null,
    val isDashed: Boolean = false,
    val rotationDeg: Float = 0f,
    /** XY軸の原点位置（枠の左上を 0,0・右下を 1,1 とした割合）。XY軸以外では使わない */
    val originFx: Float = 0.5f,
    val originFy: Float = 0.5f
) {
    val path: Path = buildStrokePath(points, shapeMode, rotationDeg, originFx, originFy)

    val isMask: Boolean get() = shapeMode == ShapeMode.MASK

    /** 図形の枠（ドラッグ開始点と終了点で決まる長方形） */
    fun shapeBox(): RectF {
        val a = points.first()
        val b = points.last()
        return RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    /** XY軸の原点の座標 */
    fun axesOriginPoint(): NotePoint {
        val box = shapeBox()
        return NotePoint(
            box.left + box.width() * originFx.coerceIn(0f, 1f),
            box.top + box.height() * originFy.coerceIn(0f, 1f)
        )
    }

    /** XY軸の原点だけを動かした新しいStrokeを返す */
    fun withAxesOrigin(fx: Float, fy: Float): Stroke {
        val mode = shapeMode ?: return this
        val nfx = fx.coerceIn(0f, 1f)
        val nfy = fy.coerceIn(0f, 1f)
        return copy(
            points = generateShapePoints(points.first(), points.last(), mode, nfx, nfy),
            originFx = nfx,
            originFy = nfy
        )
    }

    val bounds: RectF = RectF().apply {
        path.computeBounds(this, true)
        inset(-strokeWidth - 10f, -strokeWidth - 10f)
    }

    fun offsetBy(dx: Float, dy: Float, newId: String = UUID.randomUUID().toString()): Stroke {
        return copy(
            id = newId,
            points = points.map { NotePoint(it.x + dx, it.y + dy) }
        )
    }

    /**
     * 選択範囲の中心 (pivotX, pivotY) を基準に拡大縮小 (scale) および回転 (rotationRad) を適用した新しいStrokeを返す
     */
    fun transformAroundPivot(
        pivotX: Float,
        pivotY: Float,
        scale: Float,
        rotationRad: Float,
        keepId: String = id
    ): Stroke {
        val clampedScale = scale.coerceIn(0.15f, 6.0f)
        val cosA = cos(rotationRad.toDouble()).toFloat()
        val sinA = sin(rotationRad.toDouble()).toFloat()
        val deltaDeg = Math.toDegrees(rotationRad.toDouble()).toFloat()

        return if (shapeMode == null || shapeMode == ShapeMode.LINE || shapeMode == ShapeMode.ARROW) {
            val transformedPts = points.map { pt ->
                val dx = (pt.x - pivotX) * clampedScale
                val dy = (pt.y - pivotY) * clampedScale
                NotePoint(
                    x = dx * cosA - dy * sinA + pivotX,
                    y = dx * sinA + dy * cosA + pivotY
                )
            }
            copy(
                id = keepId,
                points = transformedPts,
                strokeWidth = (strokeWidth * clampedScale).coerceIn(1.2f, 48f)
            )
        } else {
            // 四角・円・二次関数・XY軸は、自身の中心をpivot周りに回転させつつ、サイズをclampedScale倍し、rotationDegを加算する
            val start = points.first()
            val end = points.last()
            val cx = (start.x + end.x) / 2f
            val cy = (start.y + end.y) / 2f

            val dcx = (cx - pivotX) * clampedScale
            val dcy = (cy - pivotY) * clampedScale
            val newCx = dcx * cosA - dcy * sinA + pivotX
            val newCy = dcx * sinA + dcy * cosA + pivotY

            val halfW = (end.x - start.x) * 0.5f * clampedScale
            val halfH = (end.y - start.y) * 0.5f * clampedScale
            val newStart = NotePoint(newCx - halfW, newCy - halfH)
            val newEnd = NotePoint(newCx + halfW, newCy + halfH)
            val newPoints = generateShapePoints(newStart, newEnd, shapeMode, originFx, originFy)

            copy(
                id = keepId,
                points = newPoints,
                strokeWidth = (strokeWidth * clampedScale).coerceIn(1.2f, 48f),
                rotationDeg = (rotationDeg + deltaDeg) % 360f
            )
        }
    }

    fun intersects(touchX: Float, touchY: Float, threshold: Float = 24f): Boolean {
        if (!bounds.contains(touchX, touchY)) return false
        val hitDistSq = (threshold + strokeWidth * 0.5f) * (threshold + strokeWidth * 0.5f)

        if (abs(rotationDeg) > 0.5f && shapeMode != null) {
            val contours = extractContoursFromPath(path, stepPx = 6f)
            for (c in contours) {
                for (i in 0 until c.size - 1) {
                    if (distToSegmentSquared(touchX, touchY, c[i].x, c[i].y, c[i + 1].x, c[i + 1].y) <= hitDistSq) {
                        return true
                    }
                }
            }
            return false
        }

        if (points.size == 1) {
            val dx = points[0].x - touchX
            val dy = points[0].y - touchY
            return dx * dx + dy * dy <= hitDistSq
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (distToSegmentSquared(touchX, touchY, p1.x, p1.y, p2.x, p2.y) <= hitDistSq) {
                return true
            }
        }
        return false
    }

    fun erasePartially(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        eraserRadius: Float
    ): List<Stroke>? {
        if (isMask) return null
        val effectiveRadius = eraserRadius + strokeWidth * 0.45f
        val segLeft = min(x1, x2) - effectiveRadius
        val segRight = max(x1, x2) + effectiveRadius
        val segTop = min(y1, y2) - effectiveRadius
        val segBottom = max(y1, y2) + effectiveRadius

        if (!bounds.intersects(segLeft, segTop, segRight, segBottom)) {
            return null
        }

        val hitRadiusSq = effectiveRadius * effectiveRadius
        val contours = extractContoursFromPath(path, stepPx = 3.5f)
        var anyPointErased = false
        val survivingStrokes = mutableListOf<Stroke>()

        for (contour in contours) {
            if (contour.isEmpty()) continue
            var currentRun = mutableListOf<NotePoint>()
            for (pt in contour) {
                val distSq = distToSegmentSquared(pt.x, pt.y, x1, y1, x2, y2)
                if (distSq <= hitRadiusSq) {
                    anyPointErased = true
                    if (currentRun.size >= 2) {
                        survivingStrokes.add(
                            Stroke(
                                points = decimatePoints(currentRun),
                                color = color,
                                strokeWidth = strokeWidth,
                                toolType = if (toolType == ToolType.HIGHLIGHTER) ToolType.HIGHLIGHTER else ToolType.PEN,
                                shapeMode = null,
                                isDashed = isDashed,
                                rotationDeg = 0f
                            )
                        )
                    }
                    currentRun = mutableListOf()
                } else {
                    currentRun.add(pt)
                }
            }
            if (currentRun.size >= 2) {
                survivingStrokes.add(
                    Stroke(
                        points = decimatePoints(currentRun),
                        color = color,
                        strokeWidth = strokeWidth,
                        toolType = if (toolType == ToolType.HIGHLIGHTER) ToolType.HIGHLIGHTER else ToolType.PEN,
                        shapeMode = null,
                        isDashed = isDashed,
                        rotationDeg = 0f
                    )
                )
            }
        }

        return if (anyPointErased) survivingStrokes else null
    }

    companion object {
        private const val NUMBER_LINE_DIVISIONS = 10

        /**
         * 直線ホールド時や直線・矢印ツール時に、5度刻み（0°, 5°, 10°, 15°...）で角度が張り付くスナップ処理
         */
        fun snapPointTo5Degrees(start: NotePoint, end: NotePoint): NotePoint {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val len = hypot(dx.toDouble(), dy.toDouble())
            if (len < 10.0) return end

            val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
            val snappedDeg = (angleDeg / 5.0).roundToInt() * 5.0
            val rad = Math.toRadians(snappedDeg)
            return NotePoint(
                x = (start.x + len * cos(rad)).toFloat(),
                y = (start.y + len * sin(rad)).toFloat()
            )
        }

        fun buildStrokePath(
            points: List<NotePoint>,
            shapeMode: ShapeMode?,
            rotationDeg: Float = 0f,
            originFx: Float = 0.5f,
            originFy: Float = 0.5f
        ): Path {
            val p = Path()
            if (points.isEmpty()) return p

            if (shapeMode != null && points.size >= 2) {
                val start = points.first()
                val end = points.last()
                when (shapeMode) {
                    ShapeMode.LINE -> {
                        p.moveTo(start.x, start.y)
                        p.lineTo(end.x, end.y)
                    }
                    ShapeMode.ARROW -> {
                        p.moveTo(start.x, start.y)
                        p.lineTo(end.x, end.y)
                        addArrowHead(p, start.x, start.y, end.x, end.y, headLen = 28.0)
                    }
                    ShapeMode.RECTANGLE, ShapeMode.MASK, ShapeMode.CROSSED_RECT -> {
                        val left = min(start.x, end.x)
                        val right = max(start.x, end.x)
                        val top = min(start.y, end.y)
                        val bottom = max(start.y, end.y)
                        p.addRect(left, top, right, bottom, Path.Direction.CW)
                        if (shapeMode == ShapeMode.CROSSED_RECT) {
                            p.moveTo(left, top)
                            p.lineTo(right, bottom)
                            p.moveTo(right, top)
                            p.lineTo(left, bottom)
                        }
                    }
                    ShapeMode.CIRCLE -> {
                        val (left, top, right, bottom) = computeCircleOrOvalBounds(start, end)
                        p.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
                    }
                    ShapeMode.PARABOLA -> {
                        val dx = max(14f, abs(end.x - start.x))
                        val dy = end.y - start.y
                        val leftX = start.x - dx
                        val rightX = start.x + dx
                        val edgeY = start.y + dy
                        p.moveTo(leftX, edgeY)
                        p.quadTo(start.x, start.y - dy, rightX, edgeY)
                    }
                    ShapeMode.XY_AXES_SHAPE -> {
                        val left = min(start.x, end.x)
                        val right = max(start.x, end.x)
                        val top = min(start.y, end.y)
                        val bottom = max(start.y, end.y)
                        // 矢印なしの十字。原点は枠内の好きな位置に置ける
                        val ox = left + (right - left) * originFx.coerceIn(0f, 1f)
                        val oy = top + (bottom - top) * originFy.coerceIn(0f, 1f)
                        p.moveTo(left, oy)
                        p.lineTo(right, oy)
                        p.moveTo(ox, bottom)
                        p.lineTo(ox, top)
                    }
                    ShapeMode.TRIANGLE -> {
                        // 二等辺三角形: ドラッグ開始側が頂点、終了側が底辺
                        val cx = (start.x + end.x) / 2f
                        p.moveTo(cx, start.y)
                        p.lineTo(end.x, end.y)
                        p.lineTo(start.x, end.y)
                        p.close()
                    }
                    ShapeMode.RIGHT_TRIANGLE -> {
                        // 直角三角形: 直角は (開始X, 終了Y) の位置
                        p.moveTo(start.x, start.y)
                        p.lineTo(start.x, end.y)
                        p.lineTo(end.x, end.y)
                        p.close()
                    }
                    ShapeMode.NUMBER_LINE -> {
                        // 数直線: 両端矢印＋等間隔の目盛り11本（中央が原点）
                        val left = min(start.x, end.x)
                        val right = max(start.x, end.x)
                        val cy = (start.y + end.y) / 2f
                        p.moveTo(left, cy)
                        p.lineTo(right, cy)
                        addArrowHead(p, left, cy, right, cy, headLen = 20.0)
                        addArrowHead(p, right, cy, left, cy, headLen = 20.0)
                        val inset = 36f
                        val usable = (right - left) - inset * 2f
                        if (usable > 60f) {
                            for (i in 0..NUMBER_LINE_DIVISIONS) {
                                val x = left + inset + usable * i / NUMBER_LINE_DIVISIONS
                                val h = if (i == NUMBER_LINE_DIVISIONS / 2) 18f else 10f
                                p.moveTo(x, cy - h)
                                p.lineTo(x, cy + h)
                            }
                        }
                    }
                }

                if (abs(rotationDeg) > 0.05f && shapeMode != ShapeMode.LINE && shapeMode != ShapeMode.ARROW) {
                    val boundsRect = RectF()
                    p.computeBounds(boundsRect, true)
                    val m = Matrix().apply {
                        postRotate(rotationDeg, boundsRect.centerX(), boundsRect.centerY())
                    }
                    p.transform(m)
                }
                return p
            }

            p.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                p.quadTo(prev.x, prev.y, midX, midY)
            }
            if (points.size > 1) {
                p.lineTo(points.last().x, points.last().y)
            }
            return p
        }

        private fun addArrowHead(
            p: Path,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            headLen: Double
        ) {
            val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
            val headAngle = Math.toRadians(27.0)
            val x1 = endX - headLen * cos(angle - headAngle)
            val y1 = endY - headLen * sin(angle - headAngle)
            val x2 = endX - headLen * cos(angle + headAngle)
            val y2 = endY - headLen * sin(angle + headAngle)
            p.moveTo(endX, endY)
            p.lineTo(x1.toFloat(), y1.toFloat())
            p.moveTo(endX, endY)
            p.lineTo(x2.toFloat(), y2.toFloat())
        }

        private fun computeCircleOrOvalBounds(start: NotePoint, end: NotePoint): FloatArray {
            val rawW = abs(end.x - start.x)
            val rawH = abs(end.y - start.y)
            val maxDim = max(rawW, rawH)
            val isNearCircle = maxDim > 10f && abs(rawW - rawH) / maxDim <= 0.18f
            val w = if (isNearCircle) maxDim else rawW
            val h = if (isNearCircle) maxDim else rawH
            val left = if (end.x >= start.x) start.x else start.x - w
            val top = if (end.y >= start.y) start.y else start.y - h
            return floatArrayOf(left, top, left + w, top + h)
        }

        fun generateShapePoints(
            start: NotePoint,
            end: NotePoint,
            mode: ShapeMode,
            originFx: Float = 0.5f,
            originFy: Float = 0.5f
        ): List<NotePoint> {
            return when (mode) {
                ShapeMode.LINE, ShapeMode.ARROW -> interpolateSegment(start, end, stepPx = 18f)
                ShapeMode.RECTANGLE, ShapeMode.MASK, ShapeMode.CROSSED_RECT -> {
                    val left = min(start.x, end.x)
                    val right = max(start.x, end.x)
                    val top = min(start.y, end.y)
                    val bottom = max(start.y, end.y)
                    val tl = NotePoint(left, top)
                    val tr = NotePoint(right, top)
                    val br = NotePoint(right, bottom)
                    val bl = NotePoint(left, bottom)
                    buildList {
                        add(start)
                        addAll(interpolateSegment(tl, tr, 20f))
                        addAll(interpolateSegment(tr, br, 20f))
                        addAll(interpolateSegment(br, bl, 20f))
                        addAll(interpolateSegment(bl, tl, 20f))
                        if (mode == ShapeMode.CROSSED_RECT) {
                            addAll(interpolateSegment(tl, br, 20f))
                            addAll(interpolateSegment(tr, bl, 20f))
                        }
                        add(end)
                    }
                }
                ShapeMode.CIRCLE -> {
                    val (left, top, right, bottom) = computeCircleOrOvalBounds(start, end)
                    val cx = (left + right) / 2f
                    val cy = (top + bottom) / 2f
                    val rx = (right - left) / 2f
                    val ry = (bottom - top) / 2f
                    val samples = mutableListOf<NotePoint>()
                    samples.add(start)
                    for (deg in 0..360 step 15) {
                        val rad = Math.toRadians(deg.toDouble())
                        samples.add(
                            NotePoint(
                                (cx + rx * cos(rad)).toFloat(),
                                (cy + ry * sin(rad)).toFloat()
                            )
                        )
                    }
                    samples.add(end)
                    samples
                }
                ShapeMode.PARABOLA -> {
                    val dx = max(14f, abs(end.x - start.x))
                    val dy = end.y - start.y
                    val samples = mutableListOf<NotePoint>()
                    samples.add(start)
                    val steps = 32
                    for (i in 0..steps) {
                        val t = -1f + (2f * i.toFloat() / steps.toFloat())
                        val px = start.x + t * dx
                        val py = start.y + (t * t) * dy
                        samples.add(NotePoint(px, py))
                    }
                    samples.add(end)
                    samples
                }
                ShapeMode.XY_AXES_SHAPE -> {
                    val left = min(start.x, end.x)
                    val right = max(start.x, end.x)
                    val top = min(start.y, end.y)
                    val bottom = max(start.y, end.y)
                    val cx = left + (right - left) * originFx.coerceIn(0f, 1f)
                    val cy = top + (bottom - top) * originFy.coerceIn(0f, 1f)
                    buildList {
                        add(start)
                        addAll(interpolateSegment(NotePoint(left, cy), NotePoint(right, cy), 20f))
                        addAll(interpolateSegment(NotePoint(cx, bottom), NotePoint(cx, top), 20f))
                        add(end)
                    }
                }
                ShapeMode.TRIANGLE -> {
                    val apex = NotePoint((start.x + end.x) / 2f, start.y)
                    val baseA = NotePoint(end.x, end.y)
                    val baseB = NotePoint(start.x, end.y)
                    buildList {
                        add(start)
                        addAll(interpolateSegment(apex, baseA, 20f))
                        addAll(interpolateSegment(baseA, baseB, 20f))
                        addAll(interpolateSegment(baseB, apex, 20f))
                        add(end)
                    }
                }
                ShapeMode.RIGHT_TRIANGLE -> {
                    val corner = NotePoint(start.x, end.y)
                    buildList {
                        add(start)
                        addAll(interpolateSegment(start, corner, 20f))
                        addAll(interpolateSegment(corner, end, 20f))
                        addAll(interpolateSegment(end, start, 20f))
                        add(end)
                    }
                }
                ShapeMode.NUMBER_LINE -> {
                    val left = min(start.x, end.x)
                    val right = max(start.x, end.x)
                    val cy = (start.y + end.y) / 2f
                    buildList {
                        add(start)
                        addAll(interpolateSegment(NotePoint(left, cy), NotePoint(right, cy), 20f))
                        add(end)
                    }
                }
            }
        }

        private fun extractContoursFromPath(path: Path, stepPx: Float): List<List<NotePoint>> {
            val pm = PathMeasure(path, false)
            val result = mutableListOf<List<NotePoint>>()
            val pos = FloatArray(2)
            do {
                val len = pm.length
                if (len > 0.5f) {
                    val count = max(2, (len / stepPx).toInt() + 1)
                    val pts = ArrayList<NotePoint>(count)
                    for (i in 0 until count) {
                        val d = (i.toFloat() / (count - 1).toFloat()) * len
                        if (pm.getPosTan(d, pos, null)) {
                            pts.add(NotePoint(pos[0], pos[1]))
                        }
                    }
                    result.add(pts)
                }
            } while (pm.nextContour())
            return result
        }

        private fun decimatePoints(pts: List<NotePoint>, minDist: Float = 2.2f): List<NotePoint> {
            if (pts.size <= 2) return pts
            val minDistSq = minDist * minDist
            val out = ArrayList<NotePoint>(pts.size)
            out.add(pts.first())
            for (i in 1 until pts.lastIndex) {
                val prev = out.last()
                val curr = pts[i]
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                if (dx * dx + dy * dy >= minDistSq) {
                    out.add(curr)
                }
            }
            out.add(pts.last())
            return out
        }

        private fun interpolateSegment(a: NotePoint, b: NotePoint, stepPx: Float): List<NotePoint> {
            val dist = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
            val steps = max(2, (dist / stepPx).toInt())
            val list = ArrayList<NotePoint>(steps + 1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                list.add(NotePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
            return list
        }

        private fun distToSegmentSquared(
            px: Float, py: Float,
            x1: Float, y1: Float,
            x2: Float, y2: Float
        ): Float {
            val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
            if (l2 == 0f) return (px - x1) * (px - x1) + (py - y1) * (py - y1)
            var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
            t = max(0f, min(1f, t))
            val projX = x1 + t * (x2 - x1)
            val projY = y1 + t * (y2 - y1)
            return (px - projX) * (px - projX) + (py - projY) * (py - projY)
        }
    }
}

class Page(
    val pageNumber: Int,
    val strokes: MutableList<Stroke> = mutableListOf(),
    val undoStack: MutableList<List<Stroke>> = mutableListOf(),
    val redoStack: MutableList<List<Stroke>> = mutableListOf()
) {
    fun saveSnapshot() {
        undoStack.add(strokes.toList())
        if (undoStack.size > 35) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.add(strokes.toList())
        val prev = undoStack.removeAt(undoStack.lastIndex)
        strokes.clear()
        strokes.addAll(prev)
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.add(strokes.toList())
        val next = redoStack.removeAt(redoStack.lastIndex)
        strokes.clear()
        strokes.addAll(next)
        return true
    }
}

data class LectureMetadata(
    val studentName: String = "サンプル生徒",
    val dateStr: String = "2026-11-14",
    val dayOfWeek: String = "土",
    val subject: String = "数学"
) {
    val formattedNoteName: String
        get() {
            val sName = studentName.trim().ifEmpty { "未設定" }
            val dStr = dateStr.trim().ifEmpty { "日付なし" }
            val dow = dayOfWeek.trim()
            val subj = subject.trim().ifEmpty { "教科なし" }
            val dayPart = if (dow.isNotEmpty()) "${dStr}(${dow})" else dStr
            return "${sName}_${dayPart}_${subj}"
        }
}

data class NoteSummary(
    val id: String,
    val metadata: LectureMetadata,
    val template: BackgroundTemplate,
    val pageCount: Int,
    val updatedAt: Long
)

data class LectureNoteDocument(
    val id: String = UUID.randomUUID().toString(),
    var metadata: LectureMetadata,
    var template: BackgroundTemplate = BackgroundTemplate.RULED_7MM,
    val pages: MutableList<Page> = mutableListOf(Page(1)),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toSummary(): NoteSummary = NoteSummary(
        id = id,
        metadata = metadata.copy(),
        template = template,
        pageCount = pages.size,
        updatedAt = updatedAt
    )
}

data class HistoryEntry(
    val value: String,
    val lastUsedAt: Long
)
