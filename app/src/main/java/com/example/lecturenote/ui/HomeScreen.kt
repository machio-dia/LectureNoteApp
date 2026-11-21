package com.example.lecturenote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.NoteSummary
import com.example.lecturenote.ui.theme.AppColors
import com.example.lecturenote.viewmodel.NoteUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面。
 * 左：授業をはじめる（続きから／新しいノート）　右：これまでのノート
 */
@Composable
fun HomeScreen(
    uiState: NoteUiState,
    onResumeActiveNote: () -> Unit,
    onStartLesson: (student: String, subject: String, template: BackgroundTemplate) -> Unit,
    onOpenDetailedNewNote: (prefillStudent: String) -> Unit,
    onOpenNote: (docId: String) -> Unit,
    onExportPdf: (docId: String) -> Unit,
    onDeleteNote: (NoteSummary) -> Unit,
    onOpenSettings: () -> Unit
) {
    val todayFormatted = remember { SimpleDateFormat("M月d日 (E)", Locale.JAPAN).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .navigationBarsPadding()
    ) {
        // ヘッダー
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("指導ノート", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text("今日は $todayFormatted", fontSize = 14.sp, color = AppColors.InkSoft)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = AppColors.InkSoft, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("設定", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.InkSoft)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 820.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(420.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ResumeCard(uiState, onResumeActiveNote)
                        // 開始ボタンは常に見えるよう、カードの下端に固定する
                        NewLessonCard(
                            uiState,
                            onStartLesson,
                            onOpenDetailedNewNote,
                            pinStartButton = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    NotesPane(
                        uiState = uiState,
                        onOpenNote = onOpenNote,
                        onExportPdf = onExportPdf,
                        onDeleteNote = onDeleteNote,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // 幅が狭いとき（分割表示など）は縦に並べる
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NotesPane(
                        uiState = uiState,
                        onOpenNote = onOpenNote,
                        onExportPdf = onExportPdf,
                        onDeleteNote = onDeleteNote,
                        header = {
                            ResumeCard(uiState, onResumeActiveNote)
                            NewLessonCard(uiState, onStartLesson, onOpenDetailedNewNote)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Line)
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.InkSoft)
}

@Composable
private fun SubjectBadge(subject: String, size: Int = 44) {
    val color = AppColors.subjectColor(subject)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(color.copy(alpha = 0.13f))
    ) {
        Text(
            text = subject.trim().take(1).ifEmpty { "?" },
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** 直前まで開いていたノートをワンタップで再開 */
@Composable
private fun ResumeCard(uiState: NoteUiState, onResume: () -> Unit) {
    val doc = uiState.activeDocument
    val meta = doc.metadata
    HomeCard {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("前回の続きから")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(meta.subject, size = 48)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${meta.studentName.ifBlank { "未設定" }} さん ・ ${meta.subject}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${meta.dateStr}${if (meta.dayOfWeek.isNotBlank()) "(${meta.dayOfWeek})" else ""} ・ ${doc.pages.size}ページ",
                        fontSize = 13.sp,
                        color = AppColors.InkSoft
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = onResume, shape = RoundedCornerShape(14.dp), color = AppColors.AccentSoft) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("続きを書く", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.AccentDeep)
                    }
                }
            }
        }
    }
}

/** 今日の授業ノートを 生徒 → 教科 → スタート の順で作る */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLessonCard(
    uiState: NoteUiState,
    onStartLesson: (student: String, subject: String, template: BackgroundTemplate) -> Unit,
    onOpenDetailedNewNote: (prefillStudent: String) -> Unit,
    pinStartButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val defaultStudent = remember(uiState.recentStudents) {
        uiState.recentStudents.firstOrNull() ?: uiState.activeDocument.metadata.studentName
    }
    var student by remember(defaultStudent) { mutableStateOf(defaultStudent) }
    var subject by remember { mutableStateOf(uiState.activeDocument.metadata.subject.ifEmpty { "数学" }) }
    var template by remember { mutableStateOf(uiState.activeDocument.template) }

    HomeCard(modifier = modifier) {
      Column {
        Column(
            modifier = Modifier
                .then(if (pinStartButton) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("今日の授業をはじめる", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("だれの授業？")
                if (uiState.recentStudents.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.recentStudents.forEach { name ->
                            ChoiceChip(label = name, selected = student == name, onClick = { student = name })
                        }
                    }
                }
                OutlinedTextField(
                    value = student,
                    onValueChange = { student = it },
                    placeholder = { Text("生徒の名前を入力") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("教科")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val subjects = remember(uiState.recentSubjects, subject) {
                        if (subject.isNotBlank() && subject !in uiState.recentSubjects) {
                            listOf(subject) + uiState.recentSubjects
                        } else {
                            uiState.recentSubjects
                        }
                    }
                    subjects.forEach { s ->
                        ChoiceChip(label = s, selected = subject == s, onClick = { subject = s })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("用紙")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackgroundTemplate.entries.forEach { t ->
                        ChoiceChip(label = t.shortLabel, selected = template == t, onClick = { template = t })
                    }
                }
            }

        }
        if (pinStartButton) HorizontalDivider(color = AppColors.Line)
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { onStartLesson(student.ifBlank { "未設定" }, subject.ifBlank { "数学" }, template) },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${student.ifBlank { "未設定" }} さんの${subject}をはじめる",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = { onOpenDetailedNewNote(student) }) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("日付や教科名を自分で指定して作る", fontSize = 13.sp)
            }
        }
      }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, count: Int? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AppColors.Accent else AppColors.Surface,
        border = if (selected) null else BorderStroke(1.dp, AppColors.LineStrong)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else AppColors.Ink,
                maxLines = 1
            )
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White.copy(alpha = 0.8f) else AppColors.InkFaint
                )
            }
        }
    }
}

/** これまでのノート：生徒で絞り込み → タップで開く */
@Composable
private fun NotesPane(
    uiState: NoteUiState,
    onOpenNote: (String) -> Unit,
    onExportPdf: (String) -> Unit,
    onDeleteNote: (NoteSummary) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var studentFilterRaw by remember { mutableStateOf<String?>(null) }

    val studentCounts = remember(uiState.noteSummaries) {
        uiState.noteSummaries
            .groupingBy { it.metadata.studentName.ifBlank { "未設定" } }
            .eachCount()
            .toList()
    }
    // 絞り込み中の生徒のノートが全部なくなったら「すべて」扱いに戻す
    val studentFilter = studentFilterRaw?.takeIf { f -> studentCounts.any { it.first == f } }

    val filtered = remember(uiState.noteSummaries, searchQuery, studentFilter) {
        val q = searchQuery.trim().lowercase()
        uiState.noteSummaries.filter { s ->
            val name = s.metadata.studentName.ifBlank { "未設定" }
            (studentFilter == null || name == studentFilter) &&
                (q.isEmpty() ||
                    name.lowercase().contains(q) ||
                    s.metadata.subject.lowercase().contains(q) ||
                    s.metadata.dateStr.contains(q))
        }
    }

    val paneContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (header != null) {
                    item(key = "__header") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) { header() }
                    }
                }
                item(key = "__controls") {
                    Column(
                        modifier = Modifier
                            .then(if (header == null) Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp) else Modifier)
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "これまでのノート",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Ink,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${filtered.size} 冊", fontSize = 13.sp, color = AppColors.InkSoft)
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("生徒名・教科・日付でさがす") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    BarIconButton(Icons.Rounded.Close, "検索をクリア", { searchQuery = "" })
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (studentCounts.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                ChoiceChip(
                                    label = "すべて",
                                    selected = studentFilter == null,
                                    count = uiState.noteSummaries.size,
                                    onClick = { studentFilterRaw = null }
                                )
                                studentCounts.forEach { (name, count) ->
                                    ChoiceChip(
                                        label = name,
                                        selected = studentFilter == name,
                                        count = count,
                                        onClick = { studentFilterRaw = if (studentFilter == name) null else name }
                                    )
                                }
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "__empty") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp)
                        ) {
                            Text(
                                if (uiState.noteSummaries.isEmpty()) {
                                    "まだノートがありません。\n左の「今日の授業をはじめる」から作成できます。"
                                } else {
                                    "条件に合うノートが見つかりません"
                                },
                                fontSize = 14.sp,
                                color = AppColors.InkSoft,
                                lineHeight = 22.sp
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { summary ->
                        NoteRow(
                            summary = summary,
                            isActive = summary.id == uiState.activeDocument.id,
                            horizontalPadding = if (header == null) 12 else 0,
                            onOpen = { onOpenNote(summary.id) },
                            onExportPdf = { onExportPdf(summary.id) },
                            onDelete = { onDeleteNote(summary) }
                        )
                    }
                }
            }
        }
    }

    if (header == null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.Line)
        ) { paneContent() }
    } else {
        Box(modifier = modifier) { paneContent() }
    }
}

@Composable
private fun NoteRow(
    summary: NoteSummary,
    isActive: Boolean,
    horizontalPadding: Int,
    onOpen: () -> Unit,
    onExportPdf: () -> Unit,
    onDelete: () -> Unit
) {
    val meta = summary.metadata
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) AppColors.AccentSoft.copy(alpha = 0.55f) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 2.dp, top = 10.dp, bottom = 10.dp)
    ) {
        SubjectBadge(meta.subject)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${meta.studentName.ifBlank { "未設定" }} さん",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    meta.subject.ifBlank { "教科なし" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.subjectColor(meta.subject),
                    maxLines = 1
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.Accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("前回開いたノート", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Text(
                "${meta.dateStr}${if (meta.dayOfWeek.isNotBlank()) "(${meta.dayOfWeek})" else ""} ・ ${summary.pageCount}ページ ・ ${summary.template.shortLabel}",
                fontSize = 13.sp,
                color = AppColors.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            BarIconButton(Icons.Rounded.MoreVert, "このノートのメニュー", { menuOpen = true }, tint = AppColors.InkSoft)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuRow(Icons.Rounded.Edit, "開く") {
                    menuOpen = false
                    onOpen()
                }
                MenuRow(Icons.Rounded.PictureAsPdf, "PDFに書き出す") {
                    menuOpen = false
                    onExportPdf()
                }
                MenuRow(Icons.Rounded.Delete, "削除", danger = true) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}
