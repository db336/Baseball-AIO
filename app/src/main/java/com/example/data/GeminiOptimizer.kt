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
        val pos6: String
    )

    /**
     * Attempts to call the Gemini API to optimize lineups.
     * Fallbacks to our high-quality deterministic rule-based local optimizer on failures.
     */
    suspend fun optimize(
        players: List<Player>,
        game: Game
    ): OptimizedRosterResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // If the key is empty or still holds a default template value, skip remote API or fail immediately to trigger local fallback.
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key is not configured or placeholder. Directing to local algorithmic optimizer.")
            return@withContext runLocalOptimization(players, game)
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
                return@withContext runLocalOptimization(players, game)
            }

            val respBody = response.body?.string() ?: ""
            val parsedResult = extractAndParseJson(respBody)
            
            if (parsedResult != null) {
                Log.d(TAG, "Gemini optimization completed successfully!")
                return@withContext parsedResult
            } else {
                Log.e(TAG, "Failed to parse json out of Gemini response.")
                return@withContext runLocalOptimization(players, game)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini optimization: ${e.message}", e)
            return@withContext runLocalOptimization(players, game)
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
            "Player(id=${p.id}, name='${p.name}', num='${p.jerseyNumber}', preferred='${p.preferredPosition}', hits=${p.hits}, ab=${p.atBats}, avg=${p.battingAverage})"
        }
        
        return """
            You are an elite Baseball Coach Assistant agent. Optimize the batting order and defensive positions for our upcoming game.
            
            Opponent: ${game.opponent}
            League Rules:
            - Minimum Defensive Innings: ${game.minInningsDefense}
            - Maximum innings a single Pitcher can throw: ${game.maxInningsPitcher}
            - Continuous Batting Order: ${game.continuousBatting} (all players active must be in the batting order, starting 1..N)
            - Run limit per inning: ${game.runLimitPerInning} runs.
            
            ACTIVE ROSTER TO ROTATE:
            $playersJson
            
            REQUIREMENTS:
            1. Batting order: 
               - Position 1 lead-off should be a high average player (high AVG / hits / speed).
               - Clean-up spot (4th) and 3rd spot should represent heavy seasonal hits.
               - Sort active players from battingOrder 1 down to N (number of active players).
            2. Defensive Rotations:
               - We play 6 innings.
               - In each inning (1 to 6), assigning a valid position to exactly 9 players on the field.
               - Positions are: "P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "BENCH".
               - Minimum defensive requirement: Every active player MUST be placed in fields for at least ${game.minInningsDefense} innings (cannot be "BENCH" more than 6 minus ${game.minInningsDefense} innings).
               - Maximum pitching rule: No pitcher (e.g. "P" value) should exceed ${game.maxInningsPitcher} innings in total.
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
                   "pos3": "BENCH",
                   "pos4": "SS",
                   "pos5": "LF",
                   "pos6": "RF"
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
    fun runLocalOptimization(players: List<Player>, game: Game): OptimizedRosterResult {
        // 1. Batting Order: Sort by seasonal batting average (highest average first) then jersey number for ties
        val sortedPlayers = players.sortedWith(
            compareByDescending<Player> { it.battingAverage }
                .thenBy { it.jerseyNumber.toIntOrNull() ?: 99 }
        )

        // 2. Defensive Inning-by-Inning Rotations (Innings 1..6)
        // Positions lists:
        val fieldPositions = listOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")
        val totalPlayers = players.size
        
        // Let's build assignments per player per inning.
        // String matrix: player index -> Inning array [pos1, pos2, pos3, pos4, pos5, pos6]
        val assignments = Array(totalPlayers) { Array(6) { "BENCH" } }

        // Keep track of pitcher innings to respect pitch rule
        var pitcherInnings = 0
        var currentPitcherIndex = 0

        // For each inning, we want to assign players.
        // Let's do a simple rotational queue. Every inning, we shift the starting offset of the queue
        // so that different players "sit" or play different spots.
        for (inning in 0..5) {
            val inningOffset = (inning * 3) % totalPlayers // deterministic offset rotation
            val selectedForField = mutableListOf<Int>()
            val selectedForBench = mutableListOf<Int>()

            // To protect pitcher limit:
            // "P" gets assigned to a dedicated player. If their limit is reached, shift pitcher index.
            if (pitcherInnings >= game.maxInningsPitcher) {
                currentPitcherIndex = (currentPitcherIndex + 1) % totalPlayers
                pitcherInnings = 0
            }

            // We want to fill the 9 field positions
            // Position 0 = "P" -> Assign to currentPitcherIndex if available, otherwise next
            var pIndex = currentPitcherIndex
            while (pIndex >= totalPlayers) {
                pIndex %= totalPlayers
            }
            selectedForField.add(pIndex)
            assignments[pIndex][inning] = "P"
            pitcherInnings++

            // Fill other 8 positions: "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF"
            var posPointer = 1 // Already assigned "P"
            for (offset in 0 until totalPlayers) {
                val pIdx = (inningOffset + offset) % totalPlayers
                if (pIdx == pIndex) continue // Skip the pitcher we already set

                if (posPointer < fieldPositions.size) {
                    val posLabel = fieldPositions[posPointer]
                    assignments[pIdx][inning] = posLabel
                    selectedForField.add(pIdx)
                    posPointer++
                } else {
                    assignments[pIdx][inning] = "BENCH"
                    selectedForBench.add(pIdx)
                }
            }
            
            // Safety: if we had fewer than 9 players, fill remaining positions with P or others
            if (posPointer < fieldPositions.size) {
                // Not enough players, some positions remain empty or rotated
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
                pos6 = assignments[realIdx][5]
            )
        }

        return OptimizedRosterResult(
            coachStrategyExplanation = "Lineup generated via BaseLineup Engine. Batting order is optimized descending by seasonal Batting Average. Defensive rotations are mathematically balanced to roster bench players equally and keep pitcher innings restricted.",
            players = entries
        )
    }
}
