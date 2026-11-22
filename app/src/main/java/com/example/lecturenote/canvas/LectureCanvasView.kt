package com.example.lecturenote.canvas

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.EraserMode
import com.example.lecturenote.model.LectureNoteDocument
import com.example.lecturenote.model.NotePoint
import com.example.lecturenote.model.Page
import com.example.lecturenote.model.PenColor
import com.example.lecturenote.model.ShapeMode
import com.example.lecturenote.model.Stroke
import com.example.lecturenote.model.ToolType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * マルチページ連続スクロール・手書きキャンバス (v3.2)
 *
 * v3.2 改修点:
 * 1. 直線ホールド時および直線・矢印ツールでの「5度刻み」角度スナップ (0°, 5°, 10°, 15°...)
 * 2. 投げ縄（Lasso）で選択した範囲内のオブジェクトの「拡大縮小（四隅ハンドル）」＆「回転（上部ハンドル）」
 * 3. コピー／カット後、別の場所をタップした時は貼り付け先ターゲット位置の指定のみを行い、
 *    「ペースト」ボタンを押した時に貼り付けを実行。さらに貼り付け完了後も貼り付けたオブジェクトの選択状態を維持。
 */
class LectureCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val PAGE_WIDTH = 1200f
        const val PAGE_HEIGHT = 1697f
        const val PAGE_GAP = 56f
        private const val HOLD_TO_STRAIGHTEN_MS = 380L
        private const val MIN_POINT_DIST = 0.8f
        private const val LASER_TRAIL_LIFETIME_MS = 850L
        private const val ROTATE_HANDLE_OFFSET = 44f
        private const val HANDLE_HIT_RADIUS = 36f
        const val DEFAULT_ZOOM_PERCENT = 80
        private const val MASK_BADGE_RADIUS = 20f
        private val MASK_COLOR = Color.parseColor("#2A3040")
    }

    private enum class LassoDragMode {
        NONE,
        DRAW_LASSO,
        MOVE,
        SCALE,
        ROTATE,
        AXES_ORIGIN
    }

    // 現在のツール・描画設定
    var currentTool: ToolType = ToolType.PEN
    var currentShapeMode: ShapeMode = ShapeMode.LINE
    var eraserMode: EraserMode = EraserMode.PARTIAL
    var eraserRadius: Float = 14f
    var currentColor: Int = PenColor.BLACK.colorInt
    var currentStrokeWidth: Float = 4.2f
    var isDashedLine: Boolean = false
    var isStylusOnlyMode: Boolean = true
    var isZoomLocked: Boolean = false

    /** これから描くXY軸の原点位置（枠の左上=0,0 / 右下=1,1） */
    var axesOriginFx: Float = 0.5f
    var axesOriginFy: Float = 0.5f

    // 目隠し（範囲指定）: 作成中のドラッグ範囲と、今だけ開いて見せている目隠し
    private var coverPageIndex: Int = 0
    private var coverStart: NotePoint? = null
    private var coverCurrent: NotePoint? = null
    private val revealedMaskIds = mutableSetOf<String>()

    // 表示中のノートドキュメント
    private var document: LectureNoteDocument? = null
    private var lastRenderedDocId: String? = null
    private var lastRenderedTemplate: BackgroundTemplate? = null
    private var lastRenderedHeaderKey: String? = null

    // 実画面1:1ピクセル解像度のオフスクリーンキャッシュ
    private var screenCacheBitmap: Bitmap? = null
    private var screenCacheCanvas: Canvas? = null
    private var isScreenCacheValid: Boolean = false
    private var cachedPanX: Float = Float.NaN
    private var cachedPanY: Float = Float.NaN
    private var cachedScale: Float = Float.NaN

    // ズーム・パン状態
    var scaleFactor: Float = 1.0f
        private set
    private var baseFitScale: Float = 1.0f
    private var minScale: Float = 0.4f
    private var maxScale: Float = 5.0f
    private var panX: Float = 0f
    private var panY: Float = 0f
    private var isInitialScaleSet: Boolean = false

    // コールバック
    var onViewportChanged: (() -> Unit)? = null
    var onContentChanged: (() -> Unit)? = null
    var onSelectionChanged: ((hasSelection: Boolean, canPaste: Boolean) -> Unit)? = null
    var onStylusBarrelButtonPressed: (() -> Unit)? = null

    // 現在描画中のストローク
    private var activePageIndex: Int = 0
    private val currentPoints = mutableListOf<NotePoint>()
    private val currentPath = Path()
    private var snappedShapeMode: ShapeMode? = null
    private var lastMovePoint: NotePoint? = null
    private var hasSavedUndoForCurrentAction: Boolean = false

    // 消しゴムのドラッグ追跡＆ガイド円表示用
    private var isEraserTouching: Boolean = false
    private var eraserPageIndex: Int = 0
    private var lastEraserPoint: NotePoint? = null

    // レーザーポインター用データ
    private data class TimedLaserPoint(val x: Float, val y: Float, val timestamp: Long)
    private var laserPageIndex: Int = 0
    private val laserTrailPoints = mutableListOf<TimedLaserPoint>()
    private var laserCurrentTip: NotePoint? = null
    private var isLaserTouching: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val laserFadeRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            laserTrailPoints.removeAll { now - it.timestamp > LASER_TRAIL_LIFETIME_MS }
            invalidate()
            if (laserTrailPoints.isNotEmpty() || isLaserTouching) {
                mainHandler.postDelayed(this, 24L)
            }
        }
    }

    // ペン停止による直線・図形自動補正タイマー（5度刻みスナップ適用）
    private val straightenRunnable = Runnable {
        if (currentPoints.size >= 4 && (currentTool == ToolType.PEN || currentTool == ToolType.HIGHLIGHTER)) {
            val start = currentPoints.first()
            val rawEnd = currentPoints.last()
            val totalLen = calculatePolylineLength(currentPoints)
            if (totalLen > 45f) {
                val endDist = hypot((rawEnd.x - start.x).toDouble(), (rawEnd.y - start.y).toDouble()).toFloat()
                if (endDist < 55f && totalLen > 180f) {
                    snappedShapeMode = ShapeMode.RECTANGLE
                } else {
                    snappedShapeMode = ShapeMode.LINE
                    val snappedEnd = Stroke.snapPointTo5Degrees(start, rawEnd)
                    currentPoints[currentPoints.lastIndex] = snappedEnd
                }
                rebuildCurrentPreviewPath()
                invalidate()
            }
        }
    }

    // 投げ縄（Lasso）＆ 拡大縮小・回転 ＆ クリップボード状態
    private var lassoPageIndex: Int = 0
    private val lassoPoints = mutableListOf<NotePoint>()
    private val lassoPath = Path()
    val selectedStrokeIds = mutableSetOf<String>()
    private var selectionBounds: RectF? = null
    private var lassoDragMode: LassoDragMode = LassoDragMode.NONE
    private var lastDragDocPoint: NotePoint? = null

    // 拡大縮小・回転の基準点と開始時スナップショット
    private var transformPivot: NotePoint = NotePoint(0f, 0f)
    private var initialScaleDist: Float = 1f
    private var initialRotateAngleRad: Float = 0f
    private var originalSelectedStrokes: Map<String, Stroke> = emptyMap()

    // クリップボード＆貼り付けターゲット座標（タップで位置指定 → 「ペースト」ボタンで貼り付け）
    private val clipboardStrokes = mutableListOf<Stroke>()
    private var pendingPastePageIndex: Int = 0
    private var pendingPastePoint: NotePoint? = null

    // マルチタッチ・ジェスチャー用トラッカー
    private var maxTouchPointers: Int = 0
    private var multiTouchDownTime: Long = 0L
    private var maxFingerTravelPx: Float = 0f
    private val initialFingerPositions = mutableMapOf<Int, NotePoint>()
    private var prevFocalX: Float = 0f
    private var prevFocalY: Float = 0f
    private var prevPinchSpan: Float = 0f
    private var isMultiTouchPanningOrZooming: Boolean = false

    // スタイラスのサイドボタンエッジ検出用
    private var wasBarrelButtonDown: Boolean = false

    // Paint定義
    private val canvasBgColor = Color.parseColor("#E9EBEF")
    private val pageBgPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pageShadowPaint = Paint().apply {
        color = Color.parseColor("#CDD2DA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val pageDividerTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#8A93A2")
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1.4f
    }
    private val cornellGuidePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#94A3B8")
        strokeWidth = 2.4f
    }
    private val headerTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#64748B")
        textSize = 24f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val lassoPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#2563EB")
        strokeWidth = 3.0f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val selectionBoxPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#1D4ED8")
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val selectionFillPaint = Paint().apply {
        color = Color.parseColor("#142563EB")
        style = Paint.Style.FILL
    }
    private val handleFillWhitePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val scaleHandleStrokePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val rotateHandleFillPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    private val rotateHandleStemPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#10B981")
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
    }
    private val axesOriginHandlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }
    private val maskFillPaint = Paint().apply {
        isAntiAlias = true
        color = MASK_COLOR
        style = Paint.Style.FILL
    }
    private val maskRevealedFillPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#142A3040")
        style = Paint.Style.FILL
    }
    private val maskOutlinePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#5B6472")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val maskPreviewPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#992A3040")
        style = Paint.Style.FILL
    }
    private val maskBadgePaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#DC2626")
        style = Paint.Style.FILL
    }
    private val maskBadgeCrossPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val pasteTargetPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    // 消しゴムガイド円用Paint
    private val eraserCursorFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#22EF4444")
    }
    private val eraserCursorStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#DC2626")
    }

    // レーザーポインター用Paint
    private val laserGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF4D6D")
    }
    private val laserCorePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E11D48")
    }
    private val laserTipHaloPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#66F43F5E")
    }
    private val laserTipRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#E11D48")
    }
    private val laserTipCenterPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val visiblePageDocRect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setDocument(doc: LectureNoteDocument) {
        val isNewDoc = lastRenderedDocId != doc.id
        val headerKey = "${doc.metadata.formattedNoteName}_${doc.pages.size}"
        val isTemplateOrHeaderChanged = lastRenderedTemplate != doc.template || lastRenderedHeaderKey != headerKey

        this.document = doc
        lastRenderedDocId = doc.id
        lastRenderedTemplate = doc.template
        lastRenderedHeaderKey = headerKey

        if (isNewDoc) {
            revealedMaskIds.clear()
            invalidateScreenCache()
            clearLassoSelection()
            pendingPastePoint = null
            if (width > 0) {
                resetZoomToDefault()
            } else {
                isInitialScaleSet = false
            }
            invalidate()
        } else if (isTemplateOrHeaderChanged) {
            invalidateScreenCache()
            invalidate()
        }
    }

    fun invalidateScreenCache() {
        isScreenCacheValid = false
    }

    private fun releaseScreenCache() {
        isScreenCacheValid = false
        screenCacheCanvas = null
        screenCacheBitmap?.recycle()
        screenCacheBitmap = null
    }

    override fun onDetachedFromWindow() {
        removeAllPrivatePanels()
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(laserFadeRunnable)
        releaseScreenCache()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseScreenCache()
        if (w > 0 && h > 0) {
            val horizontalPadding = 20f
            baseFitScale = ((w - horizontalPadding * 2f) / PAGE_WIDTH).coerceAtLeast(0.3f)
            minScale = baseFitScale * 0.5f
            maxScale = baseFitScale * 3.0f
            if (!isInitialScaleSet) {
                resetZoomToDefault()
                isInitialScaleSet = true
            }
        }
    }

    fun getRelativeZoomPercent(): Int {
        if (baseFitScale <= 0.01f) return 100
        return ((scaleFactor / baseFitScale) * 100f).roundToInt().coerceIn(50, 300)
    }

    fun adjustZoomByPercentDelta(deltaPercent: Int) {
        if (width <= 0 || baseFitScale <= 0.01f) return
        val currentPct = getRelativeZoomPercent()
        val targetPct = (currentPct + deltaPercent).coerceIn(50, 300)
        val newScale = (baseFitScale * (targetPct / 100f)).coerceIn(minScale, maxScale)
        val centerX = width / 2f
        val centerY = height / 2f
        val ratio = newScale / scaleFactor
        panX = centerX - (centerX - panX) * ratio
        panY = centerY - (centerY - panY) * ratio
        scaleFactor = newScale
        clampPanBounds()
        invalidateScreenCache()
        invalidate()
        onViewportChanged?.invoke()
    }

    /** 指定した倍率（幅いっぱい=100%）にする。画面中央に見えている位置は保つ */
    fun setZoomPercent(targetPercent: Int) {
        adjustZoomByPercentDelta(targetPercent - getRelativeZoomPercent())
    }

    /** ノートを開いたときの表示: 既定の倍率で、左右に余白を残して中央に置く */
    fun resetZoomToDefault() {
        if (width <= 0) return
        val horizontalPadding = 20f
        baseFitScale = ((width - horizontalPadding * 2f) / PAGE_WIDTH).coerceAtLeast(0.3f)
        minScale = baseFitScale * 0.5f
        maxScale = baseFitScale * 3.0f
        scaleFactor = baseFitScale * (DEFAULT_ZOOM_PERCENT / 100f)
        panX = (width - PAGE_WIDTH * scaleFactor) / 2f
        panY = 24f
        clampPanBounds()
        invalidateScreenCache()
        invalidate()
        onViewportChanged?.invoke()
    }

    fun resetZoomToFitWidth() {
        if (width <= 0) return
        val horizontalPadding = 20f
        baseFitScale = ((width - horizontalPadding * 2f) / PAGE_WIDTH).coerceAtLeast(0.3f)
        minScale = baseFitScale * 0.5f
        maxScale = baseFitScale * 3.0f
        scaleFactor = baseFitScale
        panX = (width - PAGE_WIDTH * scaleFactor) / 2f
        panY = 56f
        clampPanBounds()
        invalidateScreenCache()
        invalidate()
        onViewportChanged?.invoke()
    }

    fun onPageAppendedBelow(insertedPageIndex: Int) {
        val doc = document ?: return
        invalidateScreenCache()
        if (insertedPageIndex <= 0 || insertedPageIndex > doc.pages.lastIndex) return

        val newPageTopDocY = insertedPageIndex * (PAGE_HEIGHT + PAGE_GAP)
        val targetPanY = (height * 0.68f) - (newPageTopDocY * scaleFactor)
        if (panY > targetPanY) {
            panY = targetPanY
        }
        clampPanBounds()
        invalidate()
        onViewportChanged?.invoke()
    }

    fun onPageRemoved() {
        val doc = document ?: return
        invalidateScreenCache()
        activePageIndex = activePageIndex.coerceIn(0, max(0, doc.pages.lastIndex))
        lassoPageIndex = lassoPageIndex.coerceIn(0, max(0, doc.pages.lastIndex))
        clearLassoSelection()
        clampPanBounds()
        invalidate()
        onViewportChanged?.invoke()
    }

    /** 指定ページの先頭が画面上端に来るようにスクロールする（ページ一覧からのジャンプ用） */
    fun scrollToPage(pageIndex: Int) {
        val doc = document ?: return
        if (doc.pages.isEmpty()) return
        val idx = pageIndex.coerceIn(0, doc.pages.lastIndex)
        panY = 24f - idx * (PAGE_HEIGHT + PAGE_GAP) * scaleFactor
        clampPanBounds()
        invalidateScreenCache()
        invalidate()
        onViewportChanged?.invoke()
    }

    fun getCurrentVisiblePageNumber(): Int {
        val doc = document ?: return 1
        if (doc.pages.isEmpty()) return 1
        val centerDocY = ((height * 0.45f) - panY) / scaleFactor
        val idx = (centerDocY / (PAGE_HEIGHT + PAGE_GAP)).toInt().coerceIn(0, doc.pages.lastIndex)
        return idx + 1
    }

    // --- Undo / Redo ---
    fun undo(): Boolean {
        val doc = document ?: return false
        val targetIdx = if (doc.pages.getOrNull(activePageIndex)?.undoStack?.isNotEmpty() == true) {
            activePageIndex
        } else {
            doc.pages.indexOfLast { it.undoStack.isNotEmpty() }
        }
        if (targetIdx < 0) return false

        val result = doc.pages[targetIdx].undo()
        if (result) {
            invalidateScreenCache()
            clearLassoSelection()
            invalidate()
            onContentChanged?.invoke()
        }
        return result
    }

    fun redo(): Boolean {
        val doc = document ?: return false
        val targetIdx = if (doc.pages.getOrNull(activePageIndex)?.redoStack?.isNotEmpty() == true) {
            activePageIndex
        } else {
            doc.pages.indexOfLast { it.redoStack.isNotEmpty() }
        }
        if (targetIdx < 0) return false

        val result = doc.pages[targetIdx].redo()
        if (result) {
            invalidateScreenCache()
            clearLassoSelection()
            invalidate()
            onContentChanged?.invoke()
        }
        return result
    }

    fun canUndo(): Boolean = document?.pages?.any { it.undoStack.isNotEmpty() } == true
    fun canRedo(): Boolean = document?.pages?.any { it.redoStack.isNotEmpty() } == true

    fun clearPage(pageIndex: Int = getCurrentVisiblePageNumber() - 1) {
        val page = document?.pages?.getOrNull(pageIndex) ?: return
        if (page.strokes.isNotEmpty()) {
            page.saveSnapshot()
            page.strokes.clear()
            invalidateScreenCache()
            clearLassoSelection()
            invalidate()
            onContentChanged?.invoke()
        }
    }

    // --- 投げ縄（Lasso）操作: コピー / カット / ペーストボタンによる貼り付け（選択維持） ---
    fun copySelectionToClipboard(): Boolean {
        val page = document?.pages?.getOrNull(lassoPageIndex) ?: return false
        val selected = page.strokes.filter { it.id in selectedStrokeIds }
        if (selected.isEmpty()) return false
        clipboardStrokes.clear()
        clipboardStrokes.addAll(selected.map { it.offsetBy(0f, 0f) })
        // デフォルトの貼り付け位置（現在位置から少し右下）をセットしつつ、別場所タップで更新可能にする
        val bounds = selectionBounds
        pendingPastePageIndex = lassoPageIndex
        pendingPastePoint = if (bounds != null) {
            NotePoint(
                (bounds.centerX() + 36f).coerceIn(40f, PAGE_WIDTH - 40f),
                (bounds.centerY() + 36f).coerceIn(40f, PAGE_HEIGHT - 40f)
            )
        } else {
            NotePoint(PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        }
        notifySelectionState()
        invalidate()
        return true
    }

    fun cutSelectionToClipboard(): Boolean {
        val page = document?.pages?.getOrNull(lassoPageIndex) ?: return false
        val selected = page.strokes.filter { it.id in selectedStrokeIds }
        if (selected.isEmpty()) return false
        val bounds = selectionBounds
        page.saveSnapshot()
        clipboardStrokes.clear()
        clipboardStrokes.addAll(selected.map { it.offsetBy(0f, 0f) })
        page.strokes.removeAll { it.id in selectedStrokeIds }
        invalidateScreenCache()
        selectedStrokeIds.clear()
        selectionBounds = null
        pendingPastePageIndex = lassoPageIndex
        pendingPastePoint = if (bounds != null) {
            NotePoint(bounds.centerX(), bounds.centerY())
        } else {
            NotePoint(PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        }
        invalidate()
        notifySelectionState()
        onContentChanged?.invoke()
        return true
    }

    /**
     * 「ペースト」ボタンが押されたときに貼り付けを実行し、
     * 貼り付けたオブジェクトに対して選択枠（移動・拡大縮小・回転可能状態）を維持する
     */
    fun pasteClipboardAtTarget(): Boolean {
        if (clipboardStrokes.isEmpty()) return false
        val targetPageIdx = pendingPastePageIndex.coerceIn(0, max(0, (document?.pages?.size ?: 1) - 1))
        val page = document?.pages?.getOrNull(targetPageIdx) ?: return false

        val unionRect = RectF()
        clipboardStrokes.forEachIndexed { i, s ->
            if (i == 0) unionRect.set(s.bounds) else unionRect.union(s.bounds)
        }
        val targetPt = pendingPastePoint ?: NotePoint(
            (unionRect.centerX() + 36f).coerceIn(40f, PAGE_WIDTH - 40f),
            (unionRect.centerY() + 36f).coerceIn(40f, PAGE_HEIGHT - 40f)
        )

        val dx = targetPt.x - unionRect.centerX()
        val dy = targetPt.y - unionRect.centerY()

        page.saveSnapshot()
        selectedStrokeIds.clear()
        lassoPageIndex = targetPageIdx
        activePageIndex = targetPageIdx

        val pasted = clipboardStrokes.map { stroke -> stroke.offsetBy(dx, dy) }
        page.strokes.addAll(pasted)
        // 貼り付けたオブジェクトに対して選択を維持する
        pasted.forEach { selectedStrokeIds.add(it.id) }
        recomputeSelectionBounds(page)

        // 次回連続ペーストする場合のためにターゲット位置を少しずらしておく
        pendingPastePoint = NotePoint(
            (targetPt.x + 32f).coerceIn(40f, PAGE_WIDTH - 40f),
            (targetPt.y + 32f).coerceIn(40f, PAGE_HEIGHT - 40f)
        )

        invalidateScreenCache()
        invalidate()
        notifySelectionState()
        onContentChanged?.invoke()
        return true
    }

    fun deleteSelection() {
        val page = document?.pages?.getOrNull(lassoPageIndex) ?: return
        if (selectedStrokeIds.isEmpty()) return
        page.saveSnapshot()
        page.strokes.removeAll { it.id in selectedStrokeIds }
        invalidateScreenCache()
        clearLassoSelection()
        invalidate()
        onContentChanged?.invoke()
    }

    fun clearLassoSelection() {
        selectedStrokeIds.clear()
        selectionBounds = null
        lassoPoints.clear()
        lassoPath.reset()
        lassoDragMode = LassoDragMode.NONE
        originalSelectedStrokes = emptyMap()
        notifySelectionState()
        invalidate()
    }

    fun clearClipboardAndSelection() {
        clipboardStrokes.clear()
        pendingPastePoint = null
        clearLassoSelection()
    }

    private fun notifySelectionState() {
        onSelectionChanged?.invoke(
            selectedStrokeIds.isNotEmpty(),
            clipboardStrokes.isNotEmpty()
        )
    }

    // --- 1:1実画面解像度キャッシュ ---
    private fun ensureScreenCacheBitmap(doc: LectureNoteDocument): Bitmap? {
        if (width <= 0 || height <= 0) return null

        var bmp = screenCacheBitmap
        if (bmp == null || bmp.isRecycled || bmp.width != width || bmp.height != height) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            screenCacheBitmap = bmp
            screenCacheCanvas = Canvas(bmp)
            isScreenCacheValid = false
        }

        if (!isScreenCacheValid ||
            cachedPanX != panX ||
            cachedPanY != panY ||
            cachedScale != scaleFactor
        ) {
            val cacheCanvas = screenCacheCanvas ?: Canvas(bmp).also { screenCacheCanvas = it }
            renderVectorDocumentToCanvas(cacheCanvas, doc)
            cachedPanX = panX
            cachedPanY = panY
            cachedScale = scaleFactor
            isScreenCacheValid = true
        }

        return bmp
    }

    private fun appendStrokeToScreenCache(pageIdx: Int, stroke: Stroke) {
        // マーカーは下に、目隠しは上に重なるため、どちらかが絡むときは全体を描き直す
        val pageHasMask = document?.pages?.getOrNull(pageIdx)?.strokes?.any { it.isMask } == true
        if (stroke.toolType == ToolType.HIGHLIGHTER || pageHasMask) {
            invalidateScreenCache()
            return
        }
        val cacheCanvas = screenCacheCanvas
        if (cacheCanvas != null && isScreenCacheValid &&
            cachedPanX == panX && cachedPanY == panY && cachedScale == scaleFactor
        ) {
            val pageTopY = pageIdx * (PAGE_HEIGHT + PAGE_GAP)
            cacheCanvas.save()
            cacheCanvas.translate(panX, panY)
            cacheCanvas.scale(scaleFactor, scaleFactor)
            cacheCanvas.translate(0f, pageTopY)
            drawSingleStroke(cacheCanvas, stroke)
            cacheCanvas.restore()
        } else {
            invalidateScreenCache()
        }
    }

    private fun renderVectorDocumentToCanvas(
        canvas: Canvas,
        doc: LectureNoteDocument,
        drawMasks: Boolean = true
    ) {
        canvas.drawColor(canvasBgColor)

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        val viewLeftDoc = -panX / scaleFactor
        val viewRightDoc = (width - panX) / scaleFactor

        for ((pageIdx, page) in doc.pages.withIndex()) {
            val pageTopY = pageIdx * (PAGE_HEIGHT + PAGE_GAP)

            if (pageIdx > 0) {
                val bannerY = pageTopY - PAGE_GAP / 2f + 8f
                val bannerScreenY = bannerY * scaleFactor + panY
                if (bannerScreenY in -60f..(height + 60f)) {
                    canvas.drawText(
                        "Page ${pageIdx + 1} / ${doc.pages.size}",
                        PAGE_WIDTH / 2f,
                        bannerY,
                        pageDividerTextPaint
                    )
                }
            }

            val screenTop = pageTopY * scaleFactor + panY
            val screenBottom = (pageTopY + PAGE_HEIGHT) * scaleFactor + panY
            if (screenBottom < -80f || screenTop > height + 80f) {
                continue
            }

            val visibleTopLocal = ((-panY / scaleFactor) - pageTopY).coerceAtLeast(0f)
            val visibleBottomLocal = (((height - panY) / scaleFactor) - pageTopY).coerceAtMost(PAGE_HEIGHT)
            visiblePageDocRect.set(
                viewLeftDoc.coerceAtLeast(0f),
                visibleTopLocal,
                viewRightDoc.coerceAtMost(PAGE_WIDTH),
                visibleBottomLocal
            )

            canvas.save()
            canvas.translate(0f, pageTopY)

            canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, pageBgPaint)
            canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, pageShadowPaint)

            drawPageHeaderAndTemplate(canvas, doc, pageIdx, doc.pages.size)

            for (i in 0 until page.strokes.size) {
                val stroke = page.strokes[i]
                if (stroke.toolType == ToolType.HIGHLIGHTER &&
                    RectF.intersects(visiblePageDocRect, stroke.bounds)
                ) {
                    drawSingleStroke(canvas, stroke)
                }
            }

            for (i in 0 until page.strokes.size) {
                val stroke = page.strokes[i]
                if (stroke.toolType != ToolType.HIGHLIGHTER && !stroke.isMask &&
                    RectF.intersects(visiblePageDocRect, stroke.bounds)
                ) {
                    drawSingleStroke(canvas, stroke)
                }
            }

            // 目隠しはすべての書き込みの上に描く
            for (i in 0 until page.strokes.size) {
                val stroke = page.strokes[i]
                if (drawMasks && stroke.isMask && RectF.intersects(visiblePageDocRect, stroke.bounds)) {
                    drawMask(canvas, stroke)
                }
            }

            canvas.restore()
        }

        canvas.restore()
    }

    // --- 描画処理 ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val doc = document ?: return

        val isTransformingSelection = lassoDragMode == LassoDragMode.MOVE ||
            lassoDragMode == LassoDragMode.SCALE ||
            lassoDragMode == LassoDragMode.ROTATE ||
            lassoDragMode == LassoDragMode.AXES_ORIGIN

        if (isMultiTouchPanningOrZooming || isTransformingSelection) {
            renderVectorDocumentToCanvas(canvas, doc)
        } else {
            val cacheBmp = ensureScreenCacheBitmap(doc)
            if (cacheBmp != null) {
                canvas.drawBitmap(cacheBmp, 0f, 0f, null)
            } else {
                renderVectorDocumentToCanvas(canvas, doc)
            }
        }

        val hasLaserOverlay = isLaserTouching || laserTrailPoints.isNotEmpty()
        val hasEraserOverlay = isEraserTouching && lastEraserPoint != null
        val hasPasteTargetOverlay = currentTool == ToolType.LASSO && clipboardStrokes.isNotEmpty() && pendingPastePoint != null && selectedStrokeIds.isEmpty()

        val hasCoverPreview = coverStart != null && coverCurrent != null

        if (currentPoints.isNotEmpty() ||
            hasCoverPreview ||
            lassoPoints.isNotEmpty() ||
            selectionBounds != null ||
            hasLaserOverlay ||
            hasEraserOverlay ||
            hasPasteTargetOverlay
        ) {
            canvas.save()
            canvas.translate(panX, panY)
            canvas.scale(scaleFactor, scaleFactor)

            if (currentPoints.isNotEmpty()) {
                val activePageTopY = activePageIndex * (PAGE_HEIGHT + PAGE_GAP)
                canvas.save()
                canvas.translate(0f, activePageTopY)
                when (currentTool) {
                    ToolType.PEN, ToolType.HIGHLIGHTER, ToolType.LINE_SHAPE, ToolType.XY_AXES -> {
                        applyPaintSettings(
                            paint = strokePaint,
                            tool = currentTool,
                            color = currentColor,
                            width = currentStrokeWidth,
                            isDashed = isDashedLine && currentTool != ToolType.HIGHLIGHTER
                        )
                        canvas.drawPath(currentPath, strokePaint)
                        if (snappedShapeMode == ShapeMode.PARABOLA) {
                            val v = currentPoints.first()
                            canvas.drawCircle(v.x, v.y, 5.5f, scaleHandleStrokePaint)
                        }
                    }
                    else -> {}
                }
                canvas.restore()
            }

            if (hasCoverPreview) {
                val s0 = coverStart
                val e0 = coverCurrent
                if (s0 != null && e0 != null) {
                    canvas.save()
                    canvas.translate(0f, coverPageIndex * (PAGE_HEIGHT + PAGE_GAP))
                    canvas.drawRect(
                        min(s0.x, e0.x), min(s0.y, e0.y), max(s0.x, e0.x), max(s0.y, e0.y),
                        maskPreviewPaint
                    )
                    canvas.restore()
                }
            }

            if (hasEraserOverlay) {
                val eraserPageTopY = eraserPageIndex * (PAGE_HEIGHT + PAGE_GAP)
                canvas.save()
                canvas.translate(0f, eraserPageTopY)
                lastEraserPoint?.let { pt ->
                    canvas.drawCircle(pt.x, pt.y, eraserRadius, eraserCursorFillPaint)
                    canvas.drawCircle(pt.x, pt.y, eraserRadius, eraserCursorStrokePaint)
                }
                canvas.restore()
            }

            if (hasLaserOverlay) {
                val laserPageTopY = laserPageIndex * (PAGE_HEIGHT + PAGE_GAP)
                canvas.save()
                canvas.translate(0f, laserPageTopY)
                drawLaserPointerOverlay(canvas)
                canvas.restore()
            }

            // ペースト予定位置のターゲット十字マーカー表示
            if (hasPasteTargetOverlay) {
                val pastePageTopY = pendingPastePageIndex * (PAGE_HEIGHT + PAGE_GAP)
                canvas.save()
                canvas.translate(0f, pastePageTopY)
                pendingPastePoint?.let { pt ->
                    canvas.drawCircle(pt.x, pt.y, 12f, selectionFillPaint)
                    canvas.drawCircle(pt.x, pt.y, 12f, pasteTargetPaint)
                    canvas.drawLine(pt.x - 18f, pt.y, pt.x + 18f, pt.y, pasteTargetPaint)
                    canvas.drawLine(pt.x, pt.y - 18f, pt.x, pt.y + 18f, pasteTargetPaint)
                }
                canvas.restore()
            }

            // 投げ縄の軌跡および選択ボックス（四隅の拡大縮小ハンドル＋上部の緑色回転ハンドル）
            if (lassoPoints.isNotEmpty() || selectionBounds != null) {
                val lassoPageTopY = lassoPageIndex * (PAGE_HEIGHT + PAGE_GAP)
                canvas.save()
                canvas.translate(0f, lassoPageTopY)
                if (lassoPoints.isNotEmpty()) {
                    canvas.drawPath(lassoPath, lassoPaint)
                }
                selectionBounds?.let { rect ->
                    canvas.drawRect(rect, selectionFillPaint)
                    canvas.drawRect(rect, selectionBoxPaint)

                    // 上部中央の「回転ハンドル」（緑色）
                    val rotX = rect.centerX()
                    val rotY = rect.top - ROTATE_HANDLE_OFFSET
                    canvas.drawLine(rotX, rect.top, rotX, rotY, rotateHandleStemPaint)
                    canvas.drawCircle(rotX, rotY, 15f, rotateHandleFillPaint)
                    canvas.drawCircle(rotX, rotY, 6f, handleFillWhitePaint)

                    // 四隅の「拡大縮小ハンドル」（白塗り＋青枠）
                    val r = 12f
                    val corners = listOf(
                        rect.left to rect.top,
                        rect.right to rect.top,
                        rect.left to rect.bottom,
                        rect.right to rect.bottom
                    )
                    for ((cx, cy) in corners) {
                        canvas.drawCircle(cx, cy, r, handleFillWhitePaint)
                        canvas.drawCircle(cx, cy, r, scaleHandleStrokePaint)
                    }

                    // XY軸をひとつだけ選んでいるとき: 原点を動かすオレンジのハンドル
                    singleSelectedAxes(doc.pages.getOrNull(lassoPageIndex))?.let { axes ->
                        val o = axes.axesOriginPoint()
                        canvas.drawCircle(o.x, o.y, 16f, handleFillWhitePaint)
                        canvas.drawCircle(o.x, o.y, 12f, axesOriginHandlePaint)
                    }
                }
                canvas.restore()
            }

            canvas.restore()
        }

        syncPrivatePanels()
    }

    // -----------------------------------------------------------------------
    // 【試作】配信では隠し、手元にだけ見せる目隠し
    //
    // 目隠し自体は今までどおり本体の画面に塗りつぶしで描く（＝画面共有に映るのは塗りつぶし）。
    // そのうえで、目隠しと同じ位置に「画面共有・録画には映らない(FLAG_SECURE)」小さな窓を重ね、
    // その窓の中にだけ隠した内容を描く。手元では中身が見え、配信先には黒い四角として映る想定。
    // -----------------------------------------------------------------------

    /** true のあいだ、開いていない目隠しの中身を手元にだけ表示する */
    var isPrivateCoverMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val privatePanels = HashMap<String, PrivatePanelView>()
    private val locationInWindow = IntArray(2)

    /** 動作確認用: いま重ねている「手元だけ表示」の窓の数 */
    val privatePanelCount: Int get() = privatePanels.size

    private val privateBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#F59E0B")
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }
    private val privateTintPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#14F59E0B")
    }
    private val privateLabelBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#F59E0B")
    }
    private val privateLabelTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private inner class PrivatePanelView(context: Context) : View(context) {
        /** この窓の左上が、キャンバス上のどこに当たるか */
        var offsetX: Float = 0f
        var offsetY: Float = 0f

        override fun onDraw(canvas: Canvas) {
            val doc = document ?: return
            canvas.save()
            canvas.translate(-offsetX, -offsetY)
            renderVectorDocumentToCanvas(canvas, doc, drawMasks = false)
            drawLiveStroke(canvas)
            canvas.restore()

            // 「ここは配信に映っていない」と手元で分かるよう、枠とラベルを付ける
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), privateTintPaint)
            canvas.drawRect(2f, 2f, width - 2f, height - 2f, privateBorderPaint)
            val label = "配信では非表示"
            val textWidth = privateLabelTextPaint.measureText(label)
            if (width > textWidth + 40f && height > 60f) {
                canvas.drawRoundRect(4f, 4f, textWidth + 24f, 38f, 8f, 8f, privateLabelBgPaint)
                canvas.drawText(label, 14f, 29f, privateLabelTextPaint)
            }
        }
    }

    /** 書いている途中の線（ペンを離す前）を、手元だけの窓にも描く */
    private fun drawLiveStroke(canvas: Canvas) {
        if (currentPoints.isEmpty()) return
        val isDrawingTool = currentTool == ToolType.PEN || currentTool == ToolType.HIGHLIGHTER ||
            currentTool == ToolType.LINE_SHAPE || currentTool == ToolType.XY_AXES
        if (!isDrawingTool) return
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(0f, activePageIndex * (PAGE_HEIGHT + PAGE_GAP))
        applyPaintSettings(
            paint = strokePaint,
            tool = currentTool,
            color = currentColor,
            width = currentStrokeWidth,
            isDashed = isDashedLine && currentTool != ToolType.HIGHLIGHTER
        )
        canvas.drawPath(currentPath, strokePaint)
        canvas.restore()
    }

    /** 目隠しの位置に合わせて、手元だけの窓を追加・移動・削除する（描画のたびに呼ぶ） */
    private fun syncPrivatePanels() {
        val doc = document
        val token = windowToken
        if (!isPrivateCoverMode || doc == null || token == null || !isAttachedToWindow) {
            removeAllPrivatePanels()
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        getLocationInWindow(locationInWindow)
        val stillNeeded = HashSet<String>()

        for ((pageIdx, page) in doc.pages.withIndex()) {
            val pageTop = pageIdx * (PAGE_HEIGHT + PAGE_GAP)
            for (stroke in page.strokes) {
                if (!stroke.isMask || stroke.id in revealedMaskIds) continue
                val box = stroke.shapeBox()
                val left = max(0, floor(panX + box.left * scaleFactor).toInt())
                val top = max(0, floor(panY + (pageTop + box.top) * scaleFactor).toInt())
                val right = min(width, ceil(panX + box.right * scaleFactor).toInt())
                val bottom = min(height, ceil(panY + (pageTop + box.bottom) * scaleFactor).toInt())
                if (right - left < 8 || bottom - top < 8) continue

                val x = locationInWindow[0] + left
                val y = locationInWindow[1] + top
                val w = right - left
                val h = bottom - top
                var panel = privatePanels[stroke.id]
                try {
                    if (panel == null) {
                        panel = PrivatePanelView(context)
                        val lp = WindowManager.LayoutParams(
                            w, h,
                            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                            WindowManager.LayoutParams.FLAG_SECURE or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            PixelFormat.OPAQUE
                        ).apply {
                            this.token = token
                            gravity = Gravity.TOP or Gravity.START
                            this.x = x
                            this.y = y
                            windowAnimations = 0
                            title = "LectureNotePrivateCover"
                        }
                        wm.addView(panel, lp)
                        privatePanels[stroke.id] = panel
                    } else {
                        val lp = panel.layoutParams as WindowManager.LayoutParams
                        if (lp.x != x || lp.y != y || lp.width != w || lp.height != h) {
                            lp.x = x
                            lp.y = y
                            lp.width = w
                            lp.height = h
                            wm.updateViewLayout(panel, lp)
                        }
                    }
                } catch (e: Exception) {
                    // 窓を出せない端末では、通常の目隠し（塗りつぶし）のまま動く
                    e.printStackTrace()
                    continue
                }
                panel.offsetX = left.toFloat()
                panel.offsetY = top.toFloat()
                panel.invalidate()
                stillNeeded.add(stroke.id)
            }
        }

        val iterator = privatePanels.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in stillNeeded) {
                try {
                    wm.removeViewImmediate(entry.value)
                } catch (_: Exception) {
                }
                iterator.remove()
            }
        }
    }

    private fun removeAllPrivatePanels() {
        if (privatePanels.isEmpty()) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        for (panel in privatePanels.values) {
            try {
                wm?.removeViewImmediate(panel)
            } catch (_: Exception) {
            }
        }
        privatePanels.clear()
    }

    private fun drawLaserPointerOverlay(canvas: Canvas) {
        val now = System.currentTimeMillis()
        if (laserTrailPoints.size >= 2) {
            for (i in 1 until laserTrailPoints.size) {
                val p1 = laserTrailPoints[i - 1]
                val p2 = laserTrailPoints[i]
                val age = (now - p2.timestamp).coerceAtLeast(0L)
                val ratio = (1f - age.toFloat() / LASER_TRAIL_LIFETIME_MS.toFloat()).coerceIn(0f, 1f)
                if (ratio > 0.02f) {
                    laserGlowPaint.strokeWidth = 14f * ratio
                    laserGlowPaint.alpha = (110 * ratio).toInt()
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, laserGlowPaint)

                    laserCorePaint.strokeWidth = 5.5f * ratio
                    laserCorePaint.alpha = (240 * ratio).toInt()
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, laserCorePaint)
                }
            }
        }

        if (isLaserTouching) {
            laserCurrentTip?.let { tip ->
                canvas.drawCircle(tip.x, tip.y, 22f, laserTipHaloPaint)
                canvas.drawCircle(tip.x, tip.y, 11f, laserTipRingPaint)
                canvas.drawCircle(tip.x, tip.y, 6f, laserTipCenterPaint)
            }
        }
    }

    private fun drawMask(canvas: Canvas, stroke: Stroke) {
        if (stroke.id in revealedMaskIds) {
            canvas.drawPath(stroke.path, maskRevealedFillPaint)
            canvas.drawPath(stroke.path, maskOutlinePaint)
            // 開いている間だけ、右上に削除用の×を出す
            val box = stroke.shapeBox()
            val r = MASK_BADGE_RADIUS
            canvas.drawCircle(box.right, box.top, r, maskBadgePaint)
            val d = r * 0.4f
            canvas.drawLine(box.right - d, box.top - d, box.right + d, box.top + d, maskBadgeCrossPaint)
            canvas.drawLine(box.right + d, box.top - d, box.right - d, box.top + d, maskBadgeCrossPaint)
        } else {
            canvas.drawPath(stroke.path, maskFillPaint)
        }
    }

    // --- 目隠し（範囲指定）の操作 ---

    /** すべてのページの目隠しを開く（true）／隠し直す（false） */
    fun setAllCoversRevealed(revealed: Boolean) {
        val doc = document ?: return
        revealedMaskIds.clear()
        if (revealed) {
            doc.pages.forEach { page ->
                page.strokes.forEach { if (it.isMask) revealedMaskIds.add(it.id) }
            }
        }
        invalidateScreenCache()
        invalidate()
    }

    /** 表示中のページの目隠しをすべて削除する。削除した数を返す */
    fun deleteCoversOnVisiblePage(): Int {
        val page = document?.pages?.getOrNull(getCurrentVisiblePageNumber() - 1) ?: return 0
        val count = page.strokes.count { it.isMask }
        if (count == 0) return 0
        page.saveSnapshot()
        page.strokes.removeAll { it.isMask }
        invalidateScreenCache()
        clearLassoSelection()
        invalidate()
        onContentChanged?.invoke()
        return count
    }

    private fun handleCoverTouch(event: MotionEvent, pageIdx: Int, pagePt: NotePoint): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (selectedStrokeIds.isNotEmpty()) clearLassoSelection()
                coverPageIndex = pageIdx
                coverStart = pagePt
                coverCurrent = pagePt
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val (_, pt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = coverPageIndex)
                coverCurrent = pt
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val start = coverStart
                val end = coverCurrent
                coverStart = null
                coverCurrent = null
                val page = document?.pages?.getOrNull(coverPageIndex)
                if (start != null && end != null && page != null) {
                    val w = abs(end.x - start.x)
                    val h = abs(end.y - start.y)
                    if (w < 14f && h < 14f) {
                        handleCoverTap(page, end)
                    } else if (w >= 20f && h >= 20f) {
                        page.saveSnapshot()
                        page.strokes.add(
                            Stroke(
                                points = Stroke.generateShapePoints(start, end, ShapeMode.MASK),
                                color = MASK_COLOR,
                                strokeWidth = 2f,
                                toolType = ToolType.PEN,
                                shapeMode = ShapeMode.MASK
                            )
                        )
                        activePageIndex = coverPageIndex
                        invalidateScreenCache()
                        onContentChanged?.invoke()
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                coverStart = null
                coverCurrent = null
                invalidate()
            }
        }
        return true
    }

    /** 目隠しをタップ: 開け閉め。開いている目隠しの右上×をタップ: 削除 */
    private fun handleCoverTap(page: Page, pt: NotePoint) {
        for (i in page.strokes.indices.reversed()) {
            val stroke = page.strokes[i]
            if (!stroke.isMask) continue
            val box = stroke.shapeBox()
            val isRevealed = stroke.id in revealedMaskIds
            if (isRevealed &&
                hypot((pt.x - box.right).toDouble(), (pt.y - box.top).toDouble()) <= MASK_BADGE_RADIUS + 14f
            ) {
                page.saveSnapshot()
                page.strokes.removeAt(i)
                revealedMaskIds.remove(stroke.id)
                activePageIndex = coverPageIndex
                invalidateScreenCache()
                onContentChanged?.invoke()
                return
            }
            if (box.contains(pt.x, pt.y)) {
                if (isRevealed) revealedMaskIds.remove(stroke.id) else revealedMaskIds.add(stroke.id)
                invalidateScreenCache()
                return
            }
        }
    }

    /** 選択中のものが「回転していないXY軸ひとつ」のとき、そのStrokeを返す（原点ハンドル用） */
    private fun singleSelectedAxes(page: Page?): Stroke? {
        if (page == null || selectedStrokeIds.size != 1) return null
        val stroke = page.strokes.firstOrNull { it.id in selectedStrokeIds } ?: return null
        return if (stroke.shapeMode == ShapeMode.XY_AXES_SHAPE && abs(stroke.rotationDeg) < 0.5f) stroke else null
    }

    private fun drawSingleStroke(canvas: Canvas, stroke: Stroke) {
        applyPaintSettings(
            paint = strokePaint,
            tool = stroke.toolType,
            color = stroke.color,
            width = stroke.strokeWidth,
            isDashed = stroke.isDashed
        )
        canvas.drawPath(stroke.path, strokePaint)
    }

    private fun applyPaintSettings(
        paint: Paint,
        tool: ToolType,
        color: Int,
        width: Float,
        isDashed: Boolean
    ) {
        paint.strokeWidth = width
        when (tool) {
            ToolType.HIGHLIGHTER -> {
                paint.color = color
                paint.alpha = 85
                paint.strokeCap = Paint.Cap.SQUARE
                paint.pathEffect = null
            }
            else -> {
                paint.color = color
                paint.alpha = 255
                paint.strokeCap = Paint.Cap.ROUND
                paint.pathEffect = if (isDashed) {
                    val dashOn = max(10f, width * 3.0f)
                    val dashOff = max(8f, width * 2.2f)
                    DashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
                } else {
                    null
                }
            }
        }
    }

    private fun drawPageHeaderAndTemplate(
        canvas: Canvas,
        doc: LectureNoteDocument,
        pageIdx: Int,
        totalPages: Int
    ) {
        val topMargin = 110f
        val meta = doc.metadata

        val headerLeft = "生徒: ${meta.studentName}  |  教科: ${meta.subject}"
        val headerRight = "${meta.dateStr} (${meta.dayOfWeek})   Page ${pageIdx + 1} / $totalPages"
        canvas.drawText(headerLeft, 48f, 64f, headerTextPaint)
        val rightWidth = headerTextPaint.measureText(headerRight)
        canvas.drawText(headerRight, PAGE_WIDTH - rightWidth - 48f, 64f, headerTextPaint)
        canvas.drawLine(40f, 84f, PAGE_WIDTH - 40f, 84f, cornellGuidePaint)

        when (doc.template) {
            BackgroundTemplate.RULED_7MM -> {
                val spacing = 58f
                var y = topMargin + spacing
                while (y < PAGE_HEIGHT - 48f) {
                    canvas.drawLine(40f, y, PAGE_WIDTH - 40f, y, gridPaint)
                    y += spacing
                }
            }
            BackgroundTemplate.RULED_6MM -> {
                val spacing = 48f
                var y = topMargin + spacing
                while (y < PAGE_HEIGHT - 48f) {
                    canvas.drawLine(40f, y, PAGE_WIDTH - 40f, y, gridPaint)
                    y += spacing
                }
            }
            BackgroundTemplate.GRID_5MM -> {
                val gridSize = 42f
                var x = 40f
                while (x <= PAGE_WIDTH - 40f) {
                    canvas.drawLine(x, topMargin, x, PAGE_HEIGHT - 40f, gridPaint)
                    x += gridSize
                }
                var y = topMargin
                while (y <= PAGE_HEIGHT - 40f) {
                    canvas.drawLine(40f, y, PAGE_WIDTH - 40f, y, gridPaint)
                    y += gridSize
                }
            }
            BackgroundTemplate.CORNELL -> {
                val cueX = PAGE_WIDTH * 0.28f
                val summaryY = PAGE_HEIGHT * 0.82f
                canvas.drawLine(cueX, topMargin, cueX, summaryY, cornellGuidePaint)
                canvas.drawLine(40f, summaryY, PAGE_WIDTH - 40f, summaryY, cornellGuidePaint)

                canvas.drawText("【ポイント・疑問】", 52f, topMargin + 32f, headerTextPaint)
                canvas.drawText("【授業・指導ノート】", cueX + 28f, topMargin + 32f, headerTextPaint)
                canvas.drawText("【本日のまとめ・宿題】", 52f, summaryY + 42f, headerTextPaint)

                val spacing = 54f
                var y = topMargin + 56f + spacing
                while (y < summaryY - 16f) {
                    canvas.drawLine(cueX, y, PAGE_WIDTH - 40f, y, gridPaint)
                    y += spacing
                }
            }
            BackgroundTemplate.BLANK -> {}
        }
    }

    // --- スタイラスのホバー＆腹ボタン検知 ---
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isBarrelPressed = isStylusBarrelButtonActive(event)
        if (isBarrelPressed && !wasBarrelButtonDown) {
            wasBarrelButtonDown = true
            onStylusBarrelButtonPressed?.invoke()
            return true
        } else if (!isBarrelPressed && wasBarrelButtonDown) {
            wasBarrelButtonDown = false
        }
        return super.onGenericMotionEvent(event)
    }

    private fun isStylusBarrelButtonActive(event: MotionEvent): Boolean {
        val bs = event.buttonState
        val isStylusTool = event.pointerCount > 0 && (
            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
            )
        if ((bs and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
            (bs and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 ||
            (bs and MotionEvent.BUTTON_TERTIARY) != 0
        ) {
            return true
        }
        if (isStylusTool && bs != 0 && bs != MotionEvent.BUTTON_PRIMARY) {
            return true
        }
        return false
    }

    // --- タッチ＆ペン入力処理 ---
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val primaryToolType = event.getToolType(0)
        val isStylusEvent = primaryToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            primaryToolType == MotionEvent.TOOL_TYPE_ERASER

        val barrelPressed = isStylusBarrelButtonActive(event)
        if (barrelPressed && !wasBarrelButtonDown) {
            wasBarrelButtonDown = true
            onStylusBarrelButtonPressed?.invoke()
        } else if (!barrelPressed && event.actionMasked == MotionEvent.ACTION_UP) {
            wasBarrelButtonDown = false
        }

        if (!isStylusEvent) {
            // 投げ縄モード中でクリップボードがある場合、1本指タップでペースト先ターゲット位置を指定可能
            if (currentTool == ToolType.LASSO && clipboardStrokes.isNotEmpty() &&
                event.pointerCount == 1 && event.actionMasked == MotionEvent.ACTION_DOWN
            ) {
                val (pIdx, pt) = screenToPageCoordinates(event.x, event.y)
                val bounds = selectionBounds
                val insideExisting = pIdx == lassoPageIndex && bounds != null && bounds.contains(pt.x, pt.y)
                if (!insideExisting) {
                    selectedStrokeIds.clear()
                    selectionBounds = null
                    pendingPastePageIndex = pIdx
                    pendingPastePoint = pt
                    notifySelectionState()
                    invalidate()
                }
            }

            if (event.pointerCount >= 2 || isStylusOnlyMode) {
                return handleFingerMultiTouchGesture(event)
            }
        }

        isMultiTouchPanningOrZooming = false
        val (pageIdx, pagePt) = screenToPageCoordinates(event.x, event.y)

        val isErasing = currentTool == ToolType.STROKE_ERASER ||
            primaryToolType == MotionEvent.TOOL_TYPE_ERASER ||
            barrelPressed

        if (isErasing) {
            return handleEraserTouch(event)
        }

        if (currentTool == ToolType.COVER) {
            return handleCoverTouch(event, pageIdx, pagePt)
        }

        if (currentTool == ToolType.LASER_POINTER) {
            return handleLaserPointerTouch(event, pageIdx, pagePt)
        }

        if (currentTool == ToolType.LASSO) {
            return handleLassoTouch(event, pageIdx, pagePt)
        }

        return handleDrawingTouch(event, pageIdx, pagePt)
    }

    private fun handleLaserPointerTouch(event: MotionEvent, pageIdx: Int, pagePt: NotePoint): Boolean {
        val now = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                laserPageIndex = pageIdx
                isLaserTouching = true
                laserCurrentTip = pagePt
                laserTrailPoints.add(TimedLaserPoint(pagePt.x, pagePt.y, now))
                mainHandler.removeCallbacks(laserFadeRunnable)
                mainHandler.post(laserFadeRunnable)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    val (_, histPt) = screenToPageCoordinates(
                        event.getHistoricalX(h),
                        event.getHistoricalY(h),
                        fixedPageIndex = laserPageIndex
                    )
                    laserTrailPoints.add(TimedLaserPoint(histPt.x, histPt.y, now))
                }
                val (_, currPt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = laserPageIndex)
                laserCurrentTip = currPt
                laserTrailPoints.add(TimedLaserPoint(currPt.x, currPt.y, now))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isLaserTouching = false
                laserCurrentTip = null
                mainHandler.removeCallbacks(laserFadeRunnable)
                mainHandler.post(laserFadeRunnable)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun handleFingerMultiTouchGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maxTouchPointers = 1
                multiTouchDownTime = System.currentTimeMillis()
                maxFingerTravelPx = 0f
                initialFingerPositions.clear()
                initialFingerPositions[event.getPointerId(0)] = NotePoint(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxTouchPointers = max(maxTouchPointers, event.pointerCount)
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (!initialFingerPositions.containsKey(pid)) {
                        initialFingerPositions[pid] = NotePoint(event.getX(i), event.getY(i))
                    }
                }
                if (event.pointerCount == 2) {
                    prevFocalX = (event.getX(0) + event.getX(1)) / 2f
                    prevFocalY = (event.getY(0) + event.getY(1)) / 2f
                    prevPinchSpan = hypot(
                        (event.getX(1) - event.getX(0)).toDouble(),
                        (event.getY(1) - event.getY(0)).toDouble()
                    ).toFloat()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val startPt = initialFingerPositions[pid]
                    if (startPt != null) {
                        val d = hypot(
                            (event.getX(i) - startPt.x).toDouble(),
                            (event.getY(i) - startPt.y).toDouble()
                        ).toFloat()
                        maxFingerTravelPx = max(maxFingerTravelPx, d)
                    }
                }

                if (event.pointerCount == 2 && maxFingerTravelPx > 14f) {
                    isMultiTouchPanningOrZooming = true
                    invalidateScreenCache()

                    val currFocalX = (event.getX(0) + event.getX(1)) / 2f
                    val currFocalY = (event.getY(0) + event.getY(1)) / 2f
                    val currSpan = hypot(
                        (event.getX(1) - event.getX(0)).toDouble(),
                        (event.getY(1) - event.getY(0)).toDouble()
                    ).toFloat()

                    val dx = currFocalX - prevFocalX
                    val dy = currFocalY - prevFocalY

                    if (!isZoomLocked && prevPinchSpan > 20f && currSpan > 20f) {
                        val zoomDelta = currSpan / prevPinchSpan
                        val newScale = (scaleFactor * zoomDelta).coerceIn(minScale, maxScale)
                        val actualZoom = newScale / scaleFactor
                        panX = currFocalX - (currFocalX - panX) * actualZoom + dx
                        panY = currFocalY - (currFocalY - panY) * actualZoom + dy
                        scaleFactor = newScale
                    } else {
                        panX += dx
                        panY += dy
                    }

                    clampPanBounds()
                    prevFocalX = currFocalX
                    prevFocalY = currFocalY
                    prevPinchSpan = currSpan
                    invalidate()
                    onViewportChanged?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMultiTouchPanningOrZooming) {
                    isMultiTouchPanningOrZooming = false
                    invalidate()
                }
                maxTouchPointers = 0
                initialFingerPositions.clear()
                return true
            }
        }
        return true
    }

    private fun handleDrawingTouch(event: MotionEvent, pageIdx: Int, pagePt: NotePoint): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (selectedStrokeIds.isNotEmpty()) {
                    clearLassoSelection()
                }
                activePageIndex = pageIdx
                currentPoints.clear()
                currentPoints.add(pagePt)
                snappedShapeMode = when (currentTool) {
                    ToolType.LINE_SHAPE -> currentShapeMode
                    ToolType.XY_AXES -> ShapeMode.XY_AXES_SHAPE
                    else -> null
                }
                lastMovePoint = pagePt
                currentPath.reset()
                currentPath.moveTo(pagePt.x, pagePt.y)

                if (currentTool == ToolType.PEN || currentTool == ToolType.HIGHLIGHTER) {
                    mainHandler.removeCallbacks(straightenRunnable)
                    mainHandler.postDelayed(straightenRunnable, HOLD_TO_STRAIGHTEN_MS)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (snappedShapeMode != null) {
                    val (_, rawCurrPt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = activePageIndex)
                    // 直線または矢印の場合は5度刻み（0°, 5°, 10°, 15°...）で張り付くスナップ補正を適用
                    val currPt = if (snappedShapeMode == ShapeMode.LINE || snappedShapeMode == ShapeMode.ARROW) {
                        Stroke.snapPointTo5Degrees(currentPoints.first(), rawCurrPt)
                    } else {
                        rawCurrPt
                    }
                    if (currentPoints.size >= 2) {
                        currentPoints[currentPoints.lastIndex] = currPt
                    } else {
                        currentPoints.add(currPt)
                    }
                    rebuildCurrentPreviewPath()
                    invalidate()
                    return true
                }

                for (h in 0 until event.historySize) {
                    val (_, histPt) = screenToPageCoordinates(
                        event.getHistoricalX(h),
                        event.getHistoricalY(h),
                        fixedPageIndex = activePageIndex
                    )
                    appendFreehandPointIfMoved(histPt)
                }
                val (_, currPt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = activePageIndex)
                appendFreehandPointIfMoved(currPt)

                val prev = lastMovePoint
                if (prev == null || hypot((currPt.x - prev.x).toDouble(), (currPt.y - prev.y).toDouble()) > 6.5) {
                    lastMovePoint = currPt
                    mainHandler.removeCallbacks(straightenRunnable)
                    mainHandler.postDelayed(straightenRunnable, HOLD_TO_STRAIGHTEN_MS)
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(straightenRunnable)
                val page = document?.pages?.getOrNull(activePageIndex)
                if (page != null && currentPoints.isNotEmpty()) {
                    page.saveSnapshot()
                    val isAxes = snappedShapeMode == ShapeMode.XY_AXES_SHAPE
                    val finalPoints = if (snappedShapeMode != null && currentPoints.size >= 2) {
                        Stroke.generateShapePoints(
                            currentPoints.first(),
                            currentPoints.last(),
                            snappedShapeMode!!,
                            if (isAxes) axesOriginFx else 0.5f,
                            if (isAxes) axesOriginFy else 0.5f
                        )
                    } else {
                        currentPoints.toList()
                    }
                    val newStroke = Stroke(
                        points = finalPoints,
                        color = currentColor,
                        strokeWidth = currentStrokeWidth,
                        toolType = if (currentTool == ToolType.HIGHLIGHTER) ToolType.HIGHLIGHTER else ToolType.PEN,
                        shapeMode = snappedShapeMode,
                        isDashed = isDashedLine && currentTool != ToolType.HIGHLIGHTER,
                        originFx = if (isAxes) axesOriginFx else 0.5f,
                        originFy = if (isAxes) axesOriginFy else 0.5f
                    )
                    page.strokes.add(newStroke)
                    appendStrokeToScreenCache(activePageIndex, newStroke)
                }
                currentPoints.clear()
                currentPath.reset()
                snappedShapeMode = null
                invalidate()
                onContentChanged?.invoke()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(straightenRunnable)
                currentPoints.clear()
                currentPath.reset()
                snappedShapeMode = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun appendFreehandPointIfMoved(pt: NotePoint) {
        if (currentPoints.isEmpty()) {
            currentPoints.add(pt)
            currentPath.moveTo(pt.x, pt.y)
            return
        }
        val prev = currentPoints.last()
        val dx = pt.x - prev.x
        val dy = pt.y - prev.y
        if (dx * dx + dy * dy < MIN_POINT_DIST * MIN_POINT_DIST) {
            return
        }
        val midX = (prev.x + pt.x) / 2f
        val midY = (prev.y + pt.y) / 2f
        currentPath.quadTo(prev.x, prev.y, midX, midY)
        currentPoints.add(pt)
    }

    private fun rebuildCurrentPreviewPath() {
        currentPath.reset()
        if (currentPoints.isEmpty()) return
        val built = Stroke.buildStrokePath(currentPoints, snappedShapeMode, 0f, axesOriginFx, axesOriginFy)
        currentPath.set(built)
    }

    private fun handleEraserTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hasSavedUndoForCurrentAction = false
                val (pIdx, pt) = screenToPageCoordinates(event.x, event.y)
                isEraserTouching = true
                eraserPageIndex = pIdx
                lastEraserPoint = pt
                eraseSegmentOnPage(pIdx, pt.x, pt.y, pt.x, pt.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    val (pIdx, pt) = screenToPageCoordinates(
                        event.getHistoricalX(h),
                        event.getHistoricalY(h),
                        fixedPageIndex = eraserPageIndex
                    )
                    val prev = lastEraserPoint ?: pt
                    eraseSegmentOnPage(pIdx, prev.x, prev.y, pt.x, pt.y)
                    lastEraserPoint = pt
                }
                val (pIdx, pt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = eraserPageIndex)
                val prev = lastEraserPoint ?: pt
                eraseSegmentOnPage(pIdx, prev.x, prev.y, pt.x, pt.y)
                lastEraserPoint = pt
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isEraserTouching = false
                lastEraserPoint = null
                invalidate()
                if (hasSavedUndoForCurrentAction) {
                    onContentChanged?.invoke()
                }
                hasSavedUndoForCurrentAction = false
                return true
            }
        }
        return true
    }

    private fun eraseSegmentOnPage(pageIdx: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        val page = document?.pages?.getOrNull(pageIdx) ?: return
        if (page.strokes.isEmpty()) return

        if (eraserMode == EraserMode.STROKE) {
            val hitExists = page.strokes.any { !it.isMask && it.intersects(x2, y2, threshold = eraserRadius) }
            if (hitExists) {
                if (!hasSavedUndoForCurrentAction) {
                    page.saveSnapshot()
                    hasSavedUndoForCurrentAction = true
                }
                page.strokes.removeAll { !it.isMask && it.intersects(x2, y2, threshold = eraserRadius) }
                activePageIndex = pageIdx
                invalidateScreenCache()
            }
        } else {
            var modifiedAny = false
            val newStrokeList = ArrayList<Stroke>(page.strokes.size + 4)

            for (stroke in page.strokes) {
                val splitResult = stroke.erasePartially(x1, y1, x2, y2, eraserRadius)
                if (splitResult == null) {
                    newStrokeList.add(stroke)
                } else {
                    if (!modifiedAny && !hasSavedUndoForCurrentAction) {
                        page.saveSnapshot()
                        hasSavedUndoForCurrentAction = true
                    }
                    modifiedAny = true
                    newStrokeList.addAll(splitResult)
                }
            }

            if (modifiedAny) {
                page.strokes.clear()
                page.strokes.addAll(newStrokeList)
                activePageIndex = pageIdx
                invalidateScreenCache()
            }
        }
    }

    /**
     * 投げ縄（Lasso）タッチ処理:
     * - 上部緑ハンドルをドラッグ: 選択オブジェクトの回転 (ROTATE)
     * - 四隅青白ハンドルをドラッグ: 選択オブジェクトの拡大縮小 (SCALE)
     * - 選択枠内をドラッグ: 選択オブジェクトの移動 (MOVE)
     * - 選択枠外をタップ（移動量が小さい場合）: ペースト先の位置(pendingPastePoint)を更新（いきなりペーストはしない）
     * - 選択枠外をドラッグ: 新しい投げ縄選択を描画 (DRAW_LASSO)
     */
    private fun handleLassoTouch(event: MotionEvent, pageIdx: Int, pagePt: NotePoint): Boolean {
        val page = document?.pages?.getOrNull(pageIdx) ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val bounds = selectionBounds
                if (pageIdx == lassoPageIndex && selectedStrokeIds.isNotEmpty() && bounds != null) {
                    // 0. XY軸の原点ハンドルのヒット判定
                    val axes = singleSelectedAxes(page)
                    if (axes != null) {
                        val o = axes.axesOriginPoint()
                        if (hypot((pagePt.x - o.x).toDouble(), (pagePt.y - o.y).toDouble()) <= HANDLE_HIT_RADIUS) {
                            page.saveSnapshot()
                            lassoDragMode = LassoDragMode.AXES_ORIGIN
                            invalidate()
                            return true
                        }
                    }

                    // 1. 回転ハンドルのヒット判定
                    val rotX = bounds.centerX()
                    val rotY = bounds.top - ROTATE_HANDLE_OFFSET
                    if (hypot((pagePt.x - rotX).toDouble(), (pagePt.y - rotY).toDouble()) <= HANDLE_HIT_RADIUS) {
                        page.saveSnapshot()
                        lassoDragMode = LassoDragMode.ROTATE
                        transformPivot = NotePoint(bounds.centerX(), bounds.centerY())
                        initialRotateAngleRad = atan2(
                            (pagePt.y - transformPivot.y).toDouble(),
                            (pagePt.x - transformPivot.x).toDouble()
                        ).toFloat()
                        originalSelectedStrokes = page.strokes.filter { it.id in selectedStrokeIds }.associateBy { it.id }
                        invalidate()
                        return true
                    }

                    // 2. 四隅の拡大縮小ハンドルのヒット判定
                    val corners = listOf(
                        bounds.left to bounds.top,
                        bounds.right to bounds.top,
                        bounds.left to bounds.bottom,
                        bounds.right to bounds.bottom
                    )
                    val hitCorner = corners.any { (cx, cy) ->
                        hypot((pagePt.x - cx).toDouble(), (pagePt.y - cy).toDouble()) <= HANDLE_HIT_RADIUS
                    }
                    if (hitCorner) {
                        page.saveSnapshot()
                        lassoDragMode = LassoDragMode.SCALE
                        transformPivot = NotePoint(bounds.centerX(), bounds.centerY())
                        initialScaleDist = hypot(
                            (pagePt.x - transformPivot.x).toDouble(),
                            (pagePt.y - transformPivot.y).toDouble()
                        ).toFloat().coerceAtLeast(16f)
                        originalSelectedStrokes = page.strokes.filter { it.id in selectedStrokeIds }.associateBy { it.id }
                        invalidate()
                        return true
                    }

                    // 3. 選択ボックス内部の移動ドラッグ判定
                    if (bounds.contains(pagePt.x, pagePt.y)) {
                        page.saveSnapshot()
                        lassoDragMode = LassoDragMode.MOVE
                        lastDragDocPoint = pagePt
                        invalidate()
                        return true
                    }
                }

                // 4. 選択枠外：新規投げ縄の描画またはペースト先位置のタップ指定
                selectedStrokeIds.clear()
                selectionBounds = null
                lassoPageIndex = pageIdx
                lassoDragMode = LassoDragMode.DRAW_LASSO
                lassoPoints.clear()
                lassoPoints.add(pagePt)
                lassoPath.reset()
                lassoPath.moveTo(pagePt.x, pagePt.y)
                notifySelectionState()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val (_, currPt) = screenToPageCoordinates(event.x, event.y, fixedPageIndex = lassoPageIndex)
                when (lassoDragMode) {
                    LassoDragMode.MOVE -> {
                        val prev = lastDragDocPoint ?: currPt
                        val dx = currPt.x - prev.x
                        val dy = currPt.y - prev.y
                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            for (i in page.strokes.indices) {
                                val s = page.strokes[i]
                                if (s.id in selectedStrokeIds) {
                                    page.strokes[i] = s.offsetBy(dx, dy, newId = s.id)
                                }
                            }
                            invalidateScreenCache()
                            recomputeSelectionBounds(page)
                            lastDragDocPoint = currPt
                            invalidate()
                        }
                    }

                    LassoDragMode.SCALE -> {
                        val currDist = hypot(
                            (currPt.x - transformPivot.x).toDouble(),
                            (currPt.y - transformPivot.y).toDouble()
                        ).toFloat().coerceAtLeast(8f)
                        val scaleRatio = (currDist / initialScaleDist).coerceIn(0.2f, 5.0f)

                        for (i in page.strokes.indices) {
                            val s = page.strokes[i]
                            val orig = originalSelectedStrokes[s.id]
                            if (orig != null) {
                                page.strokes[i] = orig.transformAroundPivot(
                                    pivotX = transformPivot.x,
                                    pivotY = transformPivot.y,
                                    scale = scaleRatio,
                                    rotationRad = 0f,
                                    keepId = orig.id
                                )
                            }
                        }
                        invalidateScreenCache()
                        recomputeSelectionBounds(page)
                        invalidate()
                    }

                    LassoDragMode.ROTATE -> {
                        val currAngle = atan2(
                            (currPt.y - transformPivot.y).toDouble(),
                            (currPt.x - transformPivot.x).toDouble()
                        ).toFloat()
                        var deltaRad = currAngle - initialRotateAngleRad
                        // 回転も5度刻みで気持ちよくスナップさせる
                        val deltaDeg = Math.toDegrees(deltaRad.toDouble())
                        val snappedDeg = (deltaDeg / 5.0).roundToInt() * 5.0
                        deltaRad = Math.toRadians(snappedDeg).toFloat()

                        for (i in page.strokes.indices) {
                            val s = page.strokes[i]
                            val orig = originalSelectedStrokes[s.id]
                            if (orig != null) {
                                page.strokes[i] = orig.transformAroundPivot(
                                    pivotX = transformPivot.x,
                                    pivotY = transformPivot.y,
                                    scale = 1.0f,
                                    rotationRad = deltaRad,
                                    keepId = orig.id
                                )
                            }
                        }
                        invalidateScreenCache()
                        recomputeSelectionBounds(page)
                        invalidate()
                    }

                    LassoDragMode.AXES_ORIGIN -> {
                        val idx = page.strokes.indexOfFirst { it.id in selectedStrokeIds }
                        if (idx >= 0) {
                            val s = page.strokes[idx]
                            val box = s.shapeBox()
                            if (box.width() > 1f && box.height() > 1f) {
                                page.strokes[idx] = s.withAxesOrigin(
                                    (currPt.x - box.left) / box.width(),
                                    (currPt.y - box.top) / box.height()
                                )
                                invalidateScreenCache()
                                invalidate()
                            }
                        }
                    }

                    LassoDragMode.DRAW_LASSO -> {
                        lassoPoints.add(currPt)
                        lassoPath.lineTo(currPt.x, currPt.y)
                        invalidate()
                    }

                    LassoDragMode.NONE -> {}
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                when (lassoDragMode) {
                    LassoDragMode.MOVE, LassoDragMode.SCALE, LassoDragMode.ROTATE, LassoDragMode.AXES_ORIGIN -> {
                        lassoDragMode = LassoDragMode.NONE
                        lastDragDocPoint = null
                        originalSelectedStrokes = emptyMap()
                        invalidateScreenCache()
                        invalidate()
                        onContentChanged?.invoke()
                    }

                    LassoDragMode.DRAW_LASSO -> {
                        val travel = calculatePolylineLength(lassoPoints)
                        if (travel < 24f) {
                            // 別の場所をチョンとタップした場合は、いきなりペーストせず「ペースト先ターゲット座標」として記録
                            val tapPt = lassoPoints.lastOrNull() ?: pagePt
                            lassoPoints.clear()
                            lassoPath.reset()
                            lassoDragMode = LassoDragMode.NONE
                            if (clipboardStrokes.isNotEmpty()) {
                                pendingPastePageIndex = lassoPageIndex
                                pendingPastePoint = tapPt
                            }
                            notifySelectionState()
                            invalidate()
                        } else if (lassoPoints.size >= 3) {
                            lassoPath.close()
                            selectStrokesInsideLasso(page, lassoPoints)
                            lassoPoints.clear()
                            lassoPath.reset()
                            lassoDragMode = LassoDragMode.NONE
                            invalidate()
                        } else {
                            clearLassoSelection()
                        }
                    }

                    LassoDragMode.NONE -> {}
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                lassoDragMode = LassoDragMode.NONE
                originalSelectedStrokes = emptyMap()
                lassoPoints.clear()
                lassoPath.reset()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun selectStrokesInsideLasso(page: Page, polygon: List<NotePoint>) {
        selectedStrokeIds.clear()
        for (stroke in page.strokes) {
            if (stroke.points.isEmpty()) continue
            val insideCount = stroke.points.count { pt -> isPointInPolygon(pt, polygon) }
            if (insideCount * 2 >= stroke.points.size || insideCount >= 3) {
                selectedStrokeIds.add(stroke.id)
            }
        }
        recomputeSelectionBounds(page)
        notifySelectionState()
    }

    private fun recomputeSelectionBounds(page: Page) {
        val selected = page.strokes.filter { it.id in selectedStrokeIds }
        if (selected.isEmpty()) {
            selectionBounds = null
            return
        }
        val rect = RectF()
        selected.forEachIndexed { index, stroke ->
            if (index == 0) rect.set(stroke.bounds) else rect.union(stroke.bounds)
        }
        rect.inset(-14f, -14f)
        selectionBounds = rect
    }

    private fun isPointInPolygon(pt: NotePoint, polygon: List<NotePoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersect = ((pi.y > pt.y) != (pj.y > pt.y)) &&
                (pt.x < (pj.x - pi.x) * (pt.y - pi.y) / ((pj.y - pi.y) + 0.00001f) + pi.x)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun screenToPageCoordinates(
        screenX: Float,
        screenY: Float,
        fixedPageIndex: Int? = null
    ): Pair<Int, NotePoint> {
        val doc = document
        val pageCount = doc?.pages?.size ?: 1
        val docX = (screenX - panX) / scaleFactor
        val docY = (screenY - panY) / scaleFactor

        val pageIdx = fixedPageIndex ?: (docY / (PAGE_HEIGHT + PAGE_GAP)).toInt().coerceIn(0, pageCount - 1)
        val pageTopY = pageIdx * (PAGE_HEIGHT + PAGE_GAP)
        val localX = docX.coerceIn(0f, PAGE_WIDTH)
        val localY = (docY - pageTopY).coerceIn(0f, PAGE_HEIGHT)
        return pageIdx to NotePoint(localX, localY)
    }

    private fun clampPanBounds() {
        val doc = document ?: return
        val totalDocHeight = doc.pages.size * PAGE_HEIGHT + max(0, doc.pages.size - 1) * PAGE_GAP
        val scaledWidth = PAGE_WIDTH * scaleFactor
        val scaledHeight = totalDocHeight * scaleFactor

        if (scaledWidth <= width) {
            panX = (width - scaledWidth) / 2f
        } else {
            val minPanX = width - scaledWidth - 32f
            val maxPanX = 32f
            panX = panX.coerceIn(minPanX, maxPanX)
        }

        val minPanY = min(48f, height - scaledHeight - 120f)
        val maxPanY = 96f
        panY = panY.coerceIn(minPanY, maxPanY)
    }

    private fun calculatePolylineLength(pts: List<NotePoint>): Float {
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot((pts[i].x - pts[i - 1].x).toDouble(), (pts[i].y - pts[i - 1].y).toDouble()).toFloat()
        }
        return total
    }
}
