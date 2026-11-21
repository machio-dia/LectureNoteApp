package com.example.lecturenote.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lecturenote.canvas.LectureCanvasView
import com.example.lecturenote.model.LectureNoteDocument
import com.example.lecturenote.model.Page
import com.example.lecturenote.model.ToolType
import com.example.lecturenote.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.max

// ---------------------------------------------------------------------------
// 授業タイマー（カウントダウン／ストップウォッチ）
// ---------------------------------------------------------------------------

@Stable
class LessonTimerState {
    var isCountdown by mutableStateOf(true)
        private set
    var durationMs by mutableLongStateOf(5 * 60_000L)
        private set
    var isFinished by mutableStateOf(false)
        private set

    private var accumulatedMs by mutableLongStateOf(0L)
    private var startedAt by mutableLongStateOf(0L)
    private var nowTick by mutableLongStateOf(0L)

    val isRunning: Boolean get() = startedAt != 0L

    /** 一度でも動かした（または一時停止中）かどうか。バー上に時間を出すかの判定に使う */
    val isActive: Boolean get() = isRunning || accumulatedMs > 0L || isFinished

    private val elapsedMs: Long
        get() = accumulatedMs + if (isRunning) (nowTick - startedAt).coerceAtLeast(0L) else 0L

    val displayMs: Long
        get() = if (isCountdown) (durationMs - elapsedMs).coerceAtLeast(0L) else elapsedMs

    val displayText: String
        get() {
            // カウントダウンは切り上げ表示（残り0.4秒を 00:00 と見せない）
            val totalSec = if (isCountdown) (displayMs + 999L) / 1000L else displayMs / 1000L
            return "%02d:%02d".format(totalSec / 60L, totalSec % 60L)
        }

    fun start() {
        if (isFinished || (isCountdown && displayMs <= 0L)) reset()
        val now = SystemClock.elapsedRealtime()
        nowTick = now
        startedAt = now
    }

    fun pause() {
        if (!isRunning) return
        nowTick = SystemClock.elapsedRealtime()
        accumulatedMs = elapsedMs
        startedAt = 0L
    }

    fun reset() {
        startedAt = 0L
        accumulatedMs = 0L
        isFinished = false
    }

    fun selectCountdown(minutes: Int) {
        reset()
        isCountdown = true
        durationMs = minutes * 60_000L
    }

    fun selectStopwatch() {
        reset()
        isCountdown = false
    }

    fun adjustMinutes(delta: Int) {
        if (!isCountdown) return
        durationMs = (durationMs + delta * 60_000L).coerceIn(60_000L, 99 * 60_000L)
    }

    fun dismissFinished() {
        isFinished = false
    }

    /** 画面更新用。カウントダウンが 0 になったら true を返す */
    fun tick(): Boolean {
        if (!isRunning) return false
        nowTick = SystemClock.elapsedRealtime()
        if (isCountdown && displayMs <= 0L) {
            accumulatedMs = durationMs
            startedAt = 0L
            isFinished = true
            return true
        }
        return false
    }
}

/** タイマーを動かし続ける（ホーム画面に戻っても止まらないよう、アプリ最上位で呼ぶ） */
@Composable
fun LessonTimerDriver(timer: LessonTimerState) {
    LaunchedEffect(timer.isRunning) {
        while (timer.isRunning) {
            if (timer.tick()) {
                playTimerFinishedSound()
            }
            delay(200L)
        }
    }
}

private suspend fun playTimerFinishedSound() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        try {
            repeat(3) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                delay(320L)
            }
        } finally {
            tone.release()
        }
    } catch (_: Exception) {
        // 音が出せない端末でも、画面のお知らせは表示されるので無視してよい
    }
}

@Composable
fun TimerChip(timer: LessonTimerState, showLabel: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val urgent = timer.isCountdown && timer.isActive && timer.displayMs <= 10_000L
    val fg = when {
        urgent -> AppColors.Danger
        timer.isActive -> AppColors.Accent
        else -> AppColors.Ink
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        urgent -> AppColors.DangerSoft
                        timer.isActive -> AppColors.AccentSoft
                        else -> Color.Transparent
                    }
                )
                .clickable { expanded = true }
                .padding(horizontal = 10.dp)
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = "タイマー", tint = fg, modifier = Modifier.size(24.dp))
            if (timer.isActive) {
                Spacer(Modifier.width(6.dp))
                Text(timer.displayText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
            } else if (showLabel) {
                Spacer(Modifier.width(6.dp))
                Text("タイマー", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (timer.isCountdown) "カウントダウン" else "ストップウォッチ",
                    fontSize = 12.sp,
                    color = AppColors.InkSoft
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val canAdjust = timer.isCountdown && !timer.isActive
                    BarIconButton(Icons.Rounded.Remove, "1分減らす", { timer.adjustMinutes(-1) }, enabled = canAdjust)
                    Text(
                        timer.displayText,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (urgent) AppColors.Danger else AppColors.Ink,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    BarIconButton(Icons.Rounded.Add, "1分増やす", { timer.adjustMinutes(1) }, enabled = canAdjust)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10, 15).forEach { min ->
                        val selected = timer.isCountdown && timer.durationMs == min * 60_000L
                        TimerPresetChip("${min}分", selected) { timer.selectCountdown(min) }
                    }
                }
                TimerPresetChip("ストップウォッチ（時間を計る）", !timer.isCountdown, fill = true) {
                    timer.selectStopwatch()
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { timer.reset() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("リセット", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            if (timer.isRunning) {
                                timer.pause()
                            } else {
                                timer.start()
                                expanded = false
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                    ) {
                        Icon(
                            if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (timer.isRunning) "一時停止" else "スタート", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerPresetChip(label: String, selected: Boolean, fill: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AppColors.AccentSoft else AppColors.SurfaceMuted,
        border = if (selected) BorderStroke(1.5.dp, AppColors.Accent) else null,
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AppColors.AccentDeep else AppColors.Ink
            )
        }
    }
}

/** カウントダウン終了のお知らせ（キャンバス上部に表示） */
@Composable
fun TimerFinishedBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Danger,
        shadowElevation = 10.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("時間です！", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("タップして閉じる", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 全面の目隠し（ノート全体をワンタップで隠す）
// ---------------------------------------------------------------------------

/** ノート表示部分をまるごと覆う。中央のボタンで外す */
@Composable
fun FullCover(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.CoverSheet)
            // 下にあるノートへタッチが抜けないようにする
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(
                Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp)
            )
            Text("ノートを隠しています", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            Surface(onClick = onClose, shape = RoundedCornerShape(16.dp), color = Color.White) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 28.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("目隠しを外す", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ページ一覧（サムネイルからジャンプ）
// ---------------------------------------------------------------------------

@Composable
fun PageOverviewDialog(
    document: LectureNoteDocument,
    currentPage: Int,
    onJumpToPage: (pageIndex: Int) -> Unit,
    onAddPage: () -> Unit,
    onDismiss: () -> Unit
) {
    // ページの追加でこのダイアログを描き直すためのカウンタ（pages は通常の MutableList のため）
    var revision by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (currentPage - 1).coerceAtLeast(0)
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(max = 720.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ページ一覧", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                        Text(
                            "全 ${document.pages.size} ページ ・ タップでそのページへ移動",
                            fontSize = 13.sp,
                            color = AppColors.InkSoft
                        )
                    }
                    FilledTonalButton(onClick = {
                        onAddPage()
                        revision++
                    }) {
                        Icon(Icons.Rounded.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("今のページの下に追加")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
                Spacer(Modifier.height(16.dp))

                val pagesSnapshot = remember(revision, document.id) { document.pages.toList() }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    itemsIndexed(pagesSnapshot) { index, page ->
                        val isCurrent = index + 1 == currentPage
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(LectureCanvasView.PAGE_WIDTH / LectureCanvasView.PAGE_HEIGHT)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(
                                        width = if (isCurrent) 3.dp else 1.dp,
                                        color = if (isCurrent) AppColors.Accent else AppColors.LineStrong,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onJumpToPage(index) }
                            ) {
                                PageThumbnail(page = page, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isCurrent) "${index + 1}（表示中）" else "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) AppColors.Accent else AppColors.InkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(page: Page, modifier: Modifier = Modifier) {
    val strokes = remember(page, page.strokes.size) { page.strokes.toList() }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    Canvas(modifier = modifier) {
        val scale = size.width / LectureCanvasView.PAGE_WIDTH
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(scale, scale)
            // マーカー → ペン → 目隠し の順（本体の描画順と同じ）
            for (pass in 0..2) {
                for (stroke in strokes) {
                    val isHl = stroke.toolType == ToolType.HIGHLIGHTER
                    val strokePass = when {
                        stroke.isMask -> 2
                        isHl -> 0
                        else -> 1
                    }
                    if (strokePass != pass) continue
                    if (stroke.isMask) {
                        // 一覧でも隠した内容が見えないよう、塗りつぶして描く
                        paint.style = Paint.Style.FILL
                        paint.color = stroke.color
                        paint.alpha = 255
                        paint.pathEffect = null
                        native.drawPath(stroke.path, paint)
                        paint.style = Paint.Style.STROKE
                        continue
                    }
                    // サムネイルでも線が消えないよう、最低限の太さを確保する
                    paint.strokeWidth = max(stroke.strokeWidth, 1.6f / scale)
                    paint.color = stroke.color
                    paint.alpha = if (isHl) 85 else 255
                    paint.pathEffect = if (stroke.isDashed && !isHl) {
                        DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    } else {
                        null
                    }
                    native.drawPath(stroke.path, paint)
                }
            }
            native.restore()
        }
    }
}
