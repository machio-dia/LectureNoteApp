package com.example.lecturenote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lecturenote.model.EraserMode
import com.example.lecturenote.model.PenColor
import com.example.lecturenote.model.PenPreset
import com.example.lecturenote.model.ShapeMode
import com.example.lecturenote.model.ToolType
import com.example.lecturenote.ui.theme.AppColors
import com.example.lecturenote.viewmodel.NoteUiState

// ---------------------------------------------------------------------------
// 共通パーツ
// ---------------------------------------------------------------------------

/** バー上の 44dp アイコンボタン（ペン先でも押しやすいサイズ） */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color = AppColors.Ink
) {
    val fg = when {
        !enabled -> AppColors.LineStrong
        active -> AppColors.Accent
        else -> tint
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AppColors.AccentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun BarDivider(height: Dp = 28.dp) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(height)
            .background(AppColors.Line)
    )
}

// ---------------------------------------------------------------------------
// ファイルバー：ノート・ページ・表示まわり（細い1段）
// ---------------------------------------------------------------------------

/** ツールバーを置く場所 */
enum class ToolBarPlacement(val label: String) {
    TOP("上"),
    LEFT("左"),
    RIGHT("右")
}

@Composable
fun EditorFileBar(
    uiState: NoteUiState,
    timer: LessonTimerState,
    isCoverVisible: Boolean,
    isRangeCoverActive: Boolean,
    isPrivateCoverMode: Boolean,
    onTogglePrivateCoverMode: () -> Unit,
    isToolBarVisible: Boolean,
    toolBarPlacement: ToolBarPlacement,
    onStartRangeCover: () -> Unit,
    onHome: () -> Unit,
    onEditInfo: () -> Unit,
    onOpenNoteSwitcher: () -> Unit,
    onToggleCover: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenPages: () -> Unit,
    onAddPage: () -> Unit,
    onZoomMinus: () -> Unit,
    onZoomPlus: () -> Unit,
    onSetZoomPercent: (Int) -> Unit,
    onToggleZoomLock: () -> Unit,
    onHideFileBar: () -> Unit,
    onSetToolBarVisible: (Boolean) -> Unit,
    onSetToolBarPlacement: (ToolBarPlacement) -> Unit,
    onChangeTemplate: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleStylusOnly: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearPage: () -> Unit,
    onDeletePage: () -> Unit,
    onDeleteNote: () -> Unit
) {
    val meta = uiState.activeDocument.metadata
    var showMoreMenu by remember { mutableStateOf(false) }
    val small = Modifier.size(40.dp)

    Surface(color = AppColors.Surface, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            // ノート名は必ず読める幅を確保し、残りをボタン類に回す（入りきらない分は横スクロール）
            val actionsMaxWidth = (maxWidth - 40.dp - 170.dp - 80.dp - 8.dp).coerceAtLeast(96.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "ホームへ戻る", onHome, modifier = small)

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEditInfo)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${meta.studentName.ifBlank { "未設定" }} さん",
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(meta.subject.ifBlank { "教科なし" })
                            append(" ・ ")
                            append(meta.dateStr)
                            if (meta.dayOfWeek.isNotBlank()) append("(${meta.dayOfWeek})")
                        },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = AppColors.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = actionsMaxWidth)
                        .horizontalScroll(rememberScrollState())
                ) {
                    BarIconButton(Icons.Rounded.FolderOpen, "ほかのノートを開く", onOpenNoteSwitcher, modifier = small)
                    TimerChip(timer = timer, showLabel = false)
                    CoverMenuButton(
                        isSheetVisible = isCoverVisible,
                        isRangeCoverActive = isRangeCoverActive,
                        isPrivateCoverMode = isPrivateCoverMode,
                        onToggleSheet = onToggleCover,
                        onStartRangeCover = onStartRangeCover,
                        onTogglePrivateCoverMode = onTogglePrivateCoverMode
                    )
                    BarDivider(height = 22.dp)
                    BarIconButton(Icons.AutoMirrored.Rounded.Undo, "元に戻す", onUndo, enabled = uiState.canUndo, modifier = small)
                    BarIconButton(Icons.AutoMirrored.Rounded.Redo, "やり直す", onRedo, enabled = uiState.canRedo, modifier = small)
                    BarDivider(height = 22.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceMuted)
                            .clickable(onClick = onOpenPages)
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.GridView,
                            contentDescription = "ページ一覧",
                            tint = AppColors.InkSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${uiState.currentVisiblePage}/${uiState.totalPages}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Ink,
                            maxLines = 1
                        )
                    }
                    BarIconButton(Icons.Rounded.PostAdd, "下にページを追加", onAddPage, modifier = small)
                    BarDivider(height = 22.dp)
                    ZoomControls(
                        zoomPercent = uiState.zoomPercent,
                        isLocked = uiState.isZoomLocked,
                        onZoomMinus = onZoomMinus,
                        onZoomPlus = onZoomPlus,
                        onSetZoomPercent = onSetZoomPercent,
                        onToggleLock = onToggleZoomLock
                    )
                }

                BarIconButton(Icons.Rounded.ExpandLess, "このバーを隠す", onHideFileBar, modifier = small, tint = AppColors.InkSoft)
                Box {
                    BarIconButton(Icons.Rounded.MoreVert, "その他のメニュー", { showMoreMenu = true }, modifier = small)
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        MenuRow(
                            Icons.Rounded.GridView,
                            "用紙の種類（${uiState.activeDocument.template.shortLabel}）"
                        ) {
                            showMoreMenu = false
                            onChangeTemplate()
                        }
                        MenuRow(Icons.Rounded.PictureAsPdf, "PDFに書き出す") {
                            showMoreMenu = false
                            onExportPdf()
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("ペンだけで書く", fontSize = 15.sp)
                                    Text(
                                        if (uiState.isStylusOnlyMode) "指は移動・拡大専用" else "指でも書けます",
                                        fontSize = 12.sp,
                                        color = AppColors.InkSoft
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = AppColors.InkSoft)
                            },
                            trailingIcon = {
                                Switch(checked = uiState.isStylusOnlyMode, onCheckedChange = { onToggleStylusOnly() })
                            },
                            onClick = onToggleStylusOnly
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ToolBarPlacementRow(
                            placement = toolBarPlacement,
                            isVisible = isToolBarVisible,
                            onSetPlacement = onSetToolBarPlacement,
                            onSetVisible = onSetToolBarVisible
                        )
                        MenuRow(Icons.Rounded.Settings, "設定") {
                            showMoreMenu = false
                            onOpenSettings()
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        MenuRow(Icons.Rounded.DeleteSweep, "このページを白紙に戻す") {
                            showMoreMenu = false
                            onClearPage()
                        }
                        MenuRow(Icons.Rounded.Delete, "このページを削除", danger = true) {
                            showMoreMenu = false
                            onDeletePage()
                        }
                        MenuRow(Icons.Rounded.Delete, "このノートを削除", danger = true) {
                            showMoreMenu = false
                            onDeleteNote()
                        }
                    }
                }
            }
        }
    }
}

/** メニューの中に置く「ツールバーの位置」と表示／非表示の切り替え */
@Composable
private fun ToolBarPlacementRow(
    placement: ToolBarPlacement,
    isVisible: Boolean,
    onSetPlacement: (ToolBarPlacement) -> Unit,
    onSetVisible: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("ツールバーの位置", fontSize = 12.sp, color = AppColors.InkSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedBox {
                ToolBarPlacement.entries.forEach { p ->
                    SegmentItem(
                        selected = isVisible && placement == p,
                        onClick = {
                            onSetPlacement(p)
                            onSetVisible(true)
                        }
                    ) { tint -> SegmentLabel(p.label, tint) }
                }
                SegmentItem(selected = !isVisible, onClick = { onSetVisible(false) }) { tint ->
                    SegmentLabel("隠す", tint)
                }
            }
        }
    }
}

/** 目隠しの入口。「全面を隠す」か「範囲を選んで隠す」を選ぶ */
@Composable
private fun CoverMenuButton(
    isSheetVisible: Boolean,
    isRangeCoverActive: Boolean,
    isPrivateCoverMode: Boolean,
    onToggleSheet: () -> Unit,
    onStartRangeCover: () -> Unit,
    onTogglePrivateCoverMode: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BarIconButton(
            Icons.Rounded.VisibilityOff,
            "目隠し",
            { expanded = true },
            modifier = Modifier.size(40.dp),
            active = isSheetVisible || isRangeCoverActive || isPrivateCoverMode
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(if (isSheetVisible) "全面の目隠しを外す" else "全面を隠す", fontSize = 15.sp)
                        Text("ノート全体をまとめて隠す", fontSize = 12.sp, color = AppColors.InkSoft)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = AppColors.InkSoft) },
                trailingIcon = {
                    if (isSheetVisible) Icon(Icons.Rounded.Check, contentDescription = "使用中", tint = AppColors.Accent)
                },
                onClick = {
                    expanded = false
                    onToggleSheet()
                }
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("範囲を選んで隠す", fontSize = 15.sp)
                        Text("好きな場所を四角で隠す", fontSize = 12.sp, color = AppColors.InkSoft)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.HighlightAlt, contentDescription = null, tint = AppColors.InkSoft) },
                trailingIcon = {
                    if (isRangeCoverActive) Icon(Icons.Rounded.Check, contentDescription = "使用中", tint = AppColors.Accent)
                },
                onClick = {
                    expanded = false
                    onStartRangeCover()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Column {
                        Text("配信では隠す（試作）", fontSize = 15.sp)
                        Text(
                            "範囲の目隠しの中身を、手元にだけ表示する",
                            fontSize = 12.sp,
                            color = AppColors.InkSoft
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null, tint = AppColors.InkSoft) },
                trailingIcon = {
                    Switch(checked = isPrivateCoverMode, onCheckedChange = { onTogglePrivateCoverMode() })
                },
                onClick = onTogglePrivateCoverMode
            )
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) AppColors.Danger else AppColors.Ink
    DropdownMenuItem(
        text = { Text(label, fontSize = 15.sp, color = color) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = if (danger) AppColors.Danger else AppColors.InkSoft)
        },
        onClick = onClick
    )
}

/** 倍率: − と ＋ は常に表示。数字をタップすると既定の倍率や固定を選べる */
@Composable
private fun ZoomControls(
    zoomPercent: Int,
    isLocked: Boolean,
    onZoomMinus: () -> Unit,
    onZoomPlus: () -> Unit,
    onSetZoomPercent: (Int) -> Unit,
    onToggleLock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    BarIconButton(Icons.Rounded.Remove, "縮小", onZoomMinus, modifier = Modifier.size(36.dp))
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(34.dp)
                .widthIn(min = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLocked) AppColors.AccentSoft else Color.Transparent)
                .clickable { expanded = true }
                .padding(horizontal = 4.dp)
        ) {
            if (isLocked) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = "倍率固定中",
                    tint = AppColors.Accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = "$zoomPercent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLocked) AppColors.Accent else AppColors.Ink,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuRow(Icons.Rounded.FitScreen, "80%（標準・左右に余白）") {
                onSetZoomPercent(80)
                expanded = false
            }
            MenuRow(Icons.Rounded.FitScreen, "100%（幅いっぱい）") {
                onSetZoomPercent(100)
                expanded = false
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text("倍率を固定", fontSize = 15.sp)
                        Text("2本指は移動だけになります", fontSize = 12.sp, color = AppColors.InkSoft)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppColors.InkSoft) },
                trailingIcon = { Switch(checked = isLocked, onCheckedChange = { onToggleLock() }) },
                onClick = onToggleLock
            )
        }
    }
    BarIconButton(Icons.Rounded.Add, "拡大", onZoomPlus, modifier = Modifier.size(36.dp))
}

// ---------------------------------------------------------------------------
// ツールバー：ツールと、そのツールの設定（上に横置き／左右に縦置き）
// ---------------------------------------------------------------------------

@Composable
fun EditorToolBar(
    uiState: NoteUiState,
    placement: ToolBarPlacement,
    onSelectTool: (ToolType) -> Unit,
    onSelectPenColor: (Int) -> Unit,
    onSelectPenWidth: (Float) -> Unit,
    onToggleDashedLine: (Boolean) -> Unit,
    onSelectHlColor: (Int) -> Unit,
    onSelectShapeMode: (ShapeMode) -> Unit,
    onSelectEraserMode: (EraserMode) -> Unit,
    onSelectEraserRadius: (Float) -> Unit,
    onRequestClearPage: () -> Unit,
    onSelectAxesOrigin: (fx: Float, fy: Float) -> Unit,
    onSetAllCoversRevealed: (Boolean) -> Unit,
    onDeleteCoversOnPage: () -> Unit,
    onSelectPreset: (PenPreset) -> Unit,
    onRegisterPreset: () -> Unit,
    onDeletePreset: (String) -> Unit,
    onSetPlacement: (ToolBarPlacement) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vertical = placement != ToolBarPlacement.TOP

    // 並べる中身は横置き・縦置きで共通。wide は「太さや線種をそのまま並べる余裕があるか」
    val items: @Composable (wide: Boolean) -> Unit = { wide ->
        ToolBarItems(
            uiState = uiState,
            vertical = vertical,
            wide = wide,
            onSelectTool = onSelectTool,
            onSelectPenColor = onSelectPenColor,
            onSelectPenWidth = onSelectPenWidth,
            onToggleDashedLine = onToggleDashedLine,
            onSelectHlColor = onSelectHlColor,
            onSelectShapeMode = onSelectShapeMode,
            onSelectEraserMode = onSelectEraserMode,
            onSelectEraserRadius = onSelectEraserRadius,
            onRequestClearPage = onRequestClearPage,
            onSelectAxesOrigin = onSelectAxesOrigin,
            onSetAllCoversRevealed = onSetAllCoversRevealed,
            onDeleteCoversOnPage = onDeleteCoversOnPage
        )
    }
    val trailing: @Composable () -> Unit = {
        PenBoxMenuButton(
            presets = uiState.penPresets,
            activePresetId = uiState.activePresetId,
            showLabel = false,
            onSelectPreset = onSelectPreset,
            onRegisterPreset = onRegisterPreset,
            onDeletePreset = onDeletePreset
        )
        ToolBarMenuButton(placement = placement, onSetPlacement = onSetPlacement, onHide = onHide)
    }

    if (vertical) {
        // 用紙の左右の余白に浮かせる縦長のバー
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = AppColors.Surface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, AppColors.Line)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(60.dp)
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) { items(false) }
                ToolSeparator(vertical = true)
                trailing()
            }
        }
    } else {
        Surface(color = AppColors.Surface, modifier = modifier.fillMaxWidth()) {
            BoxWithConstraints {
                val wide = maxWidth >= 900.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) { items(wide) }
                    ToolSeparator(vertical = false)
                    trailing()
                }
            }
        }
    }
}

/** 区切り線（横置きでは縦線、縦置きでは横線） */
@Composable
private fun ToolSeparator(vertical: Boolean) {
    if (vertical) {
        Box(
            modifier = Modifier
                .padding(vertical = 5.dp)
                .width(30.dp)
                .height(1.dp)
                .background(AppColors.Line)
        )
    } else {
        BarDivider(height = 24.dp)
    }
}

@Composable
private fun ToolBarItems(
    uiState: NoteUiState,
    vertical: Boolean,
    wide: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onSelectPenColor: (Int) -> Unit,
    onSelectPenWidth: (Float) -> Unit,
    onToggleDashedLine: (Boolean) -> Unit,
    onSelectHlColor: (Int) -> Unit,
    onSelectShapeMode: (ShapeMode) -> Unit,
    onSelectEraserMode: (EraserMode) -> Unit,
    onSelectEraserRadius: (Float) -> Unit,
    onRequestClearPage: () -> Unit,
    onSelectAxesOrigin: (fx: Float, fy: Float) -> Unit,
    onSetAllCoversRevealed: (Boolean) -> Unit,
    onDeleteCoversOnPage: () -> Unit
) {
    val tool = uiState.currentTool
    val isShapeTool = tool == ToolType.LINE_SHAPE || tool == ToolType.XY_AXES

    // --- ツール ---
    ToolButton(
        selected = tool == ToolType.PEN,
        label = "ペン",
        colorDot = Color(uiState.selectedPenColorInt),
        onClick = { onSelectTool(ToolType.PEN) }
    ) { tint -> Icon(Icons.Rounded.Edit, null, tint = tint, modifier = Modifier.size(22.dp)) }
    ToolButton(
        selected = tool == ToolType.HIGHLIGHTER,
        label = "マーカー",
        colorDot = Color(uiState.selectedHlColorInt),
        onClick = { onSelectTool(ToolType.HIGHLIGHTER) }
    ) { tint -> Icon(Icons.Rounded.BorderColor, null, tint = tint, modifier = Modifier.size(22.dp)) }
    ToolButton(
        selected = isShapeTool,
        label = "図形",
        onClick = { if (!isShapeTool) onSelectTool(ToolType.LINE_SHAPE) }
    ) { tint -> ShapesGlyph(tint) }
    ToolButton(
        selected = tool == ToolType.STROKE_ERASER,
        label = "消しゴム",
        onClick = { onSelectTool(ToolType.STROKE_ERASER) }
    ) { tint -> EraserGlyph(tint) }
    ToolButton(
        selected = tool == ToolType.LASSO,
        label = "選択",
        onClick = { onSelectTool(ToolType.LASSO) }
    ) { tint -> LassoGlyph(tint) }
    ToolButton(
        selected = tool == ToolType.LASER_POINTER,
        label = "ポインター",
        accent = AppColors.Laser,
        accentSoft = Color(0xFFFFE4EA),
        onClick = { onSelectTool(ToolType.LASER_POINTER) }
    ) { tint -> LaserGlyph(tint) }

    ToolSeparator(vertical)

    // --- 選択中ツールの設定 ---
    when {
        tool == ToolType.PEN || isShapeTool -> {
            if (isShapeTool) {
                ShapePicker(
                    showLabel = wide,
                    current = if (tool == ToolType.XY_AXES) ShapeMode.XY_AXES_SHAPE else uiState.selectedShapeMode,
                    onSelect = { mode ->
                        if (mode == ShapeMode.XY_AXES_SHAPE) {
                            onSelectTool(ToolType.XY_AXES)
                        } else {
                            onSelectShapeMode(mode)
                        }
                    }
                )
                if (tool == ToolType.XY_AXES) {
                    Spacer(Modifier.size(4.dp))
                    AxesOriginPicker(
                        fx = uiState.axesOriginFx,
                        fy = uiState.axesOriginFy,
                        showLabel = wide,
                        onSelect = onSelectAxesOrigin
                    )
                }
                ToolSeparator(vertical)
            }
            PenColor.ALL_PEN_COLORS.forEach { c ->
                ColorSwatch(
                    color = Color(c.colorInt),
                    name = c.name,
                    selected = uiState.selectedPenColorInt == c.colorInt,
                    onClick = { onSelectPenColor(c.colorInt) }
                )
            }
            ToolSeparator(vertical)
            if (wide) {
                PenWidthButtons(uiState.selectedPenWidth, Color(uiState.selectedPenColorInt), onSelectPenWidth)
                ToolSeparator(vertical)
                LineStyleSegment(uiState.isDashedLine, onToggleDashedLine)
            } else {
                StrokeStyleMenuButton(
                    penWidth = uiState.selectedPenWidth,
                    isDashed = uiState.isDashedLine,
                    penColor = Color(uiState.selectedPenColorInt),
                    showArrow = !vertical,
                    onSelectPenWidth = onSelectPenWidth,
                    onToggleDashedLine = onToggleDashedLine
                )
            }
        }

        tool == ToolType.HIGHLIGHTER -> {
            PenColor.ALL_HL_COLORS.forEach { c ->
                ColorSwatch(
                    color = Color(c.colorInt),
                    name = c.name,
                    selected = uiState.selectedHlColorInt == c.colorInt,
                    checkTint = AppColors.Ink,
                    onClick = { onSelectHlColor(c.colorInt) }
                )
            }
        }

        tool == ToolType.STROKE_ERASER -> {
            if (vertical) {
                SegmentItem(
                    selected = uiState.eraserMode == EraserMode.PARTIAL,
                    onClick = { onSelectEraserMode(EraserMode.PARTIAL) }
                ) { tint -> SegmentLabel("部分", tint) }
                SegmentItem(
                    selected = uiState.eraserMode == EraserMode.STROKE,
                    onClick = { onSelectEraserMode(EraserMode.STROKE) }
                ) { tint -> SegmentLabel("一筆", tint) }
            } else {
                SegmentedBox {
                    SegmentItem(
                        selected = uiState.eraserMode == EraserMode.PARTIAL,
                        onClick = { onSelectEraserMode(EraserMode.PARTIAL) }
                    ) { tint -> SegmentLabel("なぞった所だけ", tint) }
                    SegmentItem(
                        selected = uiState.eraserMode == EraserMode.STROKE,
                        onClick = { onSelectEraserMode(EraserMode.STROKE) }
                    ) { tint -> SegmentLabel("線をまるごと", tint) }
                }
            }
            ToolSeparator(vertical)
            listOf(
                Triple(7f, 7.dp, "極小"),
                Triple(14f, 11.dp, "小"),
                Triple(24f, 16.dp, "中"),
                Triple(40f, 23.dp, "大")
            ).forEach { (radius, dot, name) ->
                DotSizeButton(
                    dotSize = dot,
                    name = "消しゴム $name",
                    selected = uiState.eraserRadius == radius,
                    dotColor = AppColors.InkFaint,
                    hollow = true,
                    onClick = { onSelectEraserRadius(radius) }
                )
            }
            ToolSeparator(vertical)
            if (vertical) {
                BarIconButton(Icons.Rounded.DeleteSweep, "ページを白紙に", onRequestClearPage, tint = AppColors.InkSoft)
            } else {
                OptionPill(Icons.Rounded.DeleteSweep, "ページを白紙に", onClick = onRequestClearPage)
            }
        }

        tool == ToolType.COVER -> {
            if (vertical) {
                BarIconButton(Icons.Rounded.Visibility, "目隠しをすべて開く", { onSetAllCoversRevealed(true) })
                BarIconButton(Icons.Rounded.VisibilityOff, "目隠しをすべて隠す", { onSetAllCoversRevealed(false) })
                BarIconButton(Icons.Rounded.Delete, "ページ内の目隠しを全削除", onDeleteCoversOnPage, tint = AppColors.Danger)
            } else {
                if (wide) {
                    ToolHint("ドラッグで隠す ／ タップで開け閉め ／ 開いて×で削除")
                    ToolSeparator(vertical)
                }
                OptionPill(Icons.Rounded.Visibility, "すべて開く") { onSetAllCoversRevealed(true) }
                OptionPill(Icons.Rounded.VisibilityOff, "すべて隠す") { onSetAllCoversRevealed(false) }
                OptionPill(Icons.Rounded.Delete, "ページ内を全削除", danger = true, onClick = onDeleteCoversOnPage)
            }
        }

        tool == ToolType.LASSO -> {
            if (!vertical) ToolHint("囲んで選択 → 移動・拡大・回転・コピー")
        }

        tool == ToolType.LASER_POINTER -> {
            if (!vertical) ToolHint("なぞると赤く光ります（ノートには残りません）")
        }
    }
}

/** ツールバー自身のメニュー（置き場所を変える・隠す） */
@Composable
private fun ToolBarMenuButton(
    placement: ToolBarPlacement,
    onSetPlacement: (ToolBarPlacement) -> Unit,
    onHide: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BarIconButton(
            Icons.Rounded.MoreHoriz,
            "ツールバーの位置と表示",
            { expanded = true },
            modifier = Modifier.size(40.dp),
            tint = AppColors.InkSoft
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ツールバーの位置", fontSize = 12.sp, color = AppColors.InkSoft)
                SegmentedBox {
                    ToolBarPlacement.entries.forEach { p ->
                        SegmentItem(
                            selected = placement == p,
                            onClick = {
                                onSetPlacement(p)
                                expanded = false
                            }
                        ) { tint -> SegmentLabel(p.label, tint) }
                    }
                }
            }
            MenuRow(Icons.Rounded.VisibilityOff, "ツールバーを隠す") {
                expanded = false
                onHide()
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    colorDot: Color? = null,
    accent: Color = AppColors.Accent,
    accentSoft: Color = AppColors.AccentSoft,
    glyph: @Composable (tint: Color) -> Unit
) {
    val fg = if (selected) accent else AppColors.InkSoft
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(1.dp)
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentSoft else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 32.dp, height = 26.dp)) {
            glyph(fg)
            if (colorDot != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(colorDot)
                )
            }
        }
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    checkTint: Color = Color.White
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (selected) 36.dp else 28.dp)
                .then(
                    if (selected) {
                        Modifier
                            .border(2.5.dp, AppColors.Accent, CircleShape)
                            .padding(5.dp)
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(color)
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "$name（選択中）",
                    tint = checkTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DotSizeButton(
    dotSize: Dp,
    name: String,
    selected: Boolean,
    dotColor: Color,
    onClick: () -> Unit,
    hollow: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.AccentSoft else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.5.dp, AppColors.Accent, RoundedCornerShape(12.dp)) else Modifier
            )
            .clickable(onClickLabel = name, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .then(
                    if (hollow) {
                        Modifier
                            .background(Color.White)
                            .border(1.5.dp, if (selected) AppColors.Accent else dotColor, CircleShape)
                    } else {
                        Modifier.background(dotColor)
                    }
                )
        )
    }
}

private val penWidthOptions = listOf(Triple(2.6f, 6.dp, "細い"), Triple(4.2f, 10.dp, "ふつう"), Triple(7.0f, 15.dp, "太い"))

@Composable
private fun PenWidthButtons(selectedWidth: Float, dotColor: Color, onSelect: (Float) -> Unit) {
    penWidthOptions.forEach { (width, dot, name) ->
        DotSizeButton(
            dotSize = dot,
            name = name,
            selected = selectedWidth == width,
            dotColor = dotColor,
            onClick = { onSelect(width) }
        )
    }
}

@Composable
private fun LineStyleSegment(isDashed: Boolean, onToggle: (Boolean) -> Unit) {
    SegmentedBox {
        SegmentItem(selected = !isDashed, onClick = { onToggle(false) }) { tint ->
            LineStyleGlyph(dashed = false, tint = tint)
        }
        SegmentItem(selected = isDashed, onClick = { onToggle(true) }) { tint ->
            LineStyleGlyph(dashed = true, tint = tint)
        }
    }
}

/** 幅が狭いとき用：太さと線種をまとめたボタン */
@Composable
private fun StrokeStyleMenuButton(
    penWidth: Float,
    isDashed: Boolean,
    penColor: Color,
    showArrow: Boolean,
    onSelectPenWidth: (Float) -> Unit,
    onToggleDashedLine: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dot = penWidthOptions.firstOrNull { it.first == penWidth }?.second ?: 9.dp
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceMuted)
                .clickable(onClickLabel = "線の太さと種類") { expanded = true }
                .padding(start = if (showArrow) 10.dp else 3.dp, end = if (showArrow) 4.dp else 3.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(dot)
                        .clip(CircleShape)
                        .background(penColor)
                )
            }
            Spacer(Modifier.width(4.dp))
            LineStyleGlyph(dashed = isDashed, tint = AppColors.Ink)
            if (showArrow) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "線の太さと種類", tint = AppColors.InkFaint, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("太さ", fontSize = 12.sp, color = AppColors.InkSoft)
                Row { PenWidthButtons(penWidth, penColor, onSelectPenWidth) }
                Text("線の種類", fontSize = 12.sp, color = AppColors.InkSoft)
                LineStyleSegment(isDashed, onToggleDashedLine)
            }
        }
    }
}

@Composable
private fun SegmentedBox(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceMuted)
            .padding(3.dp)
    ) { content() }
}

@Composable
private fun SegmentItem(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(34.dp)
                .widthIn(min = 46.dp)
                .padding(horizontal = 10.dp)
        ) {
            content(if (selected) AppColors.Accent else AppColors.InkSoft)
        }
    }
}

@Composable
private fun SegmentLabel(text: String, tint: Color) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
}

@Composable
private fun ToolHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = AppColors.InkSoft,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

private val shapeMenuEntries = listOf(
    ShapeMode.LINE to "╱",
    ShapeMode.ARROW to "→",
    ShapeMode.RECTANGLE to "□",
    ShapeMode.CROSSED_RECT to "☒",
    ShapeMode.CIRCLE to "○",
    ShapeMode.TRIANGLE to "△",
    ShapeMode.RIGHT_TRIANGLE to "⊿",
    ShapeMode.PARABOLA to "∪",
    ShapeMode.XY_AXES_SHAPE to "┼",
    ShapeMode.NUMBER_LINE to "↔"
)

@Composable
private fun ShapePicker(current: ShapeMode, showLabel: Boolean, onSelect: (ShapeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val symbol = shapeMenuEntries.firstOrNull { it.first == current }?.second ?: ""
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.AccentSoft)
                .clickable { expanded = true }
                .padding(start = 12.dp, end = if (showLabel) 6.dp else 12.dp)
        ) {
            Text(symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Accent)
            if (showLabel) {
                Spacer(Modifier.width(8.dp))
                Text(current.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.AccentDeep)
            }
            if (showLabel) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "図形を選ぶ", tint = AppColors.Accent)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            shapeMenuEntries.forEach { (mode, sym) ->
                val isSel = mode == current
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) AppColors.Accent else AppColors.Ink
                        )
                    },
                    leadingIcon = {
                        Text(
                            sym,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) AppColors.Accent else AppColors.InkSoft
                        )
                    },
                    trailingIcon = {
                        if (isSel) Icon(Icons.Rounded.Check, contentDescription = null, tint = AppColors.Accent)
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionPill(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val fg = if (danger) AppColors.Danger else AppColors.Ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (danger) AppColors.DangerSoft else AppColors.SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

private val axesOriginSteps = listOf(0.1f, 0.5f, 0.9f)

private fun nearestAxesStep(v: Float): Float = axesOriginSteps.minByOrNull { kotlin.math.abs(it - v) } ?: 0.5f

/** XY軸の原点をどこに置くかを 3×3 の見本から選ぶ */
@Composable
private fun AxesOriginPicker(fx: Float, fy: Float, showLabel: Boolean, onSelect: (Float, Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceMuted)
                .clickable(onClickLabel = "原点の位置を選ぶ") { expanded = true }
                .padding(start = 10.dp, end = if (showLabel) 6.dp else 10.dp)
        ) {
            AxesGlyph(fx, fy, AppColors.Ink, size = 22.dp)
            if (showLabel) {
                Spacer(Modifier.width(8.dp))
                Text("原点の位置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
            }
            if (showLabel) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "原点の位置を選ぶ", tint = AppColors.InkFaint)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("原点をどこに置く？", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                axesOriginSteps.forEach { rowFy ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        axesOriginSteps.forEach { colFx ->
                            val selected = nearestAxesStep(fx) == colFx && nearestAxesStep(fy) == rowFy
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) AppColors.AccentSoft else AppColors.SurfaceMuted)
                                    .then(
                                        if (selected) Modifier.border(2.dp, AppColors.Accent, RoundedCornerShape(12.dp)) else Modifier
                                    )
                                    .clickable {
                                        onSelect(colFx, rowFy)
                                        expanded = false
                                    }
                            ) {
                                AxesGlyph(colFx, rowFy, if (selected) AppColors.Accent else AppColors.Ink, size = 38.dp)
                            }
                        }
                    }
                }
                Text(
                    "描いたあとも「選択」で囲むと、\nオレンジの点をドラッグして原点を動かせます",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = AppColors.InkSoft
                )
            }
        }
    }
}

@Composable
private fun PresetDot(preset: PenPreset) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(if (preset.toolType == ToolType.HIGHLIGHTER) RoundedCornerShape(4.dp) else CircleShape)
            .background(Color(preset.colorInt))
    )
}

@Composable
private fun PresetChip(preset: PenPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AppColors.AccentSoft else AppColors.Surface,
        border = BorderStroke(1.dp, if (selected) AppColors.Accent else AppColors.Line)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 10.dp)
        ) {
            PresetDot(preset)
            Spacer(Modifier.width(6.dp))
            Text(
                preset.title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) AppColors.AccentDeep else AppColors.Ink,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PenBoxMenuButton(
    presets: List<PenPreset>,
    activePresetId: String?,
    showLabel: Boolean,
    onSelectPreset: (PenPreset) -> Unit,
    onRegisterPreset: () -> Unit,
    onDeletePreset: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp)
        ) {
            Icon(Icons.Rounded.Star, contentDescription = "ペン箱", tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
            if (showLabel) {
                Spacer(Modifier.width(6.dp))
                Text("ペン箱", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "ペン箱（よく使う設定をワンタップで）",
                fontSize = 12.sp,
                color = AppColors.InkSoft,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            presets.forEach { preset ->
                val isSel = preset.id == activePresetId
                DropdownMenuItem(
                    text = {
                        Text(
                            preset.title,
                            fontSize = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) AppColors.Accent else AppColors.Ink
                        )
                    },
                    leadingIcon = { PresetDot(preset) },
                    trailingIcon = {
                        if (presets.size > 1) {
                            BarIconButton(
                                Icons.Rounded.Close,
                                "「${preset.title}」をペン箱から外す",
                                onClick = { onDeletePreset(preset.id) },
                                tint = AppColors.InkFaint,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectPreset(preset)
                        expanded = false
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("今のペン設定を登録", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent) },
                onClick = onRegisterPreset
            )
        }
    }
}

// ---------------------------------------------------------------------------
// キャンバス上に浮かぶ操作
// ---------------------------------------------------------------------------

/** 選択ツールで何か選んでいる／コピー済みのときに出る操作バー */
@Composable
fun LassoActionBar(
    hasSelection: Boolean,
    canPaste: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (hasSelection) {
                ActionPill(Icons.Rounded.ContentCopy, "コピー", onCopy)
                ActionPill(Icons.Rounded.ContentCut, "切り取り", onCut)
            }
            if (canPaste) {
                ActionPill(Icons.Rounded.ContentPaste, "貼り付け", onPaste, emphasized = true)
            }
            if (hasSelection) {
                ActionPill(Icons.Rounded.Delete, "削除", onDelete, danger = true)
            }
            if (!hasSelection && canPaste) {
                Text(
                    "貼りたい場所をタップしてから「貼り付け」",
                    fontSize = 12.sp,
                    color = AppColors.InkSoft,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            BarDivider(height = 24.dp)
            ActionPill(Icons.Rounded.Close, if (hasSelection) "選択解除" else "やめる", onClear)
        }
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    danger: Boolean = false
) {
    val fg = when {
        emphasized -> Color.White
        danger -> AppColors.Danger
        else -> AppColors.Ink
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (emphasized) AppColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** 隠したバーを戻すための小さなつまみ（キャンバス右上） */
@Composable
fun HiddenBarHandles(
    isFileBarHidden: Boolean,
    isToolBarHidden: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    timerText: String?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowFileBar: () -> Unit,
    onShowToolBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFileBarHidden) {
                if (timerText != null) {
                    Text(
                        timerText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Accent,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                BarIconButton(Icons.AutoMirrored.Rounded.Undo, "元に戻す", onUndo, enabled = canUndo, modifier = Modifier.size(38.dp))
                BarIconButton(Icons.AutoMirrored.Rounded.Redo, "やり直す", onRedo, enabled = canRedo, modifier = Modifier.size(38.dp))
                HandlePill("ファイル", onShowFileBar)
            }
            if (isToolBarHidden) {
                HandlePill("ツール", onShowToolBar)
            }
        }
    }
}

@Composable
private fun HandlePill(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "${label}バーを表示", onClick = onClick)
            .padding(start = 4.dp, end = 10.dp)
    ) {
        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent, maxLines = 1)
    }
}
