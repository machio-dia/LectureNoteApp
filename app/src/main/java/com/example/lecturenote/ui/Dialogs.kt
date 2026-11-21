package com.example.lecturenote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DATE_PATTERN = "yyyy-MM-dd"

/** "2026-11-21" → "土"。日付として読めないときは null */
private fun dayOfWeekFor(dateStr: String): String? {
    return try {
        val fmt = SimpleDateFormat(DATE_PATTERN, Locale.JAPAN).apply { isLenient = false }
        val trimmed = dateStr.trim()
        if (trimmed.length != DATE_PATTERN.length) return null
        val date = fmt.parse(trimmed) ?: return null
        SimpleDateFormat("E", Locale.JAPAN).format(date)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.InkSoft)
}

/**
 * ノート情報（生徒・教科・日付）の入力。
 * 新規作成と、既存ノートの情報変更の両方で使う。曜日は日付から自動で決まる。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteInfoDialog(
    isNewNote: Boolean,
    initialStudent: String,
    initialSubject: String,
    initialDate: String,
    initialDayOfWeek: String,
    initialTemplate: BackgroundTemplate,
    recentStudents: List<String>,
    recentSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (student: String, dateStr: String, dayOfWeek: String, subject: String, template: BackgroundTemplate) -> Unit
) {
    var student by remember { mutableStateOf(initialStudent) }
    var subject by remember { mutableStateOf(initialSubject) }
    var dateStr by remember { mutableStateOf(initialDate) }
    var template by remember { mutableStateOf(initialTemplate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val computedDay = dayOfWeekFor(dateStr)
    // 日付を変えていない間は元の曜日を尊重し、読めない日付のときは曜日なしにする
    val dayOfWeek = computedDay ?: if (dateStr == initialDate) initialDayOfWeek else ""

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.Surface,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (isNewNote) "新しいノートを作る" else "ノート情報を変更",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Ink
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("生徒")
                    OutlinedTextField(
                        value = student,
                        onValueChange = { student = it },
                        placeholder = { Text("生徒の名前") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recentStudents.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentStudents.forEach { name ->
                                ChoiceChip(label = name, selected = student == name, onClick = { student = name })
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("教科")
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        placeholder = { Text("数学・英語・物理 など") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSubjects.forEach { s ->
                            ChoiceChip(label = s, selected = subject == s, onClick = { subject = s })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("日付")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dateStr,
                            onValueChange = { dateStr = it },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            supportingText = if (computedDay == null) {
                                { Text("例: 2026-11-21 の形で入力すると曜日が自動で入ります") }
                            } else {
                                null
                            },
                            modifier = Modifier.width(200.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        if (dayOfWeek.isNotBlank()) {
                            Text("($dayOfWeek)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                            Spacer(Modifier.width(12.dp))
                        }
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("カレンダー")
                        }
                    }
                }

                if (isNewNote) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("用紙")
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("キャンセル") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(student, dateStr, dayOfWeek, subject, template) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (isNewNote) "作成して開く" else "保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val utcFormat = remember {
            SimpleDateFormat(DATE_PATTERN, Locale.JAPAN).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
        val initialMillis = remember {
            try {
                utcFormat.parse(dateStr.trim())?.time
            } catch (_: Exception) {
                null
            } ?: utcFormat.parse(SimpleDateFormat(DATE_PATTERN, Locale.JAPAN).format(Date()))?.time
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateStr = utcFormat.format(Date(it)) }
                    showDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 消去・削除などの確認。危険な操作は赤いボタンで示す */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp) },
        text = { Text(message, fontSize = 15.sp, lineHeight = 23.sp, color = AppColors.InkSoft) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (danger) {
                    ButtonDefaults.buttonColors(containerColor = AppColors.Danger)
                } else {
                    ButtonDefaults.buttonColors()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(46.dp)
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(46.dp)) { Text("キャンセル") }
        }
    )
}

/** 用紙（罫線）の種類を選ぶ */
@Composable
fun TemplatePickerDialog(
    current: BackgroundTemplate,
    onSelect: (BackgroundTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用紙の種類", fontWeight = FontWeight.Bold, fontSize = 19.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackgroundTemplate.entries.forEach { t ->
                    val selected = t == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppColors.AccentSoft else AppColors.Surface)
                            .clickable { onSelect(t) }
                            .padding(horizontal = 14.dp)
                    ) {
                        Text(
                            t.label,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AppColors.AccentDeep else AppColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = "選択中", tint = AppColors.Accent)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

/** 設定：PDF保存先／ペンのボタン／生徒候補の保持期間 */
@Composable
fun SettingsDialog(
    hasExportFolder: Boolean,
    customStylusKeyCode: Int,
    expiryDays: Int,
    onChangeExportFolder: () -> Unit,
    onOpenStylusKeyConfig: () -> Unit,
    onUpdateExpiryDays: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定", fontWeight = FontWeight.Bold, fontSize = 19.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingRow(
                    title = "PDFの保存先フォルダ",
                    description = if (hasExportFolder) "設定済み。書き出したPDFはこのフォルダに保存されます" else "未設定。最初の書き出し時にも選べます"
                ) {
                    OutlinedButton(onClick = onChangeExportFolder) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasExportFolder) "変更" else "選ぶ")
                    }
                }
                HorizontalDivider(color = AppColors.Line)
                SettingRow(
                    title = "ペンのサイドボタン",
                    description = if (customStylusKeyCode > 0) {
                        "登録済み。押すたびに消しゴムとペンが切り替わります"
                    } else {
                        "自動検知。反応しないときはここから登録してください"
                    }
                ) {
                    OutlinedButton(onClick = onOpenStylusKeyConfig) {
                        Icon(Icons.Rounded.SettingsRemote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("登録")
                    }
                }
                HorizontalDivider(color = AppColors.Line)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("生徒名の候補を残す期間", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                    Text(
                        "この期間ノートを作っていない生徒は、候補から自動で外れます（ノート自体は消えません）",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = AppColors.InkSoft
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "2か月", 90 to "3か月", 180 to "半年").forEach { (days, label) ->
                            ChoiceChip(label = label, selected = expiryDays == days, onClick = { onUpdateExpiryDays(days) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun SettingRow(title: String, description: String, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
            Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = AppColors.InkSoft)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

/** ペンのサイドボタン登録（押されたキーを自動で覚える） */
@Composable
fun StylusButtonConfigDialog(
    customKeyCode: Int,
    detectedInfo: String?,
    onTestToggleEraser: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ペンのサイドボタンを登録", fontWeight = FontWeight.Bold, fontSize = 19.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "この画面を開いたまま、ペンの側面にあるボタンを1回押してください。\n信号を受け取ると自動で「消しゴム切り替えボタン」として登録されます。",
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = AppColors.Ink
                )
                Surface(
                    color = if (detectedInfo != null) AppColors.SuccessSoft else AppColors.SurfaceMuted,
                    shape = RoundedCornerShape(12.dp),
                    border = if (detectedInfo != null) BorderStroke(1.dp, AppColors.Success) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (detectedInfo != null) "登録しました" else "ボタンが押されるのを待っています…",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (detectedInfo != null) AppColors.Success else AppColors.InkSoft
                        )
                        Text(
                            detectedInfo
                                ?: "現在の登録: ${if (customKeyCode > 0) "キーコード $customKeyCode" else "なし（標準の自動検知）"}",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = AppColors.InkSoft
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("完了") }
        },
        dismissButton = {
            TextButton(onClick = onTestToggleEraser) { Text("切り替えをテスト") }
        }
    )
}
