package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val jerseyNumber: String,
    val preferredPosition: String = "LF",
    val isAvailable: Boolean = true,
    // Season Statistics
    val atBats: Int = 0,
    val hits: Int = 0,
    val walks: Int = 0,
    val runs: Int = 0,
    val rbis: Int = 0,
    val strikeouts: Int = 0,
    val note: String = ""
) {
    val battingAverage: Double
        get() = if (atBats > 0) hits.toDouble() / atBats else 0.0

    fun formattedBattingAverage(): String {
        val avg = battingAverage
        return if (avg >= 1.0) {
            String.format("%.3f", avg)
        } else {
            String.format(".%03d", (avg * 1000).toInt())
        }
    }
}

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opponent: String,
    val gameDate: String, // e.g., "June 10, 2026"
    val gameTime: String, // e.g., "5:30 PM"
    val status: String = "Upcoming", // "Upcoming", "Live", "Completed"
    
    // League Rules
    val minInningsDefense: Int = 2,
    val maxInningsPitcher: Int = 2,
    val continuousBatting: Boolean = true, // Continuous batting order (all available players bat)
    val runLimitPerInning: Int = 5,
    
    // Current Game Score (strictly live-tracking)
    val runsOurTeam: Int = 0,
    val runsOpponent: Int = 0,
    val currentInning: Int = 1,
    val currentOuts: Int = 0,
    val runnerOnFirst: Boolean = false,
    val runnerOnSecond: Boolean = false,
    val runnerOnThird: Boolean = false
)

@Entity(tableName = "lineup_entries")
data class LineupEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gameId: Int,
    val playerId: Int,
    val battingOrder: Int = 0, // 1..N. If 0, not in the batting lineup
    
    // Defensive positions per inning (1 to 6)
    // Common values: "P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "BENCH"
    val posInning1: String = "BENCH",
    val posInning2: String = "BENCH",
    val posInning3: String = "BENCH",
    val posInning4: String = "BENCH",
    val posInning5: String = "BENCH",
    val posInning6: String = "BENCH"
)

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPushNotification: Boolean = true,
    val isSynced: Boolean = true
)
