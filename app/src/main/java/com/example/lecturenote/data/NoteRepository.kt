package com.example.lecturenote.data

import android.content.Context
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.HistoryEntry
import com.example.lecturenote.model.LectureMetadata
import com.example.lecturenote.model.LectureNoteDocument
import com.example.lecturenote.model.NotePoint
import com.example.lecturenote.model.NoteSummary
import com.example.lecturenote.model.Page
import com.example.lecturenote.model.PenPreset
import com.example.lecturenote.model.ShapeMode
import com.example.lecturenote.model.Stroke
import com.example.lecturenote.model.ToolType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class NoteRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("lecture_note_prefs_v2", Context.MODE_PRIVATE)
    private val notesDir = File(context.filesDir, "saved_notes").apply { mkdirs() }
    private val indexFile = File(notesDir, "notes_index_v3.json")
    private val ioLock = Any()

    companion object {
        private const val KEY_STUDENT_HISTORY = "student_history_json"
        private const val KEY_SUBJECT_HISTORY = "subject_history_json"
        private const val KEY_PEN_PRESETS = "pen_presets_json"
        private const val KEY_EXPORT_TREE_URI = "export_tree_uri"
        private const val KEY_EXPIRY_DAYS = "student_expiry_days"
        private const val KEY_CUSTOM_STYLUS_KEYCODE = "custom_stylus_keycode"
        private const val DEFAULT_EXPIRY_DAYS = 90L

        private const val BIN_MAGIC = 0x4C4E4F54 // "LNOT"
        private const val BIN_VERSION = 4

        // 本体の後ろに付ける追加情報の目印（"ORG1"）。
        // 旧バージョンのアプリは本体だけ読んで後ろを無視するため、旧版でも同じファイルを開ける
        private const val TRAILER_AXES_ORIGIN = 0x4F524731
    }

    fun getCustomStylusKeyCode(): Int = prefs.getInt(KEY_CUSTOM_STYLUS_KEYCODE, -1)

    fun saveCustomStylusKeyCode(keyCode: Int) {
        prefs.edit().putInt(KEY_CUSTOM_STYLUS_KEYCODE, keyCode).apply()
    }

    fun getSavedExportTreeUri(): String? = prefs.getString(KEY_EXPORT_TREE_URI, null)

    fun saveExportTreeUri(uriString: String) {
        prefs.edit().putString(KEY_EXPORT_TREE_URI, uriString).apply()
    }

    fun getStudentExpiryDays(): Int = prefs.getInt(KEY_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS.toInt())

    fun setStudentExpiryDays(days: Int) {
        prefs.edit().putInt(KEY_EXPIRY_DAYS, days).apply()
    }

    fun getRecentStudents(): List<String> {
        val expiryMs = TimeUnit.DAYS.toMillis(getStudentExpiryDays().toLong())
        val now = System.currentTimeMillis()
        val entries = loadHistoryEntries(KEY_STUDENT_HISTORY)
            .filter { now - it.lastUsedAt <= expiryMs }
            .sortedByDescending { it.lastUsedAt }

        saveHistoryEntries(KEY_STUDENT_HISTORY, entries)
        return entries.map { it.value }
    }

    fun recordStudentUsage(studentName: String) {
        val trimmed = studentName.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = loadHistoryEntries(KEY_STUDENT_HISTORY)
            .filterNot { it.value == trimmed }
            .toMutableList()
        existing.add(0, HistoryEntry(trimmed, now))
        saveHistoryEntries(KEY_STUDENT_HISTORY, existing)
    }

    fun getRecentSubjects(): List<String> {
        val entries = loadHistoryEntries(KEY_SUBJECT_HISTORY)
            .sortedByDescending { it.lastUsedAt }
            .map { it.value }
        return if (entries.isEmpty()) {
            listOf("数学", "英語", "国語", "物理", "化学", "生物", "社会")
        } else {
            entries
        }
    }

    fun recordSubjectUsage(subject: String) {
        val trimmed = subject.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = loadHistoryEntries(KEY_SUBJECT_HISTORY)
            .filterNot { it.value == trimmed }
            .toMutableList()
        existing.add(0, HistoryEntry(trimmed, now))
        saveHistoryEntries(KEY_SUBJECT_HISTORY, existing.take(20))
    }

    private fun loadHistoryEntries(key: String): List<HistoryEntry> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<HistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val v = obj.optString("value", "").trim()
                val ts = obj.optLong("lastUsedAt", 0L)
                if (v.isNotEmpty() && ts > 0L) {
                    list.add(HistoryEntry(v, ts))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistoryEntries(key: String, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(JSONObject().apply {
                put("value", entry.value)
                put("lastUsedAt", entry.lastUsedAt)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun loadPenPresets(): List<PenPreset> {
        val raw = prefs.getString(KEY_PEN_PRESETS, null) ?: return PenPreset.defaultPresets()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PenPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PenPreset(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        toolType = ToolType.valueOf(obj.getString("toolType")),
                        colorInt = obj.getInt("colorInt"),
                        strokeWidth = obj.getDouble("strokeWidth").toFloat(),
                        shapeMode = ShapeMode.valueOf(obj.optString("shapeMode", ShapeMode.LINE.name)),
                        isDashed = obj.optBoolean("isDashed", false)
                    )
                )
            }
            if (list.isEmpty()) PenPreset.defaultPresets() else list
        } catch (_: Exception) {
            PenPreset.defaultPresets()
        }
    }

    fun savePenPresets(presets: List<PenPreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("toolType", p.toolType.name)
                put("colorInt", p.colorInt)
                put("strokeWidth", p.strokeWidth.toDouble())
                put("shapeMode", p.shapeMode.name)
                put("isDashed", p.isDashed)
            })
        }
        prefs.edit().putString(KEY_PEN_PRESETS, arr.toString()).apply()
    }

    fun loadAllNoteSummaries(): List<NoteSummary> = synchronized(ioLock) {
        val summariesMap = mutableMapOf<String, NoteSummary>()

        if (indexFile.exists()) {
            try {
                val arr = JSONArray(indexFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("id")
                    val binFile = File(notesDir, "$id.bin")
                    val jsonFile = File(notesDir, "$id.json")
                    if (binFile.exists() || jsonFile.exists()) {
                        summariesMap[id] = NoteSummary(
                            id = id,
                            metadata = LectureMetadata(
                                studentName = obj.optString("studentName", "未設定"),
                                dateStr = obj.optString("dateStr", ""),
                                dayOfWeek = obj.optString("dayOfWeek", ""),
                                subject = obj.optString("subject", "数学")
                            ),
                            template = try {
                                BackgroundTemplate.valueOf(obj.optString("template", BackgroundTemplate.RULED_7MM.name))
                            } catch (_: Exception) {
                                BackgroundTemplate.RULED_7MM
                            },
                            pageCount = obj.optInt("pageCount", 1),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val legacyJsonFiles = notesDir.listFiles { f ->
            f.extension == "json" && f.name != indexFile.name
        } ?: emptyArray()

        var migratedAny = false
        for (jFile in legacyJsonFiles) {
            val docId = jFile.nameWithoutExtension
            if (!summariesMap.containsKey(docId)) {
                val migratedDoc = parseLegacyJsonDocument(jFile)
                if (migratedDoc != null) {
                    writeBinaryDocumentInternal(migratedDoc)
                    summariesMap[migratedDoc.id] = migratedDoc.toSummary()
                    jFile.delete()
                    migratedAny = true
                }
            }
        }

        val sorted = summariesMap.values.sortedByDescending { it.updatedAt }
        if (migratedAny || !indexFile.exists()) {
            saveSummariesIndexInternal(sorted)
        }
        return sorted
    }

    fun loadNoteDocumentById(docId: String): LectureNoteDocument? = synchronized(ioLock) {
        val binFile = File(notesDir, "$docId.bin")
        if (binFile.exists()) {
            readBinaryDocumentInternal(binFile)?.let { return it }
        }
        val legacyJsonFile = File(notesDir, "$docId.json")
        if (legacyJsonFile.exists()) {
            val migrated = parseLegacyJsonDocument(legacyJsonFile)
            if (migrated != null) {
                writeBinaryDocumentInternal(migrated)
                legacyJsonFile.delete()
                return migrated
            }
        }
        return null
    }

    fun saveNoteDocument(doc: LectureNoteDocument) = synchronized(ioLock) {
        doc.updatedAt = System.currentTimeMillis()
        recordStudentUsage(doc.metadata.studentName)
        recordSubjectUsage(doc.metadata.subject)

        writeBinaryDocumentInternal(doc)

        val oldJson = File(notesDir, "${doc.id}.json")
        if (oldJson.exists()) oldJson.delete()

        val currentSummaries = loadAllNoteSummaries().filterNot { it.id == doc.id }.toMutableList()
        currentSummaries.add(0, doc.toSummary())
        saveSummariesIndexInternal(currentSummaries.sortedByDescending { it.updatedAt })
    }

    fun deleteNoteDocument(docId: String) = synchronized(ioLock) {
        val binFile = File(notesDir, "$docId.bin")
        if (binFile.exists()) binFile.delete()
        val jsonFile = File(notesDir, "$docId.json")
        if (jsonFile.exists()) jsonFile.delete()

        val updated = loadAllNoteSummaries().filterNot { it.id == docId }
        saveSummariesIndexInternal(updated)
    }

    private fun saveSummariesIndexInternal(summaries: List<NoteSummary>) {
        val arr = JSONArray()
        for (s in summaries) {
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("studentName", s.metadata.studentName)
                put("dateStr", s.metadata.dateStr)
                put("dayOfWeek", s.metadata.dayOfWeek)
                put("subject", s.metadata.subject)
                put("template", s.template.name)
                put("pageCount", s.pageCount)
                put("updatedAt", s.updatedAt)
            })
        }
        indexFile.writeText(arr.toString())
    }

    private fun writeBinaryDocumentInternal(doc: LectureNoteDocument) {
        val tmpFile = File(notesDir, "${doc.id}.bin.tmp")
        val targetFile = File(notesDir, "${doc.id}.bin")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 65536)).use { out ->
                out.writeInt(BIN_MAGIC)
                out.writeInt(BIN_VERSION)
                out.writeUTF(doc.id)
                out.writeUTF(doc.metadata.studentName)
                out.writeUTF(doc.metadata.dateStr)
                out.writeUTF(doc.metadata.dayOfWeek)
                out.writeUTF(doc.metadata.subject)
                out.writeUTF(doc.template.name)
                out.writeLong(doc.updatedAt)

                out.writeInt(doc.pages.size)
                for (page in doc.pages) {
                    out.writeInt(page.pageNumber)
                    out.writeInt(page.strokes.size)
                    for (s in page.strokes) {
                        out.writeUTF(s.id)
                        out.writeInt(s.color)
                        out.writeFloat(s.strokeWidth)
                        out.writeUTF(s.toolType.name)
                        out.writeUTF(s.shapeMode?.name ?: "")
                        out.writeBoolean(s.isDashed)
                        out.writeFloat(s.rotationDeg)
                        out.writeInt(s.points.size)
                        for (pt in s.points) {
                            out.writeFloat(pt.x)
                            out.writeFloat(pt.y)
                        }
                    }
                }

                // 追加情報: XY軸の原点位置（中央以外のものだけ）
                val withOrigin = doc.pages.flatMap { it.strokes }
                    .filter { it.originFx != 0.5f || it.originFy != 0.5f }
                out.writeInt(TRAILER_AXES_ORIGIN)
                out.writeInt(withOrigin.size)
                for (s in withOrigin) {
                    out.writeUTF(s.id)
                    out.writeFloat(s.originFx)
                    out.writeFloat(s.originFy)
                }
                out.flush()
            }
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    private fun readBinaryDocumentInternal(file: File): LectureNoteDocument? {
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 65536)).use { inp ->
                val magic = inp.readInt()
                if (magic != BIN_MAGIC) return null
                val version = inp.readInt()
                val id = inp.readUTF()
                val studentName = inp.readUTF()
                val dateStr = inp.readUTF()
                val dayOfWeek = inp.readUTF()
                val subject = inp.readUTF()
                val templateName = inp.readUTF()
                val updatedAt = inp.readLong()

                val template = try {
                    BackgroundTemplate.valueOf(templateName)
                } catch (_: Exception) {
                    BackgroundTemplate.RULED_7MM
                }

                val pageCount = inp.readInt()
                val pages = ArrayList<Page>(pageCount.coerceAtLeast(1))
                for (pIdx in 0 until pageCount) {
                    val pageNumber = inp.readInt()
                    val strokeCount = inp.readInt()
                    val strokes = ArrayList<Stroke>(strokeCount)
                    for (sIdx in 0 until strokeCount) {
                        val sId = inp.readUTF()
                        val color = inp.readInt()
                        val strokeWidth = inp.readFloat()
                        val toolTypeName = inp.readUTF()
                        val shapeModeName = inp.readUTF()
                        val isDashed = if (version >= 3) inp.readBoolean() else false
                        val rotationDeg = if (version >= 4) inp.readFloat() else 0f
                        val ptCount = inp.readInt()
                        val pts = ArrayList<NotePoint>(ptCount)
                        for (k in 0 until ptCount) {
                            pts.add(NotePoint(inp.readFloat(), inp.readFloat()))
                        }
                        if (pts.isNotEmpty()) {
                            val toolType = try {
                                ToolType.valueOf(toolTypeName)
                            } catch (_: Exception) {
                                ToolType.PEN
                            }
                            val shapeMode = if (shapeModeName.isNotEmpty()) {
                                try { ShapeMode.valueOf(shapeModeName) } catch (_: Exception) { null }
                            } else null
                            strokes.add(
                                Stroke(
                                    id = sId,
                                    points = pts,
                                    color = color,
                                    strokeWidth = strokeWidth,
                                    toolType = toolType,
                                    shapeMode = shapeMode,
                                    isDashed = isDashed,
                                    rotationDeg = rotationDeg
                                )
                            )
                        }
                    }
                    pages.add(Page(pageNumber = pageNumber, strokes = strokes))
                }
                if (pages.isEmpty()) pages.add(Page(1))

                // 追加情報（無いファイル＝旧バージョンで保存されたものは、そのまま読み終える）
                try {
                    if (inp.readInt() == TRAILER_AXES_ORIGIN) {
                        val count = inp.readInt()
                        val origins = HashMap<String, Pair<Float, Float>>(count.coerceIn(0, 4096))
                        for (i in 0 until count) {
                            origins[inp.readUTF()] = inp.readFloat() to inp.readFloat()
                        }
                        if (origins.isNotEmpty()) {
                            for (page in pages) {
                                for (i in page.strokes.indices) {
                                    val o = origins[page.strokes[i].id] ?: continue
                                    page.strokes[i] = page.strokes[i].copy(originFx = o.first, originFy = o.second)
                                }
                            }
                        }
                    }
                } catch (_: java.io.IOException) {
                    // 追加情報なし
                }

                LectureNoteDocument(
                    id = id,
                    metadata = LectureMetadata(studentName, dateStr, dayOfWeek, subject),
                    template = template,
                    pages = pages,
                    updatedAt = updatedAt
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseLegacyJsonDocument(file: File): LectureNoteDocument? {
        return try {
            val obj = JSONObject(file.readText())
            val metadata = LectureMetadata(
                studentName = obj.optString("studentName", "未設定"),
                dateStr = obj.optString("dateStr", ""),
                dayOfWeek = obj.optString("dayOfWeek", ""),
                subject = obj.optString("subject", "数学")
            )
            val template = try {
                BackgroundTemplate.valueOf(obj.optString("template", BackgroundTemplate.RULED_7MM.name))
            } catch (_: Exception) {
                BackgroundTemplate.RULED_7MM
            }
            val pagesArr = obj.optJSONArray("pages") ?: JSONArray()
            val pages = mutableListOf<Page>()
            for (i in 0 until pagesArr.length()) {
                val pObj = pagesArr.getJSONObject(i)
                val strokesArr = pObj.optJSONArray("strokes") ?: JSONArray()
                val strokes = mutableListOf<Stroke>()
                for (j in 0 until strokesArr.length()) {
                    val sObj = strokesArr.getJSONObject(j)
                    val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                    val pts = ArrayList<NotePoint>(ptsArr.length())
                    for (k in 0 until ptsArr.length()) {
                        val pair = ptsArr.getJSONArray(k)
                        pts.add(NotePoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat()))
                    }
                    val shapeModeStr = sObj.optString("shapeMode", "")
                    val shapeMode = if (shapeModeStr.isNotEmpty()) {
                        try { ShapeMode.valueOf(shapeModeStr) } catch (_: Exception) { null }
                    } else null
                    val isDashed = sObj.optBoolean("isDashed", false)

                    if (pts.isNotEmpty()) {
                        strokes.add(
                            Stroke(
                                id = sObj.optString("id", UUID.randomUUID().toString()),
                                points = pts,
                                color = sObj.getInt("color"),
                                strokeWidth = sObj.getDouble("strokeWidth").toFloat(),
                                toolType = try { ToolType.valueOf(sObj.getString("toolType")) } catch (_: Exception) { ToolType.PEN },
                                shapeMode = shapeMode,
                                isDashed = isDashed
                            )
                        )
                    }
                }
                pages.add(Page(pageNumber = pObj.optInt("pageNumber", i + 1), strokes = strokes))
            }
            if (pages.isEmpty()) pages.add(Page(1))

            LectureNoteDocument(
                id = obj.getString("id"),
                metadata = metadata,
                template = template,
                pages = pages,
                updatedAt = obj.optLong("updatedAt", file.lastModified())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
