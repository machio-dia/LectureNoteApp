package com.example.lecturenote.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lecturenote.data.NoteRepository
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.EraserMode
import com.example.lecturenote.model.LectureMetadata
import com.example.lecturenote.model.LectureNoteDocument
import com.example.lecturenote.model.NoteSummary
import com.example.lecturenote.model.Page
import com.example.lecturenote.model.PenColor
import com.example.lecturenote.model.PenPreset
import com.example.lecturenote.model.ShapeMode
import com.example.lecturenote.model.ToolType
import com.example.lecturenote.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NoteUiState(
    val isShowingLandingPage: Boolean = true,
    val activeDocument: LectureNoteDocument,
    val noteSummaries: List<NoteSummary> = emptyList(),
    val recentStudents: List<String> = emptyList(),
    val recentSubjects: List<String> = emptyList(),
    val penPresets: List<PenPreset> = emptyList(),
    val activePresetId: String? = "preset_black_med",
    val currentTool: ToolType = ToolType.PEN,
    val previousWritingTool: ToolType = ToolType.PEN,
    val selectedShapeMode: ShapeMode = ShapeMode.LINE,
    val eraserMode: EraserMode = EraserMode.PARTIAL,
    val eraserRadius: Float = 14f,
    val selectedPenColorInt: Int = PenColor.BLACK.colorInt,
    val selectedPenWidth: Float = 4.2f,
    val isDashedLine: Boolean = false,
    val axesOriginFx: Float = 0.5f,
    val axesOriginFy: Float = 0.5f,
    val selectedHlColorInt: Int = PenColor.HL_YELLOW.colorInt,
    val selectedHlWidth: Float = 24.0f,
    val isStylusOnlyMode: Boolean = true,
    val isZoomLocked: Boolean = false,
    val zoomPercent: Int = 100,
    val currentVisiblePage: Int = 1,
    val totalPages: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasLassoSelection: Boolean = false,
    val canPasteClipboard: Boolean = false,
    val savedExportTreeUri: String? = null,
    val studentExpiryDays: Int = 90,
    val customStylusKeyCode: Int = -1,
    val isListeningForStylusKey: Boolean = false,
    val lastDetectedStylusKeyInfo: String? = null,
    val notificationMessage: String? = null
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application.applicationContext)
    private var autoSaveJob: Job? = null

    private val _uiState: MutableStateFlow<NoteUiState>
    val uiState: StateFlow<NoteUiState>

    init {
        val summaries = repository.loadAllNoteSummaries()
        val initialDoc = if (summaries.isNotEmpty()) {
            repository.loadNoteDocumentById(summaries.first().id)
                ?: createDefaultDocument(studentName = "サンプル生徒")
        } else {
            createDefaultDocument(studentName = "サンプル生徒").also {
                repository.saveNoteDocument(it)
            }
        }

        val updatedSummaries = repository.loadAllNoteSummaries()
        val presets = repository.loadPenPresets()

        _uiState = MutableStateFlow(
            NoteUiState(
                isShowingLandingPage = true,
                activeDocument = initialDoc,
                noteSummaries = updatedSummaries,
                recentStudents = repository.getRecentStudents(),
                recentSubjects = repository.getRecentSubjects(),
                penPresets = presets,
                totalPages = initialDoc.pages.size,
                savedExportTreeUri = repository.getSavedExportTreeUri(),
                studentExpiryDays = repository.getStudentExpiryDays(),
                customStylusKeyCode = repository.getCustomStylusKeyCode()
            )
        )
        uiState = _uiState.asStateFlow()
    }

    private fun createDefaultDocument(
        studentName: String,
        subject: String = "数学",
        template: BackgroundTemplate = BackgroundTemplate.RULED_7MM
    ): LectureNoteDocument {
        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(now)
        val dayOfWeek = SimpleDateFormat("E", Locale.JAPAN).format(now)
        return LectureNoteDocument(
            metadata = LectureMetadata(
                studentName = studentName,
                dateStr = dateStr,
                dayOfWeek = dayOfWeek,
                subject = subject
            ),
            template = template,
            pages = mutableListOf(Page(1))
        )
    }

    fun openLandingPage() {
        flushPendingSaveAsync()
        _uiState.value = _uiState.value.copy(
            isShowingLandingPage = true,
            hasLassoSelection = false
        )
    }

    fun resumeActiveNote() {
        _uiState.value = _uiState.value.copy(isShowingLandingPage = false)
    }

    private fun isWritingTool(tool: ToolType): Boolean {
        return tool == ToolType.PEN ||
            tool == ToolType.HIGHLIGHTER ||
            tool == ToolType.LINE_SHAPE ||
            tool == ToolType.XY_AXES
    }

    fun setTool(tool: ToolType) {
        val prevWriting = if (isWritingTool(tool)) {
            tool
        } else {
            _uiState.value.previousWritingTool
        }
        _uiState.value = _uiState.value.copy(
            currentTool = tool,
            previousWritingTool = prevWriting,
            activePresetId = null
        )
    }

    fun setEraserMode(mode: EraserMode) {
        _uiState.value = _uiState.value.copy(
            eraserMode = mode,
            currentTool = ToolType.STROKE_ERASER,
            activePresetId = null
        )
    }

    fun setEraserRadius(radius: Float) {
        _uiState.value = _uiState.value.copy(
            eraserRadius = radius,
            currentTool = ToolType.STROKE_ERASER,
            activePresetId = null
        )
    }

    fun setDashedLine(dashed: Boolean) {
        val current = _uiState.value
        val nextTool = when (current.currentTool) {
            ToolType.LINE_SHAPE -> ToolType.LINE_SHAPE
            ToolType.XY_AXES -> ToolType.XY_AXES
            else -> ToolType.PEN
        }
        _uiState.value = current.copy(
            isDashedLine = dashed,
            currentTool = nextTool,
            previousWritingTool = nextTool,
            activePresetId = null
        )
    }

    fun toggleEraserByStylusButton() {
        val state = _uiState.value
        if (state.isShowingLandingPage) return
        if (state.currentTool == ToolType.STROKE_ERASER) {
            val restoreTool = state.previousWritingTool
            _uiState.value = state.copy(currentTool = restoreTool)
        } else {
            val currentWriting = if (isWritingTool(state.currentTool)) {
                state.currentTool
            } else {
                state.previousWritingTool
            }
            _uiState.value = state.copy(
                currentTool = ToolType.STROKE_ERASER,
                previousWritingTool = currentWriting
            )
        }
    }

    fun setListeningForStylusKey(listening: Boolean) {
        _uiState.value = _uiState.value.copy(
            isListeningForStylusKey = listening,
            lastDetectedStylusKeyInfo = if (listening) null else _uiState.value.lastDetectedStylusKeyInfo
        )
    }

    fun onStylusKeyLearned(keyCode: Int, scanCode: Int, deviceName: String) {
        repository.saveCustomStylusKeyCode(keyCode)
        val info = "検知成功！ KeyCode=$keyCode (ScanCode=$scanCode, Device=$deviceName) を消しゴム切替ボタンとして登録しました。"
        _uiState.value = _uiState.value.copy(
            customStylusKeyCode = keyCode,
            lastDetectedStylusKeyInfo = info
        )
    }

    fun setPenColor(colorInt: Int) {
        val current = _uiState.value
        val nextTool = when (current.currentTool) {
            ToolType.LINE_SHAPE -> ToolType.LINE_SHAPE
            ToolType.XY_AXES -> ToolType.XY_AXES
            else -> ToolType.PEN
        }
        _uiState.value = current.copy(
            selectedPenColorInt = colorInt,
            currentTool = nextTool,
            previousWritingTool = nextTool,
            activePresetId = null
        )
    }

    fun setPenWidth(width: Float) {
        val current = _uiState.value
        val nextTool = when (current.currentTool) {
            ToolType.LINE_SHAPE -> ToolType.LINE_SHAPE
            ToolType.XY_AXES -> ToolType.XY_AXES
            else -> ToolType.PEN
        }
        _uiState.value = current.copy(
            selectedPenWidth = width,
            currentTool = nextTool,
            previousWritingTool = nextTool,
            activePresetId = null
        )
    }

    fun setHighlighterColor(colorInt: Int) {
        _uiState.value = _uiState.value.copy(
            selectedHlColorInt = colorInt,
            currentTool = ToolType.HIGHLIGHTER,
            previousWritingTool = ToolType.HIGHLIGHTER,
            activePresetId = null
        )
    }

    fun setShapeMode(mode: ShapeMode) {
        _uiState.value = _uiState.value.copy(
            selectedShapeMode = mode,
            currentTool = ToolType.LINE_SHAPE,
            previousWritingTool = ToolType.LINE_SHAPE,
            activePresetId = null
        )
    }

    /** これから描くXY軸の原点位置（枠の左上=0,0 / 右下=1,1） */
    fun setAxesOrigin(fx: Float, fy: Float) {
        _uiState.value = _uiState.value.copy(
            axesOriginFx = fx,
            axesOriginFy = fy,
            currentTool = ToolType.XY_AXES,
            previousWritingTool = ToolType.XY_AXES,
            activePresetId = null
        )
    }

    fun selectPenPreset(preset: PenPreset) {
        val state = _uiState.value
        when (preset.toolType) {
            ToolType.HIGHLIGHTER -> {
                _uiState.value = state.copy(
                    currentTool = ToolType.HIGHLIGHTER,
                    previousWritingTool = ToolType.HIGHLIGHTER,
                    selectedHlColorInt = preset.colorInt,
                    selectedHlWidth = preset.strokeWidth,
                    isDashedLine = false,
                    activePresetId = preset.id
                )
            }
            ToolType.LINE_SHAPE -> {
                _uiState.value = state.copy(
                    currentTool = ToolType.LINE_SHAPE,
                    previousWritingTool = ToolType.LINE_SHAPE,
                    selectedPenColorInt = preset.colorInt,
                    selectedPenWidth = preset.strokeWidth,
                    selectedShapeMode = preset.shapeMode,
                    isDashedLine = preset.isDashed,
                    activePresetId = preset.id
                )
            }
            ToolType.XY_AXES -> {
                _uiState.value = state.copy(
                    currentTool = ToolType.XY_AXES,
                    previousWritingTool = ToolType.XY_AXES,
                    selectedPenColorInt = preset.colorInt,
                    selectedPenWidth = preset.strokeWidth,
                    isDashedLine = preset.isDashed,
                    activePresetId = preset.id
                )
            }
            else -> {
                _uiState.value = state.copy(
                    currentTool = ToolType.PEN,
                    previousWritingTool = ToolType.PEN,
                    selectedPenColorInt = preset.colorInt,
                    selectedPenWidth = preset.strokeWidth,
                    isDashedLine = preset.isDashed,
                    activePresetId = preset.id
                )
            }
        }
    }

    fun registerCurrentSettingToPenBox() {
        val state = _uiState.value
        val tool = when (state.currentTool) {
            ToolType.HIGHLIGHTER -> ToolType.HIGHLIGHTER
            ToolType.LINE_SHAPE -> ToolType.LINE_SHAPE
            ToolType.XY_AXES -> ToolType.XY_AXES
            else -> ToolType.PEN
        }
        val colorInt = if (tool == ToolType.HIGHLIGHTER) state.selectedHlColorInt else state.selectedPenColorInt
        val width = if (tool == ToolType.HIGHLIGHTER) state.selectedHlWidth else state.selectedPenWidth
        val dashed = tool != ToolType.HIGHLIGHTER && state.isDashedLine
        val colorName = (PenColor.ALL_PEN_COLORS + PenColor.ALL_HL_COLORS)
            .firstOrNull { it.colorInt == colorInt }?.name ?: "色"
        val sizeLabel = when {
            tool == ToolType.HIGHLIGHTER -> ""
            tool == ToolType.XY_AXES -> "・XY軸"
            tool == ToolType.LINE_SHAPE -> "・${state.selectedShapeMode.label}"
            width <= 3.0f -> "・細"
            width <= 5.0f -> "・中"
            else -> "・太"
        }
        val dashLabel = if (dashed) "(点線)" else ""
        val preset = PenPreset(
            title = "${colorName}${sizeLabel}${dashLabel}",
            toolType = tool,
            colorInt = colorInt,
            strokeWidth = width,
            shapeMode = if (tool == ToolType.XY_AXES) ShapeMode.XY_AXES_SHAPE else state.selectedShapeMode,
            isDashed = dashed
        )
        val updated = state.penPresets + preset
        repository.savePenPresets(updated)
        _uiState.value = state.copy(
            penPresets = updated,
            activePresetId = preset.id
        )
    }

    fun removePenPreset(presetId: String) {
        val updated = _uiState.value.penPresets.filterNot { it.id == presetId }
        repository.savePenPresets(updated)
        _uiState.value = _uiState.value.copy(penPresets = updated)
    }

    fun toggleZoomLock() {
        val nextLock = !_uiState.value.isZoomLocked
        _uiState.value = _uiState.value.copy(isZoomLocked = nextLock)
    }

    fun toggleStylusOnly() {
        val nextMode = !_uiState.value.isStylusOnlyMode
        _uiState.value = _uiState.value.copy(isStylusOnlyMode = nextMode)
    }

    fun setTemplate(template: BackgroundTemplate) {
        val doc = _uiState.value.activeDocument
        doc.template = template
        scheduleDebouncedSave()
        _uiState.value = _uiState.value.copy(activeDocument = doc)
    }

    fun addNewPageBelowCurrent(onAdded: (insertedPageIndex: Int) -> Unit) {
        val state = _uiState.value
        val doc = state.activeDocument
        val currentIdx = (state.currentVisiblePage - 1).coerceIn(0, doc.pages.lastIndex)
        val insertIndex = currentIdx + 1

        doc.pages.add(insertIndex, Page(pageNumber = insertIndex + 1))
        renumberPages(doc)

        scheduleDebouncedSave()
        _uiState.value = state.copy(
            activeDocument = doc,
            totalPages = doc.pages.size
        )
        onAdded(insertIndex)
    }

    fun deleteCurrentPage(onDeleted: () -> Unit) {
        val state = _uiState.value
        val doc = state.activeDocument
        if (doc.pages.size <= 1) {
            _uiState.value = state.copy(
                notificationMessage = "1ページのみのノートはページ削除できません（全消去をご利用ください）"
            )
            return
        }
        val removeIdx = (state.currentVisiblePage - 1).coerceIn(0, doc.pages.lastIndex)
        doc.pages.removeAt(removeIdx)
        renumberPages(doc)

        scheduleDebouncedSave()
        val newVisiblePage = removeIdx.coerceIn(0, doc.pages.lastIndex) + 1
        _uiState.value = state.copy(
            activeDocument = doc,
            currentVisiblePage = newVisiblePage,
            totalPages = doc.pages.size
        )
        onDeleted()
    }

    private fun renumberPages(doc: LectureNoteDocument) {
        val renumbered = doc.pages.mapIndexed { idx, p ->
            Page(
                pageNumber = idx + 1,
                strokes = p.strokes,
                undoStack = p.undoStack,
                redoStack = p.redoStack
            )
        }
        doc.pages.clear()
        doc.pages.addAll(renumbered)
    }

    fun syncViewportOnly(
        visiblePage: Int,
        relativeZoomPercent: Int
    ) {
        val state = _uiState.value
        val total = state.activeDocument.pages.size
        if (state.currentVisiblePage != visiblePage ||
            state.zoomPercent != relativeZoomPercent ||
            state.totalPages != total
        ) {
            _uiState.value = state.copy(
                currentVisiblePage = visiblePage,
                totalPages = total,
                zoomPercent = relativeZoomPercent
            )
        }
    }

    fun onCanvasContentModified(
        visiblePage: Int,
        relativeZoomPercent: Int,
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        val state = _uiState.value
        val total = state.activeDocument.pages.size
        if (state.currentVisiblePage != visiblePage ||
            state.zoomPercent != relativeZoomPercent ||
            state.canUndo != canUndo ||
            state.canRedo != canRedo ||
            state.totalPages != total
        ) {
            _uiState.value = state.copy(
                currentVisiblePage = visiblePage,
                totalPages = total,
                zoomPercent = relativeZoomPercent,
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
        scheduleDebouncedSave()
    }

    private fun scheduleDebouncedSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1200L)
            val snapshotDoc = createThreadSafeSnapshot(_uiState.value.activeDocument)
            val updatedSummaries = withContext(Dispatchers.IO) {
                repository.saveNoteDocument(snapshotDoc)
                repository.loadAllNoteSummaries()
            }
            _uiState.value = _uiState.value.copy(noteSummaries = updatedSummaries)
        }
    }

    fun flushPendingSaveAsync() {
        autoSaveJob?.cancel()
        val snapshotDoc = createThreadSafeSnapshot(_uiState.value.activeDocument)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNoteDocument(snapshotDoc)
            val summaries = repository.loadAllNoteSummaries()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(noteSummaries = summaries)
            }
        }
    }

    private fun createThreadSafeSnapshot(doc: LectureNoteDocument): LectureNoteDocument {
        val copiedPages = doc.pages.map { p ->
            Page(
                pageNumber = p.pageNumber,
                strokes = p.strokes.toMutableList()
            )
        }.toMutableList()
        return LectureNoteDocument(
            id = doc.id,
            metadata = doc.metadata.copy(),
            template = doc.template,
            pages = copiedPages,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun updateSelectionStatus(hasSelection: Boolean, canPaste: Boolean) {
        val state = _uiState.value
        if (state.hasLassoSelection != hasSelection || state.canPasteClipboard != canPaste) {
            _uiState.value = state.copy(
                hasLassoSelection = hasSelection,
                canPasteClipboard = canPaste
            )
        }
    }

    fun updateNoteMetadata(
        studentName: String,
        dateStr: String,
        dayOfWeek: String,
        subject: String
    ) {
        val doc = _uiState.value.activeDocument
        doc.metadata = LectureMetadata(
            studentName = studentName.trim().ifEmpty { "未設定" },
            dateStr = dateStr.trim(),
            dayOfWeek = dayOfWeek.trim(),
            subject = subject.trim().ifEmpty { "数学" }
        )
        val snapshot = createThreadSafeSnapshot(doc)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNoteDocument(snapshot)
            val summaries = repository.loadAllNoteSummaries()
            val students = repository.getRecentStudents()
            val subjects = repository.getRecentSubjects()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    activeDocument = doc,
                    noteSummaries = summaries,
                    recentStudents = students,
                    recentSubjects = subjects
                )
            }
        }
    }

    fun createNewNote(
        studentName: String,
        dateStr: String,
        dayOfWeek: String,
        subject: String,
        template: BackgroundTemplate = _uiState.value.activeDocument.template
    ) {
        val prevSnapshot = createThreadSafeSnapshot(_uiState.value.activeDocument)
        val newDoc = LectureNoteDocument(
            metadata = LectureMetadata(
                studentName = studentName.trim().ifEmpty { "未設定" },
                dateStr = dateStr.trim(),
                dayOfWeek = dayOfWeek.trim(),
                subject = subject.trim().ifEmpty { "数学" }
            ),
            template = template,
            pages = mutableListOf(Page(1))
        )
        _uiState.value = _uiState.value.copy(
            isShowingLandingPage = false,
            activeDocument = newDoc,
            currentVisiblePage = 1,
            totalPages = 1,
            canUndo = false,
            canRedo = false,
            hasLassoSelection = false,
            canPasteClipboard = false
        )
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNoteDocument(prevSnapshot)
            repository.saveNoteDocument(newDoc)
            val summaries = repository.loadAllNoteSummaries()
            val students = repository.getRecentStudents()
            val subjects = repository.getRecentSubjects()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    noteSummaries = summaries,
                    recentStudents = students,
                    recentSubjects = subjects
                )
            }
        }
    }

    fun switchNoteDocument(docId: String) {
        if (_uiState.value.activeDocument.id == docId) {
            _uiState.value = _uiState.value.copy(isShowingLandingPage = false)
            return
        }
        autoSaveJob?.cancel()
        val prevSnapshot = createThreadSafeSnapshot(_uiState.value.activeDocument)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNoteDocument(prevSnapshot)
            val target = repository.loadNoteDocumentById(docId) ?: return@launch
            val summaries = repository.loadAllNoteSummaries()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isShowingLandingPage = false,
                    activeDocument = target,
                    noteSummaries = summaries,
                    currentVisiblePage = 1,
                    totalPages = target.pages.size,
                    canUndo = false,
                    canRedo = false,
                    hasLassoSelection = false
                )
            }
        }
    }

    fun deleteNoteDocument(docId: String) {
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNoteDocument(docId)
            val remainingSummaries = repository.loadAllNoteSummaries()
            val currentActive = _uiState.value.activeDocument
            val nextActive = if (currentActive.id == docId) {
                val nextId = remainingSummaries.firstOrNull()?.id
                if (nextId != null) {
                    repository.loadNoteDocumentById(nextId) ?: createDefaultDocument("サンプル生徒").also {
                        repository.saveNoteDocument(it)
                    }
                } else {
                    createDefaultDocument("サンプル生徒").also {
                        repository.saveNoteDocument(it)
                    }
                }
            } else {
                currentActive
            }
            val finalSummaries = repository.loadAllNoteSummaries()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    // 開いていたノート自体を削除した場合は、別の生徒のノートを突然表示せずホームへ戻る
                    isShowingLandingPage = _uiState.value.isShowingLandingPage || currentActive.id == docId,
                    activeDocument = nextActive,
                    noteSummaries = finalSummaries,
                    currentVisiblePage = 1,
                    totalPages = nextActive.pages.size,
                    canUndo = false,
                    canRedo = false,
                    hasLassoSelection = false
                )
            }
        }
    }

    fun updateStudentExpiryDays(days: Int) {
        repository.setStudentExpiryDays(days)
        _uiState.value = _uiState.value.copy(
            studentExpiryDays = days,
            recentStudents = repository.getRecentStudents()
        )
    }

    /**
     * PDF保存先フォルダが選択されたとき。
     * exportDocId が指定されている場合のみ、そのノートを続けてPDF出力する
     * （保存先を設定しただけで勝手に書き出されることはない）。
     */
    fun onExportFolderSelected(context: Context, treeUri: Uri, exportDocId: String? = null) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {}

        val uriStr = treeUri.toString()
        repository.saveExportTreeUri(uriStr)
        _uiState.value = _uiState.value.copy(savedExportTreeUri = uriStr)

        if (exportDocId == null) {
            showMessage("PDFの保存先を設定しました")
        } else {
            exportNoteSummaryToPdf(context, exportDocId) {
                showMessage("PDFを書き出せませんでした。保存先フォルダを確認してください")
            }
        }
    }

    fun exportToRememberedFolderOrRequestPicker(
        context: Context,
        onNeedFolderPicker: () -> Unit
    ) {
        val savedUriStr = _uiState.value.savedExportTreeUri
        if (savedUriStr.isNullOrEmpty()) {
            onNeedFolderPicker()
            return
        }
        val treeUri = Uri.parse(savedUriStr)
        exportToTreeUri(context, _uiState.value.activeDocument, treeUri, onFailureFallbackPicker = onNeedFolderPicker)
    }

    fun exportNoteSummaryToPdf(
        context: Context,
        docId: String,
        onNeedFolderPicker: () -> Unit
    ) {
        val savedUriStr = _uiState.value.savedExportTreeUri
        if (savedUriStr.isNullOrEmpty()) {
            onNeedFolderPicker()
            return
        }
        val treeUri = Uri.parse(savedUriStr)
        viewModelScope.launch(Dispatchers.IO) {
            val doc = if (_uiState.value.activeDocument.id == docId) {
                createThreadSafeSnapshot(_uiState.value.activeDocument)
            } else {
                repository.loadNoteDocumentById(docId)
            }
            if (doc != null) {
                val res = PdfExporter.exportDocumentToTreeUri(context, doc, treeUri)
                withContext(Dispatchers.Main) {
                    if (res.success) {
                        _uiState.value = _uiState.value.copy(notificationMessage = res.message)
                    } else {
                        onNeedFolderPicker()
                    }
                }
            }
        }
    }

    private fun exportToTreeUri(
        context: Context,
        sourceDoc: LectureNoteDocument,
        treeUri: Uri,
        onFailureFallbackPicker: (() -> Unit)? = null
    ) {
        val snapshot = createThreadSafeSnapshot(sourceDoc)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNoteDocument(snapshot)
            val res = PdfExporter.exportDocumentToTreeUri(
                context = context,
                doc = snapshot,
                treeUri = treeUri
            )
            withContext(Dispatchers.Main) {
                if (res.success) {
                    _uiState.value = _uiState.value.copy(notificationMessage = res.message)
                } else if (onFailureFallbackPicker != null) {
                    onFailureFallbackPicker()
                } else {
                    _uiState.value = _uiState.value.copy(notificationMessage = res.message)
                }
            }
        }
    }

    fun showMessage(msg: String) {
        _uiState.value = _uiState.value.copy(notificationMessage = msg)
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notificationMessage = null)
    }
}
