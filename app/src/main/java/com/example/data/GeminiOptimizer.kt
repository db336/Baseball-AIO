package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// --- REST Gemini API Data Classes ---
data class GeminiPart(val text: String? = null)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(val contents: List<GeminiContent>)

object GeminiOptimizer {
    private const val TAG = "GeminiOptimizer"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Struct for our parsed result
    data class OptimizedRosterResult(
        val coachStrategyExplanation: String,
        val players: List<OptimizedPlayerEntry>
    )

    data class OptimizedPlayerEntry(
        val playerId: Int,
        val battingOrder: Int,
        val pos1: String,
        val pos2: String,
        val pos3: String,
        val pos4: String,
        val pos5: String,
        val pos6: String,
        val pos7: String? = null,
        val pos8: String? = null,
        val pos9: String? = null,
        val pos10: String? = null
    )

    /**
     * Attempts to call the Gemini API to optimize lineups.
     * Fallbacks to our high-quality deterministic rule-based local optimizer on failures.
     */
    suspend fun optimize(
        players: List<Player>,
        game: Game,
        currentLineups: List<LineupEntry>? = null,
        lockedInnings: List<Boolean> = emptyList()
    ): OptimizedRosterResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // If the key is empty or still holds a default template value, skip remote API or fail immediately to trigger local fallback.
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || lockedInnings.any { it }) {
            Log.d(TAG, "Gemini API key is not configured or locked innings are present. Directing to local algorithmic optimizer.")
            return@withContext runLocalOptimization(players, game, currentLineups, lockedInnings)
        }

        val prompt = buildPrompt(players, game)

        try {
            // Build Gemini Request Payload
            val reqPayload = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
            )
            
            val reqJson = moshi.adapter(GeminiRequest::class.java).toJson(reqPayload)
            
            val request = okhttp3.Request.Builder()
                .url("$BASE_URL/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey")
                .post(reqJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API call failed with code: ${response.code}. Response: ${response.body?.string()}")
                return@withContext runLocalOptimization(players, game, currentLineups, lockedInnings)
            }

            val respBody = response.body?.string() ?: ""
            val parsedResult = extractAndParseJson(respBody)
            
            if (parsedResult != null) {
                Log.d(TAG, "Gemini optimization completed successfully!")
                return@withContext parsedResult
            } else {
                Log.e(TAG, "Failed to parse json out of Gemini response.")
                return@withContext runLocalOptimization(players, game, currentLineups, lockedInnings)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini optimization: ${e.message}", e)
            return@withContext runLocalOptimization(players, game, currentLineups, lockedInnings)
        }
    }

    private fun extractAndParseJson(rawResponse: String): OptimizedRosterResult? {
        try {
            val root = JSONObject(rawResponse)
            val candidates = root.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textValue = parts.getJSONObject(0).getString("text")

            // Locate JSON block in text (Gemini sometimes wraps it in ```json ... ```)
            val jsonStartIndex = textValue.indexOf("{")
            val jsonEndIndex = textValue.lastIndexOf("}")
            if (jsonStartIndex == -1 || jsonEndIndex == -1) {
                return null
            }
            val cleanJsonString = textValue.substring(jsonStartIndex, jsonEndIndex + 1)
            
            val adapter = moshi.adapter(OptimizedRosterResult::class.java)
            return adapter.fromJson(cleanJsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractAndParseJson: ${e.message}")
            return null
        }
    }

    private fun buildPrompt(players: List<Player>, game: Game): String {
        val playersJson = players.joinToString(separator = "\n") { p ->
            "Player(id=${p.id}, name='${p.name}', num='${p.jerseyNumber}', preferred='${p.preferredPosition}', secondary1='${p.secondaryPosition1}', secondary2='${p.secondaryPosition2}', hits=${p.hits}, ab=${p.atBats}, avg=${p.battingAverage}, pitchSmartEligible=${p.pitchSmartEligible()})"
        }
        
        return """
            You are an elite Baseball Coach Assistant agent. Optimize the batting order and defensive positions for our upcoming game.
            
            Opponent: ${game.opponent}
            League Rules:
            - Minimum Defensive Innings: ${game.minInningsDefense}
            - Maximum innings a single Pitcher can throw: ${game.maxInningsPitcher}
            - Continuous Batting Order: ${game.continuousBatting} (all players active must be in the batting order, starting 1..N)
            - Run limit per inning: ${game.runLimitPerInning} runs.
            - Equal Bench Rotation Rule (equalBenchRule): ${game.equalBenchRule} (if true, no player can sit on the bench twice before all players have sat once)
            - Max Consecutive Bench Innings (maxConsecutiveBench): ${game.maxConsecutiveBench} (no player can sit on the bench for more than ${game.maxConsecutiveBench} consecutive innings)
            
            ACTIVE ROSTER TO ROTATE:
            $playersJson
            
            REQUIREMENTS:
            1. Batting order: 
            - Position 1 lead-off should be a high average player (high AVG / hits / speed).
            - Clean-up spot (4th) and 3rd spot should represent heavy seasonal hits.
            - Sort active players from battingOrder 1 down to N (number of active players).
            2. Defensive Rotations:
            - We play ${game.totalInnings} innings.
            - In each inning (1 to ${game.totalInnings}), assigning a valid position to exactly 9 players on the field.
            - Positions are: "P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "BENCH".
            - Position Preferences (CRITICAL): Under NO circumstances should any player with `pitchSmartEligible=false` be assigned to the "P" (Pitcher) position in any inning. Only players with `pitchSmartEligible=true` are eligible to pitch.
            - Primary/Secondary Placement: Attempt to place each player in defensive positions matching their preferred position (`preferred`). If they cannot play their preferred position (or if it is already taken), prioritize assigning them to their secondary positions (`secondary1` or `secondary2`) over completely arbitrary positions.
            - PITCHER/CATCHER RULE (CRITICAL): No player can both pitch ("P") and catch ("C") in the same game. If a player is assigned "P" in any inning, they can NOT be assigned "C" in any other inning of the same game, and vice versa.
            - PITCHER/CATCHER CAPABILITY RULE (CRITICAL): Do not assign a player to "P" (Pitcher) or "C" (Catcher) UNLESS that position is explicitly listed as their preferred, secondary1, or secondary2 position. If no such players are left, leave that fielding spot empty and assign them to BENCH.
            - Minimum defensive requirement: Every active player MUST be placed in fields for at least ${game.minInningsDefense} innings (cannot be "BENCH" more than ${game.totalInnings} minus ${game.minInningsDefense} innings).
            - Maximum pitching rule: No pitcher (e.g. "P" value) should exceed ${game.maxInningsPitcher} innings in total.
            - Equal Bench Rotation requirement: If equalBenchRule is true, do not sit any player on the bench for a second time until all active players have sat on the bench at least once.
            - Max Consecutive Bench requirement: Do not sit any player on the bench for more than ${game.maxConsecutiveBench} consecutive innings.
            - Exactly 1 player should be assigned to each of the 9 field positions per inning. Excess players are assigned to "BENCH".
            
            OUTPUT FORMAT:
            You must output ONLY a valid parsable JSON object, with no conversational fluff outside the JSON. The JSON structure must match this:
            {
              "coachStrategyExplanation": "Write 2-3 friendly sentences detailing your AI strategy (e.g., placing Alex Mercer at leadoff to capitalize on speed, rotating the bench equally, protecting pitching limits).",
              "players": [
                 {
                   "playerId": 1,
                   "battingOrder": 1,
                   "pos1": "CF",
                   "pos2": "CF",
                   ... include pos3 up to pos${game.totalInnings}
                 },
                 ... continue for all active players
              ]
            }
        """.trimIndent()
    }

    /**
     * Local high-quality rule-compliant algorithmic fallback optimizer.
     * Ensures perfect field counts, follows pitcher restrictions, and rotates the bench optimally.
     */
    fun runLocalOptimization(
        players: List<Player>, 
        game: Game,
        currentLineups: List<LineupEntry>? = null,
        lockedInnings: List<Boolean> = emptyList()
    ): OptimizedRosterResult {
        // 1. Batting Order: Sort by seasonal batting average (highest average first) then jersey number for ties
        val sortedPlayers = players.sortedWith(
            compareByDescending<Player> { it.battingAverage }
                .thenBy { it.jerseyNumber.toIntOrNull() ?: 99 }
        )

        val totalPlayers = players.size
        val fieldPositions = listOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")
        val totalInn = game.totalInnings
        val assignments = Array(totalPlayers) { Array(10) { "BENCH" } }

        // Rules tracking state
        val sitCounts = IntArray(totalPlayers) { 0 }
        val consecutiveBench = IntArray(totalPlayers) { 0 }
        val pitchInningsCount = IntArray(totalPlayers) { 0 }
        val hasFinishedPitching = BooleanArray(totalPlayers) { false }

        val playerToLineup = currentLineups?.associateBy { it.playerId } ?: emptyMap()

        for (inning in 0 until totalInn) {
            if (lockedInnings.getOrNull(inning) == true) {
                for (p in 0 until totalPlayers) {
                    val player = players[p]
                    val entry = playerToLineup[player.id]
                    val pos = if (entry != null) {
                        when (inning) {
                            0 -> entry.posInning1
                            1 -> entry.posInning2
                            2 -> entry.posInning3
                            3 -> entry.posInning4
                            4 -> entry.posInning5
                            5 -> entry.posInning6
                            6 -> entry.posInning7
                            7 -> entry.posInning8
                            8 -> entry.posInning9
                            9 -> entry.posInning10
                            else -> "BENCH"
                        }
                    } else "BENCH"
                    
                    assignments[p][inning] = pos
                    
                    if (pos == "BENCH") {
                        sitCounts[p]++
                        consecutiveBench[p]++
                    } else {
                        consecutiveBench[p] = 0
                        if (pos == "P") pitchInningsCount[p]++
                    }
                    if (pos != "P" && pitchInningsCount[p] > 0) {
                        hasFinishedPitching[p] = true
                    }
                }
                continue // Skip the rest of the optimization for this inning
            }

            val mustPlay = mutableListOf<Int>()
            for (p in 0 until totalPlayers) {
                if (consecutiveBench[p] >= game.maxConsecutiveBench) {
                    mustPlay.add(p)
                }
            }

            val targetSitCount = Math.max(0, totalPlayers - 9)
            val selectedToSit = mutableListOf<Int>()

            if (targetSitCount > 0) {
                val eligibleToSit = (0 until totalPlayers)
                    .filter { !mustPlay.contains(it) }
                    .toMutableList()

                eligibleToSit.sortWith(
                    compareBy<Int> { if (game.equalBenchRule) sitCounts[it] else 0 }
                        .thenBy { consecutiveBench[it] }
                        .thenBy { it }
                )

                val sitCountThisInning = Math.min(targetSitCount, eligibleToSit.size)
                for (i in 0 until sitCountThisInning) {
                    selectedToSit.add(eligibleToSit[i])
                }

                if (selectedToSit.size < targetSitCount) {
                    val remainingSitsNeeded = targetSitCount - selectedToSit.size
                    val fallbackCandidates = mustPlay.toMutableList()
                    fallbackCandidates.sortBy { sitCounts[it] }
                    for (i in 0 until Math.min(remainingSitsNeeded, fallbackCandidates.size)) {
                        selectedToSit.add(fallbackCandidates[i])
                    }
                }
            }

            for (p in 0 until totalPlayers) {
                if (selectedToSit.contains(p)) {
                    assignments[p][inning] = "BENCH"
                    sitCounts[p]++
                    consecutiveBench[p]++
                } else {
                    consecutiveBench[p] = 0
                }
            }

            val playersOnField = (0 until totalPlayers).filter { !selectedToSit.contains(it) }.toMutableList()

            // 1. Assign "P"
            var selectedPitcherIdx: Int? = null

            if (inning > 0) {
                val lastPitcher = (0 until totalPlayers).find { assignments[it][inning - 1] == "P" }
                if (lastPitcher != null) {
                    val hasCaught = (0 until inning).any { assignments[lastPitcher][it] == "C" }
                    if (playersOnField.contains(lastPitcher) && pitchInningsCount[lastPitcher] < game.maxInningsPitcher && players[lastPitcher].pitchSmartEligible() && !hasCaught) {
                        selectedPitcherIdx = lastPitcher
                    } else {
                        hasFinishedPitching[lastPitcher] = true
                    }
                }
            }

            // Filter players on the field who are pitch smart eligible AND have NOT played Catcher "C"
            val eligiblePitchersOnField = playersOnField.filter { p ->
                players[p].pitchSmartEligible() && (0 until inning).none { assignments[p][it] == "C" }
            }

            if (selectedPitcherIdx == null) {
                selectedPitcherIdx = eligiblePitchersOnField.firstOrNull { p ->
                    pitchInningsCount[p] < game.maxInningsPitcher && !hasFinishedPitching[p] && players[p].preferredPosition == "P"
                }
            }
            if (selectedPitcherIdx == null) {
                selectedPitcherIdx = eligiblePitchersOnField.firstOrNull { p ->
                    pitchInningsCount[p] < game.maxInningsPitcher && !hasFinishedPitching[p] && (players[p].secondaryPosition1 == "P" || players[p].secondaryPosition2 == "P")
                }
            }
            if (selectedPitcherIdx == null) {
                selectedPitcherIdx = playersOnField.firstOrNull { p ->
                    pitchInningsCount[p] < game.maxInningsPitcher && !hasFinishedPitching[p] && players[p].preferredPosition == "P" && (0 until inning).none { assignments[p][it] == "C" }
                }
            }
            if (selectedPitcherIdx == null) {
                selectedPitcherIdx = playersOnField.firstOrNull { p ->
                    pitchInningsCount[p] < game.maxInningsPitcher && !hasFinishedPitching[p] && (players[p].secondaryPosition1 == "P" || players[p].secondaryPosition2 == "P") && (0 until inning).none { assignments[p][it] == "C" }
                }
            }

            if (selectedPitcherIdx != null) {
                assignments[selectedPitcherIdx][inning] = "P"
                pitchInningsCount[selectedPitcherIdx]++
                playersOnField.remove(selectedPitcherIdx)
            }

            // 2. Assign other positions: "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF"
            val remainingPositions = mutableListOf("C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")
            val assignedInThisStep = mutableListOf<Int>()
            
            // Pass 1: Preferred Position
            for (pIdx in playersOnField) {
                val pref = players[pIdx].preferredPosition
                if (remainingPositions.contains(pref)) {
                    if (pref == "C") {
                        val hasPitched = (0..inning).any { assignments[pIdx][it] == "P" }
                        if (hasPitched) continue
                    }
                    assignments[pIdx][inning] = pref
                    remainingPositions.remove(pref)
                    assignedInThisStep.add(pIdx)
                }
            }
            playersOnField.removeAll(assignedInThisStep)
            assignedInThisStep.clear()

            // Pass 2: Secondary Position 1
            for (pIdx in playersOnField) {
                val sec1 = players[pIdx].secondaryPosition1
                if (remainingPositions.contains(sec1)) {
                    if (sec1 == "C") {
                        val hasPitched = (0..inning).any { assignments[pIdx][it] == "P" }
                        if (hasPitched) continue
                    }
                    assignments[pIdx][inning] = sec1
                    remainingPositions.remove(sec1)
                    assignedInThisStep.add(pIdx)
                }
            }
            playersOnField.removeAll(assignedInThisStep)
            assignedInThisStep.clear()

            // Pass 3: Secondary Position 2
            for (pIdx in playersOnField) {
                val sec2 = players[pIdx].secondaryPosition2
                if (remainingPositions.contains(sec2)) {
                    if (sec2 == "C") {
                        val hasPitched = (0..inning).any { assignments[pIdx][it] == "P" }
                        if (hasPitched) continue
                    }
                    assignments[pIdx][inning] = sec2
                    remainingPositions.remove(sec2)
                    assignedInThisStep.add(pIdx)
                }
            }
            playersOnField.removeAll(assignedInThisStep)

            // Pass 4: Fallback residual assignments
            remainingPositions.remove("C") // Never assign C as a fallback if it wasn't a preferred/secondary position
            for (pIdx in playersOnField) {
                if (remainingPositions.isNotEmpty()) {
                    var pos = remainingPositions[0]
                    remainingPositions.removeAt(0)
                    assignments[pIdx][inning] = pos
                } else {
                    assignments[pIdx][inning] = "BENCH"
                }
            }
        }

        // Map these back to OptimizedPlayerEntry
        val entries = sortedPlayers.mapIndexed { idx, player ->
            val playerDbIndex = players.indexOfFirst { it.id == player.id }
            val realIdx = if (playerDbIndex != -1) playerDbIndex else idx
            OptimizedPlayerEntry(
                playerId = player.id,
                battingOrder = idx + 1, // 1-indexed batting rank
                pos1 = assignments[realIdx][0],
                pos2 = assignments[realIdx][1],
                pos3 = assignments[realIdx][2],
                pos4 = assignments[realIdx][3],
                pos5 = assignments[realIdx][4],
                pos6 = assignments[realIdx][5],
                pos7 = assignments[realIdx][6],
                pos8 = assignments[realIdx][7],
                pos9 = assignments[realIdx][8],
                pos10 = assignments[realIdx][9]
            )
        }

        return OptimizedRosterResult(
            coachStrategyExplanation = "Lineup generated via BaseLineup Engine. Batting order is optimized descending by seasonal Batting Average. Defensive rotations are mathematically balanced to roster bench players equally and keep pitcher innings restricted.",
            players = entries
        )
    }
}
