package com.example.lecturenote.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lecturenote.canvas.LectureCanvasView
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.EraserMode
import com.example.lecturenote.model.NoteSummary
import com.example.lecturenote.model.PenColor
import com.example.lecturenote.model.PenPreset
import com.example.lecturenote.model.ShapeMode
import com.example.lecturenote.model.ToolType
import com.example.lecturenote.viewmodel.NoteUiState
import com.example.lecturenote.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LectureNoteApp(viewModel: NoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var canvasViewRef by remember { mutableStateOf<LectureCanvasView?>(null) }
    var showInfoBar by remember { mutableStateOf(false) }
    var showPenBoxBar by remember { mutableStateOf(true) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var isCreatingNewNoteInDialog by remember { mutableStateOf(false) }
    var prefillStudentInDialog by remember { mutableStateOf<String?>(null) }
    var showStudentGroupDialog by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }

    // 確認ダイアログ状態
    var showClearPageConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePageConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteNoteSummary by remember { mutableStateOf<NoteSummary?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onExportFolderSelected(context, treeUri)
        }
    }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearNotification()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFFE2E8F0))
    ) {
        if (uiState.isShowingLandingPage) {
            TutorLandingPage(
                uiState = uiState,
                onResumeActiveNote = { viewModel.resumeActiveNote() },
                onQuickCreateTodayNote = { student, subject, template ->
                    val now = Date()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(now)
                    val dayOfWeek = SimpleDateFormat("E", Locale.JAPAN).format(now)
                    viewModel.createNewNote(
                        studentName = student,
                        dateStr = dateStr,
                        dayOfWeek = dayOfWeek,
                        subject = subject,
                        template = template
                    )
                },
                onOpenCustomNewNoteDialog = { prefillStudent ->
                    prefillStudentInDialog = prefillStudent.ifEmpty { null }
                    isCreatingNewNoteInDialog = true
                    showMetadataDialog = true
                },
                onSelectNoteFromList = { docId ->
                    viewModel.switchNoteDocument(docId)
                },
                onExportNoteSummaryToPdf = { docId ->
                    viewModel.exportNoteSummaryToPdf(
                        context = context,
                        docId = docId,
                        onNeedFolderPicker = { folderPickerLauncher.launch(null) }
                    )
                },
                onRequestDeleteNoteSummary = { summary ->
                    pendingDeleteNoteSummary = summary
                },
                onSelectExportFolder = { folderPickerLauncher.launch(null) },
                onOpenStylusKeyConfig = { viewModel.setListeningForStylusKey(true) }
            )
        } else {
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

                    if (uiState.currentTool == ToolType.HIGHLIGHTER) {
                        view.currentColor = uiState.selectedHlColorInt
                        view.currentStrokeWidth = uiState.selectedHlWidth
                    } else {
                        view.currentColor = uiState.selectedPenColorInt
                        view.currentStrokeWidth = uiState.selectedPenWidth
                    }

                    view.setDocument(uiState.activeDocument)
                }
            )

            // 上部省スペース統合UI
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // (A) ハイド可能な「情報バー」
                AnimatedVisibility(
                    visible = showInfoBar,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    CollapsibleInfoBar(
                        uiState = uiState,
                        showTemplateMenu = showTemplateMenu,
                        onBackToLandingPage = { viewModel.openLandingPage() },
                        onOpenStudentGroups = { showStudentGroupDialog = true },
                        onOpenNamingDialog = {
                            isCreatingNewNoteInDialog = false
                            prefillStudentInDialog = null
                            showMetadataDialog = true
                        },
                        onAddPageBelow = {
                            viewModel.addNewPageBelowCurrent { insertedIdx ->
                                canvasViewRef?.onPageAppendedBelow(insertedIdx)
                            }
                        },
                        onRequestDeleteCurrentPage = {
                            if (uiState.totalPages <= 1) {
                                viewModel.showMessage("1ページのみのノートはページ削除できません（右上のゴミ箱ボタンで白紙に戻せます）")
                            } else {
                                showDeletePageConfirmDialog = true
                            }
                        },
                        onRequestDeleteCurrentNote = {
                            pendingDeleteNoteSummary = uiState.activeDocument.toSummary()
                        },
                        onOpenStylusKeyConfig = {
                            viewModel.setListeningForStylusKey(true)
                        },
                        onToggleStylusOnly = {
                            viewModel.toggleStylusOnly()
                            canvasViewRef?.isStylusOnlyMode = !uiState.isStylusOnlyMode
                        },
                        onToggleTemplateMenu = { showTemplateMenu = it },
                        onSelectTemplate = { tmpl ->
                            viewModel.setTemplate(tmpl)
                            canvasViewRef?.invalidate()
                            showTemplateMenu = false
                        },
                        onExportPdf = {
                            viewModel.exportToRememberedFolderOrRequestPicker(
                                context = context,
                                onNeedFolderPicker = { folderPickerLauncher.launch(null) }
                            )
                        },
                        onChangeFolder = { folderPickerLauncher.launch(null) },
                        onHideInfoBar = { showInfoBar = false }
                    )
                }

                // (B) 常時表示スリムバー
                AlwaysVisibleSlimTopBar(
                    uiState = uiState,
                    showInfoBar = showInfoBar,
                    showPenBoxBar = showPenBoxBar,
                    onToggleInfoBar = { showInfoBar = !showInfoBar },
                    onZoomMinus = { canvasViewRef?.adjustZoomByPercentDelta(-10) },
                    onZoomPlus = { canvasViewRef?.adjustZoomByPercentDelta(10) },
                    onZoomResetFit = { canvasViewRef?.resetZoomToFitWidth() },
                    onToggleZoomLock = {
                        viewModel.toggleZoomLock()
                        canvasViewRef?.isZoomLocked = !uiState.isZoomLocked
                    },
                    onSelectTool = { viewModel.setTool(it) },
                    onSelectPenColor = { viewModel.setPenColor(it) },
                    onSelectPenWidth = { viewModel.setPenWidth(it) },
                    onToggleDashedLine = { viewModel.setDashedLine(it) },
                    onSelectHlColor = { viewModel.setHighlighterColor(it) },
                    onSelectShapeMode = { viewModel.setShapeMode(it) },
                    onSelectEraserMode = { viewModel.setEraserMode(it) },
                    onSelectEraserRadius = { viewModel.setEraserRadius(it) },
                    onUndo = { canvasViewRef?.undo() },
                    onRedo = { canvasViewRef?.redo() },
                    onTogglePenBox = { showPenBoxBar = !showPenBoxBar },
                    onRequestClearPage = {
                        showClearPageConfirmDialog = true
                    }
                )

                // (C) スリム化したペン箱
                AnimatedVisibility(
                    visible = showPenBoxBar,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    SlimPenBoxBar(
                        presets = uiState.penPresets,
                        activePresetId = uiState.activePresetId,
                        onSelectPreset = { viewModel.selectPenPreset(it) },
                        onRegisterCurrent = { viewModel.registerCurrentSettingToPenBox() },
                        onDeletePreset = { viewModel.removePenPreset(it) }
                    )
                }

                // (D) 投げ縄（Lasso）操作バー（選択中 または コピー/カット後に「ペースト」ボタンを押せる状態）
                if (uiState.currentTool == ToolType.LASSO && (uiState.hasLassoSelection || uiState.canPasteClipboard)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LassoSelectionActionBar(
                        hasSelection = uiState.hasLassoSelection,
                        canPaste = uiState.canPasteClipboard,
                        onCopy = { canvasViewRef?.copySelectionToClipboard() },
                        onCut = { canvasViewRef?.cutSelectionToClipboard() },
                        onPaste = { canvasViewRef?.pasteClipboardAtTarget() },
                        onDelete = { canvasViewRef?.deleteSelection() },
                        onClearSelection = {
                            if (uiState.hasLassoSelection) {
                                canvasViewRef?.clearLassoSelection()
                            } else {
                                canvasViewRef?.clearClipboardAndSelection()
                            }
                        }
                    )
                }
            }
        }
    }

    // --- 1. ページ内容全消去の確認ダイアログ ---
    if (showClearPageConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearPageConfirmDialog = false },
            title = { Text("ページ内容の全消去", fontWeight = FontWeight.Bold) },
            text = {
                Text("現在表示中のページ（Page ${uiState.currentVisiblePage}）に書かれた内容をすべて消去しますか？\n（※消去後も「元に戻す」ボタンで復元可能です）")
            },
            confirmButton = {
                Button(
                    onClick = {
                        canvasViewRef?.clearPage()
                        showClearPageConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("すべて消去する", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPageConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // --- 2. ページごと削除の確認ダイアログ ---
    if (showDeletePageConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePageConfirmDialog = false },
            title = { Text("ページごと削除の確認", fontWeight = FontWeight.Bold) },
            text = {
                Text("現在開いている Page ${uiState.currentVisiblePage} / ${uiState.totalPages} をページごと削除しますか？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentPage {
                            canvasViewRef?.onPageRemoved()
                        }
                        showDeletePageConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("このページを削除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePageConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // --- 3. ノート丸ごと削除の確認ダイアログ ---
    pendingDeleteNoteSummary?.let { targetSummary ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNoteSummary = null },
            title = { Text("ノートを丸ごと削除", fontWeight = FontWeight.Bold) },
            text = {
                Text("ノート「${targetSummary.metadata.formattedNoteName}」（全${targetSummary.pageCount}ページ）を端末から丸ごと削除しますか？\n（※この操作は元に戻せません）")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNoteDocument(targetSummary.id)
                        pendingDeleteNoteSummary = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("ノートを丸ごと削除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNoteSummary = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // --- 4. ペンのサイドボタン診断・登録ダイアログ ---
    if (uiState.isListeningForStylusKey) {
        StylusButtonConfigDialog(
            customKeyCode = uiState.customStylusKeyCode,
            detectedInfo = uiState.lastDetectedStylusKeyInfo,
            onTestToggleEraser = { viewModel.toggleEraserByStylusButton() },
            onDismiss = { viewModel.setListeningForStylusKey(false) }
        )
    }

    // --- 5. ノート名設定ダイアログ ---
    if (showMetadataDialog) {
        val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(Date()) }
        val todayDayStr = remember { SimpleDateFormat("E", Locale.JAPAN).format(Date()) }

        TutorNamingDialog(
            initialStudent = prefillStudentInDialog ?: uiState.activeDocument.metadata.studentName,
            initialDate = todayDateStr,
            initialDayOfWeek = todayDayStr,
            initialSubject = uiState.activeDocument.metadata.subject,
            recentStudents = uiState.recentStudents,
            recentSubjects = uiState.recentSubjects,
            expiryDays = uiState.studentExpiryDays,
            isNewNoteMode = isCreatingNewNoteInDialog,
            onUpdateExpiryDays = { viewModel.updateStudentExpiryDays(it) },
            onDismiss = { showMetadataDialog = false },
            onConfirm = { student, dateStr, dayOfWeek, subject, createAsNew ->
                if (createAsNew) {
                    viewModel.createNewNote(student, dateStr, dayOfWeek, subject)
                } else {
                    viewModel.updateNoteMetadata(student, dateStr, dayOfWeek, subject)
                }
                showMetadataDialog = false
            }
        )
    }

    // --- 6. 生徒別プルダウン一覧ダイアログ ---
    if (showStudentGroupDialog) {
        StudentGroupedNotesDialog(
            noteSummaries = uiState.noteSummaries,
            activeDocId = uiState.activeDocument.id,
            onSelectDocument = { docId ->
                viewModel.switchNoteDocument(docId)
                showStudentGroupDialog = false
            },
            onCreateNewNoteForStudent = { studentName ->
                prefillStudentInDialog = if (studentName.isNotEmpty()) studentName else null
                isCreatingNewNoteInDialog = true
                showStudentGroupDialog = false
                showMetadataDialog = true
            },
            onRequestDeleteDocument = { summary ->
                pendingDeleteNoteSummary = summary
            },
            onDismiss = { showStudentGroupDialog = false }
        )
    }
}

@Composable
fun TutorLandingPage(
    uiState: NoteUiState,
    onResumeActiveNote: () -> Unit,
    onQuickCreateTodayNote: (student: String, subject: String, template: BackgroundTemplate) -> Unit,
    onOpenCustomNewNoteDialog: (prefillStudent: String) -> Unit,
    onSelectNoteFromList: (docId: String) -> Unit,
    onExportNoteSummaryToPdf: (docId: String) -> Unit,
    onRequestDeleteNoteSummary: (NoteSummary) -> Unit,
    onSelectExportFolder: () -> Unit,
    onOpenStylusKeyConfig: () -> Unit
) {
    val todayFormatted = remember {
        SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.JAPAN).format(Date())
    }

    val defaultStudent = remember(uiState.recentStudents, uiState.activeDocument) {
        uiState.recentStudents.firstOrNull() ?: uiState.activeDocument.metadata.studentName
    }
    var quickStudent by remember(defaultStudent) { mutableStateOf(defaultStudent) }
    var quickSubject by remember { mutableStateOf(uiState.activeDocument.metadata.subject.ifEmpty { "数学" }) }
    var quickTemplate by remember { mutableStateOf(uiState.activeDocument.template) }

    val expandedStudentMap = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }

    val groupedByStudent = remember(uiState.noteSummaries, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            uiState.noteSummaries
        } else {
            val q = searchQuery.trim().lowercase()
            uiState.noteSummaries.filter {
                it.metadata.studentName.lowercase().contains(q) ||
                    it.metadata.subject.lowercase().contains(q) ||
                    it.metadata.dateStr.contains(q)
            }
        }
        filtered.groupBy { it.metadata.studentName.ifEmpty { "未設定" } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "家庭教師 指導ノート (LectureNote)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "本日の日付: $todayFormatted",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onSelectExportFolder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (uiState.savedExportTreeUri != null) "PDF保存先: 設定済" else "PDF保存先を設定",
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenStylusKeyConfig,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("ペン釦設定", fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "直前のノートを再開",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.activeDocument.metadata.formattedNoteName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${uiState.activeDocument.template.label} | 全${uiState.activeDocument.pages.size}ページ",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onResumeActiveNote,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("続きを書く", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "本日の授業ノートをクイック作成",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            TextButton(
                                onClick = { onOpenCustomNewNoteDialog(quickStudent) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("日付等を個別指定...", fontSize = 11.sp)
                            }
                        }

                        OutlinedTextField(
                            value = quickStudent,
                            onValueChange = { quickStudent = it },
                            label = { Text("① 生徒名（下のチップから選択）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.recentStudents.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.recentStudents.forEach { sName ->
                                    FilterChip(
                                        selected = quickStudent == sName,
                                        onClick = { quickStudent = sName },
                                        label = { Text(sName, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        Text("② 教科を選択:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.recentSubjects.forEach { subj ->
                                FilterChip(
                                    selected = quickSubject == subj,
                                    onClick = { quickSubject = subj },
                                    label = { Text(subj, fontSize = 12.sp) }
                                )
                            }
                        }

                        Text("③ 罫線スタイル:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BackgroundTemplate.entries.forEach { tmpl ->
                                FilterChip(
                                    selected = quickTemplate == tmpl,
                                    onClick = { quickTemplate = tmpl },
                                    label = { Text(tmpl.label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onQuickCreateTodayNote(
                                    quickStudent.ifBlank { "未設定" },
                                    quickSubject.ifBlank { "数学" },
                                    quickTemplate
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text(
                                text = "「${quickStudent.ifBlank { "未設定" }}」さんの${quickSubject}ノートを開始",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "生徒別ノートフォルダ（タップでプルダウン開閉）",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("生徒名・教科・日付で検索...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "クリア", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedByStudent.forEach { (studentName, summaries) ->
                            val isExpanded = expandedStudentMap[studentName] == true || searchQuery.isNotBlank()
                            item(key = "landing_student_$studentName") {
                                StudentPullDownFolderCard(
                                    studentName = studentName,
                                    summaries = summaries,
                                    activeDocId = uiState.activeDocument.id,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedStudentMap[studentName] = !isExpanded
                                    },
                                    onSelectNote = onSelectNoteFromList,
                                    onCreateNewForStudent = {
                                        quickStudent = studentName
                                        onOpenCustomNewNoteDialog(studentName)
                                    },
                                    onExportPdf = onExportNoteSummaryToPdf,
                                    onDeleteNote = onRequestDeleteNoteSummary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentPullDownFolderCard(
    studentName: String,
    summaries: List<NoteSummary>,
    activeDocId: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectNote: (String) -> Unit,
    onCreateNewForStudent: () -> Unit,
    onExportPdf: ((String) -> Unit)? = null,
    onDeleteNote: (NoteSummary) -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) Color(0xFFF8FAFC) else Color(0xFFF1F5F9)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isExpanded) Color(0xFF93C5FD) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "プルダウン開閉",
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$studentName さん",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${summaries.size}冊",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onCreateNewForStudent,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("＋ 新規作成", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                    summaries.forEach { summary ->
                        val isCurrent = summary.id == activeDocId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Color(0xFFDBEAFE) else Color.White)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) Color(0xFF3B82F6) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectNote(summary.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.metadata.formattedNoteName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (isCurrent) Color(0xFF1E40AF) else Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "教科: ${summary.metadata.subject} | 日付: ${summary.metadata.dateStr}(${summary.metadata.dayOfWeek}) | 全${summary.pageCount}頁",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (onExportPdf != null) {
                                    OutlinedButton(
                                        onClick = { onExportPdf(summary.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteNote(summary) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "ノート削除",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleInfoBar(
    uiState: NoteUiState,
    showTemplateMenu: Boolean,
    onBackToLandingPage: () -> Unit,
    onOpenStudentGroups: () -> Unit,
    onOpenNamingDialog: () -> Unit,
    onAddPageBelow: () -> Unit,
    onRequestDeleteCurrentPage: () -> Unit,
    onRequestDeleteCurrentNote: () -> Unit,
    onOpenStylusKeyConfig: () -> Unit,
    onToggleStylusOnly: () -> Unit,
    onToggleTemplateMenu: (Boolean) -> Unit,
    onSelectTemplate: (BackgroundTemplate) -> Unit,
    onExportPdf: () -> Unit,
    onChangeFolder: () -> Unit,
    onHideInfoBar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8FAFC),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenStudentGroups,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("生徒別一覧", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onBackToLandingPage,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("ホーム", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                    .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onOpenNamingDialog() }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = uiState.activeDocument.metadata.formattedNoteName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "ノート名変更", modifier = Modifier.size(14.dp), tint = Color(0xFF2563EB))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFEE2E2))
                        .clickable { onRequestDeleteCurrentNote() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ノート削除", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEFF6FF))
                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                    .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Text(
                    text = "頁 ${uiState.currentVisiblePage}/${uiState.totalPages}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onAddPageBelow,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("＋ 下に頁追加", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (uiState.totalPages > 1) Color(0xFFFEE2E2) else Color(0xFFE2E8F0))
                        .clickable { onRequestDeleteCurrentPage() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "頁削除",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.totalPages > 1) Color(0xFFDC2626) else Color.Gray
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (uiState.isStylusOnlyMode) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                    .clickable { onToggleStylusOnly() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (uiState.isStylusOnlyMode) "ペン専用" else "指描画OK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isStylusOnlyMode) Color(0xFF166534) else Color.DarkGray
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFEF3C7))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(6.dp))
                    .clickable { onOpenStylusKeyConfig() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ペン釦設定",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
            }

            Box {
                OutlinedButton(
                    onClick = { onToggleTemplateMenu(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(uiState.activeDocument.template.label, fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = showTemplateMenu,
                    onDismissRequest = { onToggleTemplateMenu(false) }
                ) {
                    BackgroundTemplate.entries.forEach { tmpl ->
                        DropdownMenuItem(
                            text = { Text(tmpl.label, fontSize = 13.sp) },
                            onClick = { onSelectTemplate(tmpl) }
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onExportPdf,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("PDF出力", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onChangeFolder,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "保存先フォルダ変更",
                    modifier = Modifier.size(18.dp),
                    tint = if (uiState.savedExportTreeUri != null) Color(0xFF2563EB) else Color.Gray
                )
            }

            TextButton(
                onClick = onHideInfoBar,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("▲ 隠す", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 常時表示スリムバー (v3.2)
 * - 黒色を選択した際も一目で分かるよう、選択中のカラー丸に「外側ブルー枠＋内側白枠＋中央の白チェックマーク(✓)」を表示
 */
@Composable
fun AlwaysVisibleSlimTopBar(
    uiState: NoteUiState,
    showInfoBar: Boolean,
    showPenBoxBar: Boolean,
    onToggleInfoBar: () -> Unit,
    onZoomMinus: () -> Unit,
    onZoomPlus: () -> Unit,
    onZoomResetFit: () -> Unit,
    onToggleZoomLock: () -> Unit,
    onSelectTool: (ToolType) -> Unit,
    onSelectPenColor: (Int) -> Unit,
    onSelectPenWidth: (Float) -> Unit,
    onToggleDashedLine: (Boolean) -> Unit,
    onSelectHlColor: (Int) -> Unit,
    onSelectShapeMode: (ShapeMode) -> Unit,
    onSelectEraserMode: (EraserMode) -> Unit,
    onSelectEraserRadius: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePenBox: () -> Unit,
    onRequestClearPage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (showInfoBar) Color(0xFFE2E8F0) else Color(0xFFF1F5F9))
                    .clickable { onToggleInfoBar() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (showInfoBar) "▲ メニュー" else "▼ メニュー",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (uiState.isZoomLocked) Color(0xFFDBEAFE) else Color(0xFFF8FAFC))
                    .border(
                        width = 1.dp,
                        color = if (uiState.isZoomLocked) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onZoomMinus() }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "縮小", modifier = Modifier.size(14.dp), tint = Color(0xFF1E293B))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleZoomLock() }
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isZoomLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "ズーム固定切替",
                        modifier = Modifier.size(12.dp),
                        tint = if (uiState.isZoomLocked) Color(0xFF1E40AF) else Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${uiState.zoomPercent}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isZoomLocked) Color(0xFF1E40AF) else Color(0xFF1E293B)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onZoomPlus() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "拡大", modifier = Modifier.size(14.dp), tint = Color(0xFF1E293B))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onZoomResetFit() }
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "100%フィット", modifier = Modifier.size(14.dp), tint = Color(0xFF2563EB))
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            LabelToolPill(
                selected = uiState.currentTool == ToolType.PEN,
                label = "ペン",
                colorDot = Color(uiState.selectedPenColorInt),
                onClick = { onSelectTool(ToolType.PEN) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.HIGHLIGHTER,
                label = "マーカー",
                colorDot = Color(uiState.selectedHlColorInt),
                onClick = { onSelectTool(ToolType.HIGHLIGHTER) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.LINE_SHAPE,
                label = "図形",
                onClick = { onSelectTool(ToolType.LINE_SHAPE) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.XY_AXES,
                label = "XY軸",
                onClick = { onSelectTool(ToolType.XY_AXES) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.LASER_POINTER,
                label = "ポインター",
                activeTextColor = Color(0xFFE11D48),
                activeBgColor = Color(0xFFFFE4E6),
                onClick = { onSelectTool(ToolType.LASER_POINTER) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.STROKE_ERASER,
                label = "消しゴム",
                activeTextColor = Color(0xFFDC2626),
                activeBgColor = Color(0xFFFEE2E2),
                onClick = { onSelectTool(ToolType.STROKE_ERASER) }
            )
            LabelToolPill(
                selected = uiState.currentTool == ToolType.LASSO,
                label = "投げ縄",
                onClick = { onSelectTool(ToolType.LASSO) }
            )

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            if (uiState.currentTool == ToolType.PEN ||
                uiState.currentTool == ToolType.LINE_SHAPE ||
                uiState.currentTool == ToolType.XY_AXES
            ) {
                if (uiState.currentTool == ToolType.LINE_SHAPE) {
                    listOf(
                        ShapeMode.LINE,
                        ShapeMode.ARROW,
                        ShapeMode.RECTANGLE,
                        ShapeMode.CIRCLE,
                        ShapeMode.PARABOLA
                    ).forEach { mode ->
                        val isSel = uiState.selectedShapeMode == mode
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF1D4ED8) else Color.DarkGray,
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) Color(0xFFDBEAFE) else Color(0xFFF1F5F9))
                                .clickable { onSelectShapeMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                }

                // 実線 / 点線
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                        .padding(1.dp)
                ) {
                    Text(
                        text = "実線",
                        fontSize = 11.sp,
                        fontWeight = if (!uiState.isDashedLine) FontWeight.Bold else FontWeight.Normal,
                        color = if (!uiState.isDashedLine) Color(0xFF1E40AF) else Color.Gray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (!uiState.isDashedLine) Color(0xFFDBEAFE) else Color.Transparent)
                            .clickable { onToggleDashedLine(false) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "点線",
                        fontSize = 11.sp,
                        fontWeight = if (uiState.isDashedLine) FontWeight.Bold else FontWeight.Normal,
                        color = if (uiState.isDashedLine) Color(0xFF1E40AF) else Color.Gray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (uiState.isDashedLine) Color(0xFFDBEAFE) else Color.Transparent)
                            .clickable { onToggleDashedLine(true) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // ペンカラーパレット（黒を選んだ時も白チェックマーク＋二重リングでくっきり判別可能）
                PenColor.ALL_PEN_COLORS.forEach { color ->
                    val isSelected = uiState.selectedPenColorInt == color.colorInt
                    ColorSwatchButton(
                        colorInt = color.colorInt,
                        isSelected = isSelected,
                        checkTint = Color.White,
                        onClick = { onSelectPenColor(color.colorInt) }
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                listOf(2.6f to "細", 4.2f to "中", 7.0f to "太").forEach { (width, label) ->
                    val isSel = uiState.selectedPenWidth == width
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) Color(0xFFE2E8F0) else Color.Transparent)
                            .clickable { onSelectPenWidth(width) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
            } else if (uiState.currentTool == ToolType.HIGHLIGHTER) {
                PenColor.ALL_HL_COLORS.forEach { color ->
                    val isSelected = uiState.selectedHlColorInt == color.colorInt
                    ColorSwatchButton(
                        colorInt = color.colorInt,
                        isSelected = isSelected,
                        checkTint = Color(0xFF0F172A),
                        onClick = { onSelectHlColor(color.colorInt) }
                    )
                }
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
            } else if (uiState.currentTool == ToolType.STROKE_ERASER) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                        .padding(1.dp)
                ) {
                    EraserMode.entries.forEach { mode ->
                        val isSel = uiState.eraserMode == mode
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color(0xFFDC2626) else Color.Gray,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) Color(0xFFFEE2E2) else Color.Transparent)
                                .clickable { onSelectEraserMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                listOf(
                    7f to "極細",
                    14f to "細",
                    24f to "中",
                    40f to "太"
                ).forEach { (radius, label) ->
                    val isSel = uiState.eraserRadius == radius
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color(0xFFDC2626) else Color.Gray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) Color(0xFFFEE2E2) else Color.Transparent)
                            .clickable { onSelectEraserRadius(radius) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
            }

            IconButton(
                onClick = onUndo,
                enabled = uiState.canUndo,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "元に戻す", modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = onRedo,
                enabled = uiState.canRedo,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "やり直し", modifier = Modifier.size(17.dp))
            }

            Spacer(modifier = Modifier.width(3.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (showPenBoxBar) Color(0xFFEDE9FE) else Color(0xFFF1F5F9))
                    .clickable { onTogglePenBox() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ペン箱",
                    fontSize = 11.sp,
                    fontWeight = if (showPenBoxBar) FontWeight.Bold else FontWeight.Medium,
                    color = if (showPenBoxBar) Color(0xFF6D28D9) else Color.DarkGray
                )
            }

            IconButton(
                onClick = onRequestClearPage,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "現在ページ全消去", modifier = Modifier.size(17.dp), tint = Color.Gray)
            }
        }
    }
}

/**
 * 黒などの暗い色でも選択状態が一目で分かるカラースウォッチボタン
 * （選択時は外側に鮮やかな青枠＋内側に白枠＋中央にチェックマークを表示）
 */
@Composable
fun ColorSwatchButton(
    colorInt: Int,
    isSelected: Boolean,
    checkTint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .padding(1.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                shape = CircleShape
            )
            .padding(if (isSelected) 2.dp else 1.dp)
            .clip(CircleShape)
            .background(Color(colorInt))
            .clickable { onClick() }
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "選択中",
                tint = checkTint,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun LabelToolPill(
    selected: Boolean,
    label: String,
    colorDot: Color? = null,
    activeTextColor: Color = Color(0xFF1E40AF),
    activeBgColor: Color = Color(0xFFDBEAFE),
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) activeBgColor else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        if (colorDot != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colorDot)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeTextColor else Color(0xFF475569)
        )
    }
}

@Composable
fun SlimPenBoxBar(
    presets: List<PenPreset>,
    activePresetId: String?,
    onSelectPreset: (PenPreset) -> Unit,
    onRegisterCurrent: () -> Unit,
    onDeletePreset: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(top = 3.dp)
            .shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC).copy(alpha = 0.96f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "ペン箱:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )

            presets.forEach { preset ->
                val isSelected = preset.id == activePresetId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFFDBEAFE) else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(start = 7.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(preset.colorInt))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = preset.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color(0xFF1E293B)
                    )
                    if (presets.size > 1) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "削除",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onDeletePreset(preset.id) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEFF6FF))
                    .clickable { onRegisterCurrent() }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("＋ 追加", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }
        }
    }
}

/**
 * 投げ縄（Lasso）操作バー (v3.2):
 * - コピー・カット後、別の場所をタップしただけではいきなりペーストせず、
 *   このバーの「ペースト」ボタンを押したときに貼り付けを実行し、貼り付け後も選択を維持する
 */
@Composable
fun LassoSelectionActionBar(
    hasSelection: Boolean,
    canPaste: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(
        modifier = Modifier.shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasSelection) {
                TextButton(
                    onClick = onCopy,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("コピー", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onCut,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFDE047)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("カット", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (canPaste) {
                Button(
                    onClick = onPaste,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("ペースト", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (hasSelection) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFCA5A5)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("削除", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onClearSelection,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("解除", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StylusButtonConfigDialog(
    customKeyCode: Int,
    detectedInfo: String?,
    onTestToggleEraser: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = Color(0xFF2563EB))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ペンボタン（腹のキー）診断・登録", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "この画面を開いた状態で、専用ペンの腹にあるボタンを押してみてください。信号が届くと下に表示され、自動的に「消しゴム切替ボタン」として記憶されます。",
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B)
                )

                Surface(
                    color = if (detectedInfo != null) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = detectedInfo ?: "待機中... ペンのボタンを押してください（現在登録KeyCode: ${if (customKeyCode > 0) customKeyCode else "標準自動検知[92/93/308-311]"}）",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (detectedInfo != null) Color(0xFF166534) else Color(0xFF475569)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完了")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onTestToggleEraser) {
                Text("手動で切替テスト")
            }
        }
    )
}

@Composable
fun TutorNamingDialog(
    initialStudent: String,
    initialDate: String,
    initialDayOfWeek: String,
    initialSubject: String,
    recentStudents: List<String>,
    recentSubjects: List<String>,
    expiryDays: Int,
    isNewNoteMode: Boolean,
    onUpdateExpiryDays: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (student: String, dateStr: String, dayOfWeek: String, subject: String, createAsNew: Boolean) -> Unit
) {
    var student by remember { mutableStateOf(initialStudent) }
    var dateStr by remember { mutableStateOf(initialDate) }
    var dayOfWeek by remember { mutableStateOf(initialDayOfWeek) }
    var subject by remember { mutableStateOf(initialSubject) }
    var createAsNew by remember { mutableStateOf(isNewNoteMode) }

    val previewName = "${student.ifEmpty { "未設定" }}_${dateStr}(${dayOfWeek})_${subject.ifEmpty { "教科" }}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ノート名設定（生徒名・日・曜日・教科）",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "プレビュー: $previewName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF2563EB)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !createAsNew,
                            onClick = { createAsNew = false },
                            label = { Text("現在のノート名を更新", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = createAsNew,
                            onClick = { createAsNew = true },
                            label = { Text("新規白紙ノートとして作成", fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = student,
                            onValueChange = { student = it },
                            label = { Text("① 生徒名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (recentStudents.isNotEmpty()) {
                            Text(
                                text = "過去の生徒からワンタップ選択（${expiryDays}日未使用で自動消去）:",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                recentStudents.forEach { name ->
                                    SuggestionChip(
                                        onClick = { student = name },
                                        label = { Text(name, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("未使用生徒の消去:", fontSize = 11.sp, color = Color.Gray)
                            listOf(60 to "2ヶ月", 90 to "3ヶ月", 180 to "半年").forEach { (days, label) ->
                                FilterChip(
                                    selected = expiryDays == days,
                                    onClick = { onUpdateExpiryDays(days) },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("② 教科 (数学・英語・物理など)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "教科をワンタップ選択:",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentSubjects.forEach { subj ->
                                SuggestionChip(
                                    onClick = { subject = subj },
                                    label = { Text(subj, fontSize = 12.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = dateStr,
                                onValueChange = { dateStr = it },
                                label = { Text("③ 日付 (自動入力)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dayOfWeek,
                                onValueChange = { dayOfWeek = it },
                                label = { Text("曜日") },
                                singleLine = true,
                                modifier = Modifier.width(72.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("月", "火", "水", "木", "金", "土", "日").forEach { d ->
                                FilterChip(
                                    selected = dayOfWeek == d,
                                    onClick = { dayOfWeek = d },
                                    label = { Text(d, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(student, dateStr, dayOfWeek, subject, createAsNew) }
                    ) {
                        Text(if (createAsNew) "新規ノートを作成して開く" else "この名前で保存")
                    }
                }
            }
        }
    }
}

@Composable
fun StudentGroupedNotesDialog(
    noteSummaries: List<NoteSummary>,
    activeDocId: String,
    onSelectDocument: (String) -> Unit,
    onCreateNewNoteForStudent: (String) -> Unit,
    onRequestDeleteDocument: (NoteSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedByStudent = remember(noteSummaries) {
        noteSummaries.groupBy { it.metadata.studentName.ifEmpty { "未設定" } }
    }
    val expandedStudentMap = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生徒別ノート一覧（タップでプルダウン開閉）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                FilledTonalButton(
                    onClick = { onCreateNewNoteForStudent("") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("＋ 新規ノート", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedByStudent.forEach { (studentName, summaries) ->
                    val isExpanded = expandedStudentMap[studentName] == true
                    item(key = "dialog_student_$studentName") {
                        StudentPullDownFolderCard(
                            studentName = studentName,
                            summaries = summaries,
                            activeDocId = activeDocId,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedStudentMap[studentName] = !isExpanded
                            },
                            onSelectNote = onSelectDocument,
                            onCreateNewForStudent = { onCreateNewNoteForStudent(studentName) },
                            onExportPdf = null,
                            onDeleteNote = onRequestDeleteDocument
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
