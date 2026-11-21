package com.example.lecturenote.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lecturenote.canvas.LectureCanvasView
import com.example.lecturenote.model.NoteSummary
import com.example.lecturenote.model.ToolType
import com.example.lecturenote.ui.theme.AppColors
import com.example.lecturenote.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ノート情報ダイアログの開き方 */
private sealed interface NoteInfoRequest {
    data class NewNote(val prefillStudent: String?) : NoteInfoRequest
    data object EditCurrent : NoteInfoRequest
}

@Composable
fun LectureNoteApp(viewModel: NoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var canvasViewRef by remember { mutableStateOf<LectureCanvasView?>(null) }

    // 授業サポート機能の状態（ホームに戻ってもタイマーは動き続ける）
    val timer = remember { LessonTimerState() }
    LessonTimerDriver(timer)
    var isFullscreen by remember { mutableStateOf(false) }
    var isCoverVisible by remember { mutableStateOf(false) }

    // ダイアログの状態
    var noteInfoRequest by remember { mutableStateOf<NoteInfoRequest?>(null) }
    var showPageOverview by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showClearPageConfirm by remember { mutableStateOf(false) }
    var showDeletePageConfirm by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<NoteSummary?>(null) }

    // フォルダ選択後に続けて書き出すノート（保存先の設定だけなら null）
    var pendingExportDocId by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onExportFolderSelected(context, treeUri, pendingExportDocId)
        }
        pendingExportDocId = null
    }

    val exportPdf: (String) -> Unit = { docId ->
        viewModel.exportNoteSummaryToPdf(
            context = context,
            docId = docId,
            onNeedFolderPicker = {
                pendingExportDocId = docId
                folderPickerLauncher.launch(null)
            }
        )
    }

    val goHome: () -> Unit = {
        isFullscreen = false
        isCoverVisible = false
        viewModel.openLandingPage()
    }

    val addPage: () -> Unit = {
        viewModel.addNewPageBelowCurrent { insertedIdx ->
            canvasViewRef?.onPageAppendedBelow(insertedIdx)
        }
    }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearNotification()
        }
    }

    // 授業中に「戻る」でアプリが閉じてしまわないよう、編集画面ではホームへ戻る動作にする
    BackHandler(enabled = !uiState.isShowingLandingPage) {
        if (isFullscreen) isFullscreen = false else goHome()
    }

    if (uiState.isShowingLandingPage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.AppBackground)
                .statusBarsPadding()
        ) {
            HomeScreen(
                uiState = uiState,
                onResumeActiveNote = { viewModel.resumeActiveNote() },
                onStartLesson = { student, subject, template ->
                    val now = Date()
                    viewModel.createNewNote(
                        studentName = student,
                        dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(now),
                        dayOfWeek = SimpleDateFormat("E", Locale.JAPAN).format(now),
                        subject = subject,
                        template = template
                    )
                },
                onOpenDetailedNewNote = { prefill ->
                    noteInfoRequest = NoteInfoRequest.NewNote(prefill.ifBlank { null })
                },
                onOpenNote = { docId -> viewModel.switchNoteDocument(docId) },
                onExportPdf = exportPdf,
                onDeleteNote = { summary -> pendingDeleteNote = summary },
                onOpenSettings = { showSettings = true }
            )
        }
    } else {
        // 授業中は画面が勝手に暗くならないようにする
        val hostView = LocalView.current
        DisposableEffect(hostView) {
            hostView.keepScreenOn = true
            onDispose { hostView.keepScreenOn = false }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Surface)
                .statusBarsPadding()
        ) {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    EditorTopBar(
                        uiState = uiState,
                        timer = timer,
                        isCoverVisible = isCoverVisible,
                        isRangeCoverActive = uiState.currentTool == ToolType.COVER,
                        onStartRangeCover = { viewModel.setTool(ToolType.COVER) },
                        onHome = goHome,
                        onEditInfo = { noteInfoRequest = NoteInfoRequest.EditCurrent },
                        onToggleCover = { isCoverVisible = !isCoverVisible },
                        onUndo = { canvasViewRef?.undo() },
                        onRedo = { canvasViewRef?.redo() },
                        onOpenPages = { showPageOverview = true },
                        onAddPage = addPage,
                        onZoomMinus = { canvasViewRef?.adjustZoomByPercentDelta(-10) },
                        onZoomPlus = { canvasViewRef?.adjustZoomByPercentDelta(10) },
                        onZoomFit = { canvasViewRef?.resetZoomToFitWidth() },
                        onToggleZoomLock = {
                            viewModel.toggleZoomLock()
                            canvasViewRef?.isZoomLocked = !uiState.isZoomLocked
                        },
                        onEnterFullscreen = { isFullscreen = true },
                        onChangeTemplate = { showTemplatePicker = true },
                        onExportPdf = { exportPdf(uiState.activeDocument.id) },
                        onToggleStylusOnly = {
                            viewModel.toggleStylusOnly()
                            canvasViewRef?.isStylusOnlyMode = !uiState.isStylusOnlyMode
                        },
                        onOpenSettings = { showSettings = true },
                        onClearPage = { showClearPageConfirm = true },
                        onDeletePage = {
                            if (uiState.totalPages <= 1) {
                                viewModel.showMessage("ページが1枚だけのときは削除できません（「白紙に戻す」は使えます）")
                            } else {
                                showDeletePageConfirm = true
                            }
                        },
                        onDeleteNote = { pendingDeleteNote = uiState.activeDocument.toSummary() }
                    )
                    HorizontalDivider(color = AppColors.Line)
                    EditorToolBar(
                        uiState = uiState,
                        onSelectTool = { viewModel.setTool(it) },
                        onSelectPenColor = { viewModel.setPenColor(it) },
                        onSelectPenWidth = { viewModel.setPenWidth(it) },
                        onToggleDashedLine = { viewModel.setDashedLine(it) },
                        onSelectHlColor = { viewModel.setHighlighterColor(it) },
                        onSelectShapeMode = { viewModel.setShapeMode(it) },
                        onSelectEraserMode = { viewModel.setEraserMode(it) },
                        onSelectEraserRadius = { viewModel.setEraserRadius(it) },
                        onRequestClearPage = { showClearPageConfirm = true },
                        onSelectAxesOrigin = { fx, fy -> viewModel.setAxesOrigin(fx, fy) },
                        onSetAllCoversRevealed = { canvasViewRef?.setAllCoversRevealed(it) },
                        onDeleteCoversOnPage = {
                            val removed = canvasViewRef?.deleteCoversOnVisiblePage() ?: 0
                            viewModel.showMessage(
                                if (removed > 0) "目隠しを${removed}個削除しました（「元に戻す」で復元できます）" else "このページに目隠しはありません"
                            )
                        },
                        onSelectPreset = { viewModel.selectPenPreset(it) },
                        onRegisterPreset = { viewModel.registerCurrentSettingToPenBox() },
                        onDeletePreset = { viewModel.removePenPreset(it) }
                    )
                    HorizontalDivider(color = AppColors.Line)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        LectureCanvasView(ctx).apply {
                            canvasViewRef = this
                            onViewportChanged = {
                                viewModel.syncViewportOnly(
                                    visiblePage = getCurrentVisiblePageNumber(),
                                    relativeZoomPercent = getRelativeZoomPercent()
                                )
                            }
                            onContentChanged = {
                                viewModel.onCanvasContentModified(
                                    visiblePage = getCurrentVisiblePageNumber(),
                                    relativeZoomPercent = getRelativeZoomPercent(),
                                    canUndo = canUndo(),
                                    canRedo = canRedo()
                                )
                            }
                            onSelectionChanged = { hasSel, canPaste ->
                                viewModel.updateSelectionStatus(hasSel, canPaste)
                            }
                            onStylusBarrelButtonPressed = {
                                viewModel.toggleEraserByStylusButton()
                            }
                            setDocument(uiState.activeDocument)
                        }
                    },
                    update = { view ->
                        canvasViewRef = view
                        view.currentTool = uiState.currentTool
                        view.currentShapeMode = uiState.selectedShapeMode
                        view.eraserMode = uiState.eraserMode
                        view.eraserRadius = uiState.eraserRadius
                        view.isDashedLine = uiState.isDashedLine
                        view.isStylusOnlyMode = uiState.isStylusOnlyMode
                        view.isZoomLocked = uiState.isZoomLocked
                        view.axesOriginFx = uiState.axesOriginFx
                        view.axesOriginFy = uiState.axesOriginFy

                        if (uiState.currentTool == ToolType.HIGHLIGHTER) {
                            view.currentColor = uiState.selectedHlColorInt
                            view.currentStrokeWidth = uiState.selectedHlWidth
                        } else {
                            view.currentColor = uiState.selectedPenColorInt
                            view.currentStrokeWidth = uiState.selectedPenWidth
                        }

                        view.setDocument(uiState.activeDocument)
                    },
                    onRelease = { canvasViewRef = null }
                )

                if (isCoverVisible) {
                    FullCover(onClose = { isCoverVisible = false })
                }

                if (uiState.currentTool == ToolType.LASSO &&
                    (uiState.hasLassoSelection || uiState.canPasteClipboard)
                ) {
                    LassoActionBar(
                        hasSelection = uiState.hasLassoSelection,
                        canPaste = uiState.canPasteClipboard,
                        onCopy = { canvasViewRef?.copySelectionToClipboard() },
                        onCut = { canvasViewRef?.cutSelectionToClipboard() },
                        onPaste = { canvasViewRef?.pasteClipboardAtTarget() },
                        onDelete = { canvasViewRef?.deleteSelection() },
                        onClear = {
                            if (uiState.hasLassoSelection) {
                                canvasViewRef?.clearLassoSelection()
                            } else {
                                canvasViewRef?.clearClipboardAndSelection()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (isFullscreen) 64.dp else 12.dp)
                    )
                }

                if (isFullscreen) {
                    FullscreenControls(
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        timerText = if (timer.isActive) timer.displayText else null,
                        onUndo = { canvasViewRef?.undo() },
                        onRedo = { canvasViewRef?.redo() },
                        onExit = { isFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

                if (timer.isFinished) {
                    TimerFinishedBanner(
                        onDismiss = { timer.dismissFinished() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp)
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------------- ダイアログ

    if (showClearPageConfirm) {
        ConfirmDialog(
            title = "このページを白紙に戻しますか？",
            message = "${uiState.currentVisiblePage} ページ目に書いた内容をすべて消します。\n消したあとも「元に戻す」で復元できます。",
            confirmLabel = "白紙に戻す",
            onConfirm = {
                canvasViewRef?.clearPage()
                showClearPageConfirm = false
            },
            onDismiss = { showClearPageConfirm = false }
        )
    }

    if (showDeletePageConfirm) {
        ConfirmDialog(
            title = "このページを削除しますか？",
            message = "${uiState.currentVisiblePage} ページ目（全 ${uiState.totalPages} ページ）をページごと削除します。\nこの操作は元に戻せません。",
            confirmLabel = "ページを削除",
            onConfirm = {
                viewModel.deleteCurrentPage { canvasViewRef?.onPageRemoved() }
                showDeletePageConfirm = false
            },
            onDismiss = { showDeletePageConfirm = false }
        )
    }

    pendingDeleteNote?.let { target ->
        val meta = target.metadata
        ConfirmDialog(
            title = "このノートを削除しますか？",
            message = "${meta.studentName} さん ・ ${meta.subject} ・ ${meta.dateStr}（全 ${target.pageCount} ページ）\n\n端末から完全に削除され、元に戻せません。",
            confirmLabel = "ノートを削除",
            onConfirm = {
                viewModel.deleteNoteDocument(target.id)
                pendingDeleteNote = null
            },
            onDismiss = { pendingDeleteNote = null }
        )
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            current = uiState.activeDocument.template,
            onSelect = { tmpl ->
                viewModel.setTemplate(tmpl)
                canvasViewRef?.invalidate()
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            hasExportFolder = uiState.savedExportTreeUri != null,
            customStylusKeyCode = uiState.customStylusKeyCode,
            expiryDays = uiState.studentExpiryDays,
            onChangeExportFolder = {
                pendingExportDocId = null
                folderPickerLauncher.launch(null)
            },
            onOpenStylusKeyConfig = {
                showSettings = false
                viewModel.setListeningForStylusKey(true)
            },
            onUpdateExpiryDays = { viewModel.updateStudentExpiryDays(it) },
            onDismiss = { showSettings = false }
        )
    }

    if (uiState.isListeningForStylusKey) {
        StylusButtonConfigDialog(
            customKeyCode = uiState.customStylusKeyCode,
            detectedInfo = uiState.lastDetectedStylusKeyInfo,
            onTestToggleEraser = { viewModel.toggleEraserByStylusButton() },
            onDismiss = { viewModel.setListeningForStylusKey(false) }
        )
    }

    noteInfoRequest?.let { request ->
        val isNew = request is NoteInfoRequest.NewNote
        val meta = uiState.activeDocument.metadata
        val today = remember(request) { Date() }
        NoteInfoDialog(
            isNewNote = isNew,
            initialStudent = when (request) {
                is NoteInfoRequest.NewNote -> request.prefillStudent ?: ""
                NoteInfoRequest.EditCurrent -> meta.studentName
            },
            initialSubject = meta.subject,
            // 新規は今日の日付、情報変更はそのノート自身の日付から始める
            initialDate = if (isNew) SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(today) else meta.dateStr,
            initialDayOfWeek = if (isNew) SimpleDateFormat("E", Locale.JAPAN).format(today) else meta.dayOfWeek,
            initialTemplate = uiState.activeDocument.template,
            recentStudents = uiState.recentStudents,
            recentSubjects = uiState.recentSubjects,
            onDismiss = { noteInfoRequest = null },
            onConfirm = { student, dateStr, dayOfWeek, subject, template ->
                if (isNew) {
                    viewModel.createNewNote(student, dateStr, dayOfWeek, subject, template)
                } else {
                    viewModel.updateNoteMetadata(student, dateStr, dayOfWeek, subject)
                }
                noteInfoRequest = null
            }
        )
    }

    if (showPageOverview && !uiState.isShowingLandingPage) {
        PageOverviewDialog(
            document = uiState.activeDocument,
            currentPage = uiState.currentVisiblePage,
            onJumpToPage = { index ->
                canvasViewRef?.scrollToPage(index)
                showPageOverview = false
            },
            onAddPage = addPage,
            onDismiss = { showPageOverview = false }
        )
    }
}
