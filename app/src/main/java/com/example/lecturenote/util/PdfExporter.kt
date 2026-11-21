package com.example.lecturenote.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import com.example.lecturenote.canvas.LectureCanvasView
import com.example.lecturenote.model.BackgroundTemplate
import com.example.lecturenote.model.LectureNoteDocument
import com.example.lecturenote.model.ToolType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

object PdfExporter {

    data class ExportResult(
        val success: Boolean,
        val fileName: String,
        val message: String
    )

    fun exportDocumentToTreeUri(
        context: Context,
        doc: LectureNoteDocument,
        treeUri: Uri
    ): ExportResult {
        val sanitizedBaseName = doc.metadata.formattedNoteName
            .replace("[\\/:*?\"<>|]".toRegex(), "_")
        val fileName = "${sanitizedBaseName}.pdf"

        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            val targetUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocumentUri,
                "application/pdf",
                fileName
            ) ?: return ExportResult(false, fileName, "保存先フォルダへのファイル作成権限がありません")

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                writePdfToStream(doc, outStream)
            } ?: return ExportResult(false, fileName, "出力ストリームを開けませんでした")

            ExportResult(true, fileName, "指定フォルダに保存しました: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(false, fileName, "フォルダ保存エラー: ${e.localizedMessage}")
        }
    }

    fun exportDocumentToAppFolder(
        context: Context,
        doc: LectureNoteDocument
    ): ExportResult {
        val sanitizedBaseName = doc.metadata.formattedNoteName
            .replace("[\\/:*?\"<>|]".toRegex(), "_")
        val fileName = "${sanitizedBaseName}.pdf"

        return try {
            val outputFile = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(outputFile).use { outStream ->
                writePdfToStream(doc, outStream)
            }
            ExportResult(true, fileName, "端末内に保存しました: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(false, fileName, "PDF出力エラー: ${e.localizedMessage}")
        }
    }

    private fun writePdfToStream(doc: LectureNoteDocument, outStream: OutputStream) {
        val pdfDocument = PdfDocument()
        val pageWidth = LectureCanvasView.PAGE_WIDTH.toInt()
        val pageHeight = LectureCanvasView.PAGE_HEIGHT.toInt()

        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val gridPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1.4f
        }

        val guidePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.parseColor("#94A3B8")
            strokeWidth = 2.4f
        }

        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#334155")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        try {
            for ((index, page) in doc.pages.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawColor(Color.WHITE)

                drawPdfTemplate(canvas, doc, index, doc.pages.size, headerPaint, gridPaint, guidePaint)

                val highlighters = page.strokes.filter { it.toolType == ToolType.HIGHLIGHTER }
                // 目隠しは授業中の表示用なので、PDFには出さない（隠していた内容もそのまま出力する）
                val pens = page.strokes.filter { it.toolType != ToolType.HIGHLIGHTER && !it.isMask }

                for (stroke in highlighters) {
                    strokePaint.strokeWidth = stroke.strokeWidth
                    strokePaint.color = stroke.color
                    strokePaint.alpha = 85
                    strokePaint.strokeCap = Paint.Cap.SQUARE
                    strokePaint.pathEffect = null
                    canvas.drawPath(stroke.path, strokePaint)
                }

                for (stroke in pens) {
                    val w = stroke.strokeWidth
                    strokePaint.strokeWidth = w
                    strokePaint.color = stroke.color
                    strokePaint.alpha = 255
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.pathEffect = if (stroke.isDashed) {
                        val dashOn = max(10f, w * 3.0f)
                        val dashOff = max(8f, w * 2.2f)
                        DashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
                    } else {
                        null
                    }
                    canvas.drawPath(stroke.path, strokePaint)
                }

                pdfDocument.finishPage(pdfPage)
            }

            pdfDocument.writeTo(outStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawPdfTemplate(
        canvas: Canvas,
        doc: LectureNoteDocument,
        pageIdx: Int,
        totalPages: Int,
        headerPaint: Paint,
        gridPaint: Paint,
        guidePaint: Paint
    ) {
        val w = LectureCanvasView.PAGE_WIDTH
        val h = LectureCanvasView.PAGE_HEIGHT
        val topMargin = 110f
        val meta = doc.metadata

        val headerLeft = "生徒: ${meta.studentName}  |  教科: ${meta.subject}"
        val headerRight = "${meta.dateStr} (${meta.dayOfWeek})   Page ${pageIdx + 1} / $totalPages"
        canvas.drawText(headerLeft, 48f, 64f, headerPaint)
        val rightWidth = headerPaint.measureText(headerRight)
        canvas.drawText(headerRight, w - rightWidth - 48f, 64f, headerPaint)
        canvas.drawLine(40f, 84f, w - 40f, 84f, guidePaint)

        when (doc.template) {
            BackgroundTemplate.RULED_7MM -> {
                var y = topMargin + 58f
                while (y < h - 48f) {
                    canvas.drawLine(40f, y, w - 40f, y, gridPaint)
                    y += 58f
                }
            }
            BackgroundTemplate.RULED_6MM -> {
                var y = topMargin + 48f
                while (y < h - 48f) {
                    canvas.drawLine(40f, y, w - 40f, y, gridPaint)
                    y += 48f
                }
            }
            BackgroundTemplate.GRID_5MM -> {
                var x = 40f
                while (x <= w - 40f) {
                    canvas.drawLine(x, topMargin, x, h - 40f, gridPaint)
                    x += 42f
                }
                var y = topMargin
                while (y <= h - 40f) {
                    canvas.drawLine(40f, y, w - 40f, y, gridPaint)
                    y += 42f
                }
            }
            BackgroundTemplate.CORNELL -> {
                val cueX = w * 0.28f
                val summaryY = h * 0.82f
                canvas.drawLine(cueX, topMargin, cueX, summaryY, guidePaint)
                canvas.drawLine(40f, summaryY, w - 40f, summaryY, guidePaint)
                canvas.drawText("【ポイント・疑問】", 52f, topMargin + 32f, headerPaint)
                canvas.drawText("【授業・指導ノート】", cueX + 28f, topMargin + 32f, headerPaint)
                canvas.drawText("【本日のまとめ・宿題】", 52f, summaryY + 42f, headerPaint)
                var y = topMargin + 110f
                while (y < summaryY - 16f) {
                    canvas.drawLine(cueX, y, w - 40f, y, gridPaint)
                    y += 54f
                }
            }
            BackgroundTemplate.BLANK -> {}
        }
    }
}
