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

/*
 * ホーム画面とノート一覧（生徒別フォルダ）。
 * v3.2 の画面構成をそのまま使っている。
 */

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
fun StudentGroupedNotesDialog(
    noteSummaries: List<NoteSummary>,
    activeDocId: String,
    currentStudent: String,
    onSelectDocument: (String) -> Unit,
    onCreateNewNoteForStudent: (String) -> Unit,
    onRequestDeleteDocument: (NoteSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val currentKey = currentStudent.ifEmpty { "未設定" }
    val groupedByStudent = remember(noteSummaries, currentKey) {
        // 今授業している生徒のノートを一番上に出す
        noteSummaries.groupBy { it.metadata.studentName.ifEmpty { "未設定" } }
            .toList()
            .sortedByDescending { it.first == currentKey }
    }
    val expandedStudentMap = remember { mutableStateMapOf(currentKey to true) }

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
                    Text("ノートを切り替える", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
