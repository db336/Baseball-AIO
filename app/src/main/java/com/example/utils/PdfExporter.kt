package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.Game
import com.example.data.LineupEntry
import com.example.data.Player
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfExporter {
    private const val TAG = "PdfExporter";

    fun generateLineupPdf(
        context: Context,
        game: Game,
        players: List<Player>,
        lineupEntries: List<LineupEntry>
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            // Standard letter/A4 LANDSCAPE: 842 x 595 points
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Paint styles
            val paintTitle = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val paintSubtitle = Paint().apply {
                color = AndroidColor.DKGRAY
                textSize = 11f
                isAntiAlias = true
            }
            val paintHeader = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val paintBody = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 9f
                isAntiAlias = true
            }
            val paintBodyBold = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 9f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val paintLine = Paint().apply {
                color = AndroidColor.LTGRAY
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            val paintGridHeaderBg = Paint().apply {
                color = AndroidColor.rgb(235, 240, 250)
                style = Paint.Style.FILL
            }

            // Margin parameters
            val startX = 35f
            val endX = 807f
            var currentY = 45f

            // 1. Header Banner
            val prefs = context.getSharedPreferences("baselineup_prefs", Context.MODE_PRIVATE)
            val teamName = prefs.getString("team_name", "Wildcats") ?: "Wildcats"
            canvas.drawText("BASE_LINEUP STUDIO - DUGOUT ROSTER", startX, currentY, paintTitle)
            currentY += 20f
            canvas.drawText("$teamName vs ${game.opponent} (${game.status})", startX, currentY, paintSubtitle)
            currentY += 15f
            canvas.drawText("Matchup Date: ${game.gameDate} at ${game.gameTime}  |  Total Innings Match: ${game.totalInnings}", startX, currentY, paintSubtitle)
            currentY += 18f

            // Divider line
            canvas.drawLine(startX, currentY, endX, currentY, paintLine)
            currentY += 12f

            // 2. Rules summary box
            val rulesText = "Continuous Batting: ${if (game.continuousBatting) "Enabled" else "Disabled"}  |  " +
                    "Min Defense: ${game.minInningsDefense} inn  |  " +
                    "Max Pitch: ${game.maxInningsPitcher} inn"
            val rulesText2 = "Equal Bench Rotation: ${if (game.equalBenchRule) "Yes (Fair Play)" else "No"}  |  " +
                    "Max Consecutive Bench: ${game.maxConsecutiveBench} inn"
            
            canvas.drawText(rulesText, startX, currentY, paintSubtitle)
            currentY += 14f
            canvas.drawText(rulesText2, startX, currentY, paintSubtitle)
            currentY += 18f

            // 3. Grid Table of positions
            // Draw Table Header
            val tableY = currentY
            val rowHeight = 20f

            // Draw Header Block
            canvas.drawRect(startX, tableY, endX, tableY + rowHeight, paintGridHeaderBg)
            canvas.drawRect(startX, tableY, endX, tableY + rowHeight, paintLine)

            // Dynamic columns based on totalInnings
            val totalInnings = game.totalInnings.coerceIn(3, 9)
            val baseCols = mutableListOf<ColumnDef>()
            baseCols.add(ColumnDef(35f, 40f, "BAT"))
            baseCols.add(ColumnDef(75f, 45f, "JERSEY"))
            baseCols.add(ColumnDef(120f, 135f, "PLAYER NAME"))
            
            val remainingWidth = endX - 255f
            val colInningWidth = remainingWidth / totalInnings
            
            for (i in 1..totalInnings) {
                baseCols.add(ColumnDef(255f + (i - 1) * colInningWidth, colInningWidth, "INN $i"))
            }
            val cols = baseCols

            cols.forEach { col ->
                // Draw vertical lines inside header
                canvas.drawLine(col.x, tableY, col.x, tableY + rowHeight, paintLine)
                // Draw text
                canvas.drawText(col.title, col.x + 4f, tableY + 14f, paintHeader)
            }
            // Close right vertical border
            canvas.drawLine(endX, tableY, endX, tableY + rowHeight, paintLine)

            currentY += rowHeight

            // Fill Table Rows
            val sortedEntries = lineupEntries.sortedBy { if (it.battingOrder <= 0) 99 else it.battingOrder }
            val playerMap = players.associateBy { it.id }

            sortedEntries.forEach { entry ->
                val player = playerMap[entry.playerId]
                if (player != null) {
                    // Draw outer rect for the row
                    canvas.drawRect(startX, currentY, endX, currentY + rowHeight, paintLine)

                    // Draw inner lines and content
                    cols.forEach { col ->
                        canvas.drawLine(col.x, currentY, col.x, currentY + rowHeight, paintLine)
                        
                        val text = when {
                            col.title == "BAT" -> if (entry.battingOrder > 0) "${entry.battingOrder}." else "Bench"
                            col.title == "JERSEY" -> "#${player.jerseyNumber}"
                            col.title == "PLAYER NAME" -> player.name
                            col.title.startsWith("INN ") -> {
                                val innNum = col.title.substringAfter("INN ").toIntOrNull() ?: 1
                                when (innNum) {
                                    1 -> entry.posInning1
                                    2 -> entry.posInning2
                                    3 -> entry.posInning3
                                    4 -> entry.posInning4
                                    5 -> entry.posInning5
                                    6 -> entry.posInning6
                                    else -> "BENCH"
                                }
                            }
                            else -> ""
                        }

                        // Style playing positions nicely
                        val usePaint = if (col.title.startsWith("INN") && text != "BENCH") {
                            paintBodyBold
                        } else {
                            paintBody
                        }
                        
                        canvas.drawText(text, col.x + 5f, currentY + 14f, usePaint)
                    }
                    // Close right border
                    canvas.drawLine(endX, currentY, endX, currentY + rowHeight, paintLine)

                    currentY += rowHeight
                }
            }

            currentY += 15f

            // 4. Notes Section / Footer (adjusted for landscape constraints)
            if (currentY < 500f) {
                canvas.drawText("NOTES / SPECIAL INSTRUCTIONS (DUGOUT RECORD):", startX, currentY, paintHeader)
                currentY += 13f
                
                // Draw manual writing lines for coaches
                for (i in 0..2) {
                    canvas.drawLine(startX, currentY + 10f, endX, currentY + 10f, paintLine)
                    currentY += 16f
                }
            }

            // Footer brand label
            canvas.drawText(
                "Generated with BaseLineup Studio — All rights reserved.", 
                startX, 
                570f, 
                Paint().apply {
                    color = AndroidColor.LTGRAY
                    textSize = 8f
                    isAntiAlias = true
                }
            )

            pdfDocument.finishPage(page)

            // Save PDF to cache dir to share or print
            val cleanOpponentName = game.opponent.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "BaseLineup_Dugout_${cleanOpponentName}_Roster.pdf"
            val cacheFile = File(context.cacheDir, fileName)
            
            val outputStream = FileOutputStream(cacheFile)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            return cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate PDF lineup: ${e.message}", e)
            return null
        }
    }

    fun exportAsPdf(context: Context, pdfFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "BaseLineup PDF Export: Roster & Lineup")
                putExtra(Intent.EXTRA_TEXT, "Hi Coach! Attached is the printable dugout lineup card PDF generated on BaseLineup.")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share PDF Lineup Card")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing PDF file: ${e.message}", e)
        }
    }

    fun printPdf(context: Context, pdfFile: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "BaseLineup_Print_${pdfFile.nameWithoutExtension}"
            printManager.print(jobName, FilePrintAdapter(pdfFile), null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing print task: ${e.message}", e)
        }
    }

    private data class ColumnDef(val x: Float, val width: Float, val title: String)

    private class FilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            var input: FileInputStream? = null
            var output: FileOutputStream? = null
            try {
                input = FileInputStream(file)
                output = FileOutputStream(destination?.fileDescriptor)
                val buf = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buf).also { bytesRead = it } >= 0) {
                    output.write(buf, 0, bytesRead)
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            } finally {
                input?.close()
                output?.close()
            }
        }
    }
}
