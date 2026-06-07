package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.Game
import com.example.data.LineupEntry
import com.example.data.Player
import java.io.File
import java.io.FileWriter

object SpreadsheetExporter {
    private const val TAG = "SpreadsheetExporter"

    fun exportGameAndStats(
        context: Context,
        game: Game,
        players: List<Player>,
        lineupEntries: List<LineupEntry>
    ) {
        try {
            val csvBuilder = StringBuilder()

            // Header Section
            csvBuilder.append("BASE_LINEUP COACH ASSISTANT EXPORT REPORT\n")
            csvBuilder.append("Game: ${game.opponent} (${game.status})\n")
            csvBuilder.append("Date: ${game.gameDate} at ${game.gameTime}\n")
            csvBuilder.append("Run limit/Inning: ${game.runLimitPerInning} runs, Min Defensive Innings: ${game.minInningsDefense}\n")
            csvBuilder.append("Report Generation Time: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n\n")

            // Lineup Section
            csvBuilder.append("=== OFFENSIVE LINEUP & INNING DEFENSIVE ROTATIONS ===\n")
            csvBuilder.append("Batting Order,Player,Jersey,Pref Position,Inning 1,Inning 2,Inning 3,Inning 4,Inning 5,Inning 6\n")

            // Map player matching IDs for line rows
            val playerMap = players.associateBy { it.id }
            
            // Sort entries by batting order
            val rosterInLineup = lineupEntries.sortedBy { it.battingOrder }
            rosterInLineup.forEach { entry ->
                val player = playerMap[entry.playerId]
                if (player != null) {
                    val orderStr = if (entry.battingOrder > 0) entry.battingOrder.toString() else "BENCH"
                    csvBuilder.append("\"$orderStr\",\"${player.name}\",\"${player.jerseyNumber}\",\"${player.preferredPosition}\",")
                    csvBuilder.append("\"${entry.posInning1}\",\"${entry.posInning2}\",\"${entry.posInning3}\",\"${entry.posInning4}\",\"${entry.posInning5}\",\"${entry.posInning6}\"\n")
                }
            }
            csvBuilder.append("\n")

            // Seasonal Roster Stats Section
            csvBuilder.append("=== TEAM SEASON PLAYER STATISTICS ===\n")
            csvBuilder.append("Player,Jersey,Pref Position,At Bats,Hits,Walks,Runs,RBIs,Strikeouts,Batting AVG,Coach Notes\n")
            
            players.sortedBy { it.name }.forEach { p ->
                val battingAvg = if (p.atBats > 0) p.hits.toDouble() / p.atBats else 0.0
                val formattedAvg = String.format("%.3f", battingAvg)
                csvBuilder.append("\"${p.name}\",\"${p.jerseyNumber}\",\"${p.preferredPosition}\",")
                csvBuilder.append("${p.atBats},${p.hits},${p.walks},${p.runs},${p.rbis},${p.strikeouts},")
                csvBuilder.append("\"$formattedAvg\",\"${p.note.replace("\"", "\"\"")}\"\n")
            }

            // Write File to Cache
            val cleanOpponentName = game.opponent.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val fileName = "BaseLineup_${cleanOpponentName}_report.csv"
            val cacheFile = File(context.cacheDir, fileName)
            
            val writer = FileWriter(cacheFile)
            writer.write(csvBuilder.toString())
            writer.flush()
            writer.close()

            // Share File via FileProvider
            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, cacheFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "BaseLineup Export - ${game.opponent} vs US")
                putExtra(Intent.EXTRA_TEXT, "Hello Coach! Attached is the detailed spreadsheet lineup & roster statistics report generated using BaseLineup for our game against ${game.opponent}.")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Spreadsheet Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating or sharing spreadsheet CSV: ${e.message}", e)
        }
    }
}
