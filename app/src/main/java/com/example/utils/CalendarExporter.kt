package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.Game
import java.io.File
import java.io.FileWriter
import java.net.URLEncoder

object CalendarExporter {
    private const val TAG = "CalendarExporter"

    fun formatGameToIcsDateTime(dateStr: String, timeStr: String): String {
        try {
            val cleanedDate = dateStr.trim().replace(",", "")
            val parts = cleanedDate.split(" ")
            if (parts.size >= 3) {
                val month = when (parts[0].lowercase()) {
                    "january", "jan" -> "01"
                    "february", "feb" -> "02"
                    "march", "mar" -> "03"
                    "april", "apr" -> "04"
                    "may" -> "05"
                    "june", "jun" -> "06"
                    "july", "jul" -> "07"
                    "august", "aug" -> "08"
                    "september", "sep" -> "09"
                    "october", "oct" -> "10"
                    "november", "nov" -> "11"
                    "december", "dec" -> "12"
                    else -> "06"
                }
                val day = parts[1].padStart(2, '0')
                val year = parts[2]
                
                // Clean time
                val cleanedTime = timeStr.trim().uppercase()
                var hour = 18
                var minute = 0
                val amPm = if (cleanedTime.contains("PM")) "PM" else "AM"
                val timeParts = cleanedTime.replace("AM", "").replace("PM", "").trim().split(":")
                if (timeParts.isNotEmpty()) {
                    hour = timeParts[0].toIntOrNull() ?: 18
                    if (timeParts.size > 1) {
                        minute = timeParts[1].toIntOrNull() ?: 0
                    }
                    if (amPm == "PM" && hour < 12) {
                        hour += 12
                    } else if (amPm == "AM" && hour == 12) {
                        hour = 0
                    }
                }
                val hourStr = hour.toString().padStart(2, '0')
                val minStr = minute.toString().padStart(2, '0')
                
                return "${year}${month}${day}T${hourStr}${minStr}00"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting ICS date time: ${e.message}")
        }
        return "20260610T180000"
    }

    fun formatGameToIcsEndDateTime(dateStr: String, timeStr: String): String {
        val start = formatGameToIcsDateTime(dateStr, timeStr)
        if (start.contains("T") && start.length >= 15) {
            val datePart = start.substringBefore("T")
            val timePart = start.substringAfter("T")
            val hourVal = timePart.substring(0, 2).toIntOrNull() ?: 18
            val endHourVal = (hourVal + 2) % 24
            val endHourStr = endHourVal.toString().padStart(2, '0')
            return datePart + "T" + endHourStr + timePart.substring(2)
        }
        return "20260610T200000"
    }

    fun generateGoogleCalendarUrl(context: Context, game: Game): String {
        return try {
            val prefs = context.getSharedPreferences("baselineup_prefs", Context.MODE_PRIVATE)
            val teamName = prefs.getString("team_name", "Wildcats") ?: "Wildcats"
            val start = formatGameToIcsDateTime(game.gameDate, game.gameTime)
            val end = formatGameToIcsEndDateTime(game.gameDate, game.gameTime)
            val text = URLEncoder.encode("Baseball Matchup: $teamName vs ${game.opponent}", "UTF-8")
            val detailsStr = "Match Date: ${game.gameDate} at ${game.gameTime}\nInnings Length: ${game.totalInnings}\nContinuous Batting: ${if (game.continuousBatting) "Enabled" else "Disabled"}"
            val details = URLEncoder.encode(detailsStr, "UTF-8")
            "https://calendar.google.com/calendar/render?action=TEMPLATE&text=$text&dates=${start}/${end}&details=$details"
        } catch (e: Exception) {
            "https://calendar.google.com"
        }
    }

    fun exportScheduleAsIcal(context: Context, games: List<Game>) {
        try {
            val prefs = context.getSharedPreferences("baselineup_prefs", Context.MODE_PRIVATE)
            val teamName = prefs.getString("team_name", "Wildcats") ?: "Wildcats"
            val sb = java.lang.StringBuilder()
            sb.append("BEGIN:VCALENDAR\n")
            sb.append("VERSION:2.0\n")
            sb.append("PRODID:-//BaseLineup Studio//Schedule//EN\n")
            sb.append("CALSCALE:GREGORIAN\n")
            sb.append("METHOD:PUBLISH\n")

            games.forEach { game ->
                val start = formatGameToIcsDateTime(game.gameDate, game.gameTime)
                val end = formatGameToIcsEndDateTime(game.gameDate, game.gameTime)
                sb.append("BEGIN:VEVENT\n")
                sb.append("UID:game_${game.id}_2026@baselineup.com\n")
                sb.append("DTSTAMP:20260607T071114Z\n")
                sb.append("DTSTART:${start}Z\n")
                sb.append("DTEND:${end}Z\n")
                sb.append("SUMMARY:Baseball Matchup: $teamName vs ${game.opponent}\n")
                sb.append("DESCRIPTION:Match Date: ${game.gameDate} at ${game.gameTime}\\nInnings Length: ${game.totalInnings}\\nContinuous Batting: ${if (game.continuousBatting) "Enabled" else "Disabled"}\\nStatus: ${game.status}\n")
                sb.append("STATUS:CONFIRMED\n")
                sb.append("END:VEVENT\n")
            }
            sb.append("END:VCALENDAR\n")

            // Save text to temporary cache file
            val outputDir = context.cacheDir
            val tempFile = File(outputDir, "baselineup_schedule.ics")
            val writer = FileWriter(tempFile)
            writer.write(sb.toString())
            writer.close()

            // Trigger Share Intent
            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, tempFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_SUBJECT, "BaseLineup Game Schedule Export")
                putExtra(Intent.EXTRA_TEXT, "Hi Team! Here is our game schedule iCal/ICS file ready to be imported into your Google Calendar, Apple Calendar, or Outlook.")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Game Schedule Calendar File")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting game schedule: ${e.message}", e)
        }
    }
}
