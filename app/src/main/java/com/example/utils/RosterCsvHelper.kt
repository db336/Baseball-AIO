package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.Player
import java.io.File
import java.io.FileWriter

object RosterCsvHelper {
    private const val TAG = "RosterCsvHelper"

    /**
     * Standard CSV header for Roster import/export.
     */
    const val CSV_HEADER = "Name,Jersey,PreferredPosition,SecondaryPosition1,SecondaryPosition2,IsAvailable,AtBats,Hits,Walks,Runs,RBIs,Strikeouts,Note,LastGamePitchCount,DaysSinceLastPitched"

    /**
     * Serializes a list of players to a CSV string.
     */
    fun exportToCsv(players: List<Player>): String {
        val csv = StringBuilder()
        csv.append(CSV_HEADER).append("\n")
        players.forEach { p ->
            val escapedName = escapeCsv(p.name)
            val escapedJersey = escapeCsv(p.jerseyNumber)
            val escapedPref = escapeCsv(p.preferredPosition)
            val escapedSec1 = escapeCsv(p.secondaryPosition1)
            val escapedSec2 = escapeCsv(p.secondaryPosition2)
            val escapedNote = escapeCsv(p.note)
            
            csv.append("$escapedName,$escapedJersey,$escapedPref,$escapedSec1,$escapedSec2,")
            csv.append("${p.isAvailable},${p.atBats},${p.hits},${p.walks},${p.runs},${p.rbis},${p.strikeouts},")
            csv.append("$escapedNote,${p.lastGamePitchCount},${p.daysSinceLastPitched}\n")
        }
        return csv.toString()
    }

    /**
     * Escapes CSV values by putting them in quotes and duplicating inner quotes if necessary.
     */
    private fun escapeCsv(value: String): String {
        val containsSpecial = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Parses a CSV string and returns a list of Players.
     */
    fun parseFromCsv(csvText: String): List<Player> {
        val playersList = mutableListOf<Player>()
        val lines = csvText.lines()
        if (lines.isEmpty()) return emptyList()

        var headerRow: List<String>? = null
        var startIndex = 0

        // Find the first non-empty line
        while (startIndex < lines.size && lines[startIndex].trim().isBlank()) {
            startIndex++
        }

        if (startIndex >= lines.size) return emptyList()

        val firstLine = lines[startIndex]
        val firstLineTokens = parseCsvLine(firstLine)

        // Detect header row dynamically
        val hasNameHeader = firstLineTokens.any { it.trim().lowercase().contains("name") }
        val hasJerseyHeader = firstLineTokens.any { it.trim().lowercase().contains("jersey") || it.trim().contains("#") }

        if (hasNameHeader || hasJerseyHeader) {
            headerRow = firstLineTokens.map { it.trim().lowercase().replace(" ", "").replace("_", "") }
            startIndex++
        }

        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.trim().isBlank()) continue
            val tokens = parseCsvLine(line)
            if (tokens.isEmpty()) continue

            try {
                // Dynamically fetch token by header names or index fallback
                fun getToken(headerNames: List<String>, indexFallback: Int): String? {
                    if (headerRow != null) {
                        for (hName in headerNames) {
                            val cleanHName = hName.lowercase().replace(" ", "").replace("_", "")
                            val colIdx = headerRow.indexOfFirst { it.contains(cleanHName) }
                            if (colIdx != -1 && colIdx < tokens.size) {
                                return tokens[colIdx]
                            }
                        }
                        return null
                    } else {
                        return if (indexFallback < tokens.size) tokens[indexFallback] else null
                    }
                }

                // Parse core values
                val name = getToken(listOf("name", "player"), 0) ?: continue
                if (name.isBlank()) continue

                val jersey = getToken(listOf("jersey", "number", "#"), 1) ?: ""
                val prefPos = getToken(listOf("preferredposition", "prefposition", "prefpos", "preferred", "pref"), 2) ?: "LF"
                val secPos1 = getToken(listOf("secondaryposition1", "secondary1", "sec1", "secpos1"), 3) ?: "BENCH"
                val secPos2 = getToken(listOf("secondaryposition2", "secondary2", "sec2", "secpos2"), 4) ?: "BENCH"

                val isAvailStr = getToken(listOf("isavailable", "available", "active"), 5) ?: "true"
                val isAvailable = isAvailStr.lowercase().toBooleanStrictOrNull() ?: true

                // Statistics & Rest status
                val atBats = getToken(listOf("atbats", "ab"), 6)?.toIntOrNull() ?: 0
                val hits = getToken(listOf("hits", "h"), 7)?.toIntOrNull() ?: 0
                val walks = getToken(listOf("walks", "bb", "w"), 8)?.toIntOrNull() ?: 0
                val runs = getToken(listOf("runs", "r"), 9)?.toIntOrNull() ?: 0
                val rbis = getToken(listOf("rbis", "rbi"), 10)?.toIntOrNull() ?: 0
                val strikeouts = getToken(listOf("strikeouts", "so", "k"), 11)?.toIntOrNull() ?: 0
                val note = getToken(listOf("note", "notes", "coachnotes"), 12) ?: ""
                val pitchCount = getToken(listOf("lastgamepitchcount", "pitchcount", "pitches", "lastpitchcount"), 13)?.toIntOrNull() ?: 0
                val restDays = getToken(listOf("dayssincelastpitched", "daysrest", "rest", "dayssincepitched"), 14)?.toIntOrNull() ?: 0

                playersList.add(
                    Player(
                        name = name.trim(),
                        jerseyNumber = jersey.trim(),
                        preferredPosition = if (prefPos.trim().isNotBlank()) prefPos.trim().uppercase() else "LF",
                        secondaryPosition1 = if (secPos1.trim().isNotBlank()) secPos1.trim().uppercase() else "BENCH",
                        secondaryPosition2 = if (secPos2.trim().isNotBlank()) secPos2.trim().uppercase() else "BENCH",
                        isAvailable = isAvailable,
                        atBats = atBats,
                        hits = hits,
                        walks = walks,
                        runs = runs,
                        rbis = rbis,
                        strikeouts = strikeouts,
                        note = note,
                        lastGamePitchCount = pitchCount,
                        daysSinceLastPitched = restDays
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CSV line index $i: $line", e)
            }
        }
        return playersList
    }

    /**
     * Splits a CSV line with consideration of enclosing double quotes and escaped quotes.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = java.lang.StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    /**
     * Writes roster CSV to local cache and shares it via Intent chooser.
     */
    fun shareRosterCsv(context: Context, players: List<Player>) {
        try {
            val csvText = exportToCsv(players)
            val fileName = "BaseLineup_Team_Roster.csv"
            val cacheFile = File(context.cacheDir, fileName)

            val writer = FileWriter(cacheFile)
            writer.write(csvText)
            writer.flush()
            writer.close()

            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, cacheFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "BaseLineup Roster CSV Export")
                putExtra(Intent.EXTRA_TEXT, "Hello! Detached is the squad roster export spreadsheet from BaseLineup. You can import this CSV back directly in BaseLineup on other devices.")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Roster CSV File")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting roster CSV: ${e.message}", e)
        }
    }
}
