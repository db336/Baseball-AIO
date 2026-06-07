package com.example.ui

import android.content.Context
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Announcement
import com.example.data.BaseballRepository
import com.example.data.Game
import com.example.data.GeminiOptimizer
import com.example.data.LineupEntry
import com.example.data.Player
import com.example.utils.NotificationHelper
import com.example.utils.PdfExporter
import com.example.utils.SpreadsheetExporter
import com.example.utils.CalendarExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BaseballViewModel(
    application: Application,
    private val repository: BaseballRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val prefs = context.getSharedPreferences("baselineup_prefs", Context.MODE_PRIVATE)

    private val _teamName = MutableStateFlow(prefs.getString("team_name", "Wildcats") ?: "Wildcats")
    val teamName: StateFlow<String> = _teamName.asStateFlow()

    private val _teamDivision = MutableStateFlow(prefs.getString("team_division", "18U") ?: "18U")
    val teamDivision: StateFlow<String> = _teamDivision.asStateFlow()

    fun updateTeamInfo(name: String, division: String) {
        prefs.edit()
            .putString("team_name", name)
            .putString("team_division", division)
            .apply()
        _teamName.value = name
        _teamDivision.value = division
    }

    // DB state observers
    val players: StateFlow<List<Player>> = repository.allPlayers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val games: StateFlow<List<Game>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val announcements: StateFlow<List<Announcement>> = repository.allAnnouncements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLineupEntries: StateFlow<List<LineupEntry>> = repository.allLineupEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI State & Configurations
    private val _activeGameId = MutableStateFlow<Int?>(null)
    val activeGameId: StateFlow<Int?> = _activeGameId.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _coachMessage = MutableStateFlow("Click 'Optimize' above to generate batting orders and inning rotations via local algorithms or Google Gemini AI.")
    val coachMessage: StateFlow<String> = _coachMessage.asStateFlow()

    private val _syncOn = MutableStateFlow(true)
    val syncOn: StateFlow<Boolean> = _syncOn.asStateFlow()

    init {
        viewModelScope.launch {
            // Pre-seed sample data on first start to provide an immediate elegant preview
            repository.seedIfNeeded()
            
            // Set the first seeded game as active automatically
            repository.allGames.collect { gamesList ->
                if (gamesList.isNotEmpty() && _activeGameId.value == null) {
                    _activeGameId.value = gamesList.first().id
                }
            }
        }
    }

    // Dynamic Lineup observer based on activeGameId
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeLineup: StateFlow<List<LineupEntry>> = _activeGameId
        .flatMapLatest { gameId ->
            if (gameId != null) {
                repository.getLineupFlowForGame(gameId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Game observer based on activeGameId
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGame: StateFlow<Game?> = _activeGameId
        .flatMapLatest { gameId ->
            if (gameId != null) {
                repository.getGameFlowById(gameId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Active Game Control ---
    fun selectActiveGame(gameId: Int) {
        _activeGameId.value = gameId
    }

    // --- Player Management ---
    fun addPlayer(name: String, jerseyNumber: String, preferredPosition: String, note: String) {
        viewModelScope.launch {
            val nextId = repository.insertPlayer(
                Player(
                    name = name,
                    jerseyNumber = jerseyNumber,
                    preferredPosition = preferredPosition,
                    note = note
                )
            ).toInt()

            // If a game is active, append player with a bench slot
            val activeId = _activeGameId.value
            if (activeId != null) {
                val currentLineupSize = repository.getLineupForGame(activeId).size
                repository.saveLineupEntries(
                    listOf(
                        LineupEntry(
                            gameId = activeId,
                            playerId = nextId,
                            battingOrder = currentLineupSize + 1
                        )
                    )
                )
            }
        }
    }

    fun updatePlayer(player: Player) {
        viewModelScope.launch {
            repository.updatePlayer(player)
        }
    }

    fun deletePlayer(id: Int) {
        viewModelScope.launch {
            repository.deletePlayer(id)
        }
    }

    fun updatePlayerStats(
        player: Player,
        atBats: Int,
        hits: Int,
        walks: Int,
        runs: Int,
        rbis: Int,
        strikeouts: Int,
        lastGamePitchCount: Int,
        daysSinceLastPitched: Int
    ) {
        viewModelScope.launch {
            repository.updatePlayer(
                player.copy(
                    atBats = atBats,
                    hits = hits,
                    walks = walks,
                    runs = runs,
                    rbis = rbis,
                    strikeouts = strikeouts,
                    lastGamePitchCount = lastGamePitchCount,
                    daysSinceLastPitched = daysSinceLastPitched
                )
            )
        }
    }

    // --- Game Scheduled Management ---
    private fun parseTimeToMinutes(timeStr: String): Int? {
        try {
            val cleaned = timeStr.trim().uppercase()
            val amPm = if (cleaned.contains("PM")) "PM" else "AM"
            val numbersPart = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = numbersPart.split(":")
            if (parts.isNotEmpty()) {
                var hour = parts[0].toIntOrNull() ?: return null
                val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
                if (amPm == "PM" && hour < 12) {
                    hour += 12
                } else if (amPm == "AM" && hour == 12) {
                    hour = 0
                }
                return hour * 60 + minute
            }
        } catch (e: Exception) {
            // fallback
        }
        return null
    }

    fun addGame(
        opponent: String,
        date: String,
        time: String,
        minInningsDefense: Int,
        maxInningsPitcher: Int,
        continuousBatting: Boolean,
        runLimit: Int,
        equalBenchRule: Boolean,
        maxConsecutiveBench: Int,
        totalInnings: Int
    ) {
        viewModelScope.launch {
            // Check calendar scheduling conflicts
            val sameDayConflictMsg = run {
                val newMint = parseTimeToMinutes(time) ?: return@run null
                val existingGames = games.value
                existingGames.forEach { g ->
                    if (g.gameDate.trim().lowercase() == date.trim().lowercase()) {
                        val extMint = parseTimeToMinutes(g.gameTime)
                        if (extMint != null) {
                            val diff = Math.abs(newMint - extMint)
                            if (diff < 120) {
                                return@run "Warning: Scheduled game times are too close! The matchup against ${g.opponent} at ${g.gameTime} is on the same day (${g.gameDate}) and less than 2 hours apart."
                            }
                        }
                    }
                }
                null
            }

            if (sameDayConflictMsg != null) {
                android.widget.Toast.makeText(context, sameDayConflictMsg, android.widget.Toast.LENGTH_LONG).show()
                NotificationHelper.triggerTeamNotification(
                    context,
                    "Scheduling conflict notice ⚠️",
                    sameDayConflictMsg
                )
            }

            val newGame = Game(
                opponent = opponent,
                gameDate = date,
                gameTime = time,
                status = "Upcoming",
                minInningsDefense = minInningsDefense,
                maxInningsPitcher = maxInningsPitcher,
                continuousBatting = continuousBatting,
                runLimitPerInning = runLimit,
                equalBenchRule = equalBenchRule,
                maxConsecutiveBench = maxConsecutiveBench,
                totalInnings = totalInnings
            )
            val newId = repository.insertGame(newGame)
            _activeGameId.value = newId

            // Copy over existing active roster, setting initial lineup records
            val activePlayers = repository.allPlayers.stateIn(viewModelScope).value
            val entries = activePlayers.mapIndexed { idx, player ->
                LineupEntry(
                    gameId = newId,
                    playerId = player.id,
                    battingOrder = idx + 1,
                    posInning1 = "BENCH",
                    posInning2 = "BENCH",
                    posInning3 = "BENCH",
                    posInning4 = "BENCH",
                    posInning5 = "BENCH",
                    posInning6 = "BENCH"
                )
            }
            repository.saveLineupEntries(entries)

            // Trigger Push Notification of a new schedule
            NotificationHelper.triggerTeamNotification(
                context,
                "New Game Scheduled ⚾",
                "Lineup set for game against the $opponent on $date at $time!"
            )
            repository.insertAnnouncement(
                Announcement(
                    title = "New Game vs $opponent 🗓️",
                    content = "Roster availability and lineup planning are now open for the matchup on $date, $time. Verify rules: Min innings $minInningsDefense."
                )
            )
        }
    }

    fun updateLiveGameScore(
        game: Game,
        runsUs: Int,
        runsOpps: Int,
        inning: Int,
        outs: Int,
        r1: Boolean,
        r2: Boolean,
        r3: Boolean
    ) {
        viewModelScope.launch {
            repository.updateGame(
                game.copy(
                    runsOurTeam = runsUs,
                    runsOpponent = runsOpps,
                    currentInning = inning,
                    currentOuts = outs,
                    runnerOnFirst = r1,
                    runnerOnSecond = r2,
                    runnerOnThird = r3
                )
            )
        }
    }

    fun completeGame(game: Game) {
        viewModelScope.launch {
            repository.updateGame(game.copy(status = "Completed"))
            
            // Build nice system alert
            NotificationHelper.triggerTeamNotification(
                context,
                "Game Recap: Us ${game.runsOurTeam} - Opps ${game.runsOpponent} 📊",
                "The game vs ${game.opponent} was marked finalized. Excellent effort out there!"
            )
            repository.insertAnnouncement(
                Announcement(
                    title = "Game Completed! vs ${game.opponent}",
                    content = "Final Score: We scored ${game.runsOurTeam} runs to ${game.opponent}'s ${game.runsOpponent}. Head over to Stats to check season totals!"
                )
            )
        }
    }

    fun startLiveGame(game: Game) {
        viewModelScope.launch {
            repository.updateGame(game.copy(status = "Live"))
            
            NotificationHelper.triggerTeamNotification(
                context,
                "Game Day Alert: Live Action Started 💬",
                "Come follow the play-by-play lineup rotation and live score trackers!"
            )
        }
    }

    fun deleteGame(gameId: Int) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
            repository.deleteLineupForGame(gameId)
            if (_activeGameId.value == gameId) {
                _activeGameId.value = repository.allGames.stateIn(viewModelScope).value.firstOrNull { it.id != gameId }?.id
            }
        }
    }

    fun deactivateActiveGame() {
        _activeGameId.value = null
    }

    fun completeAndDeactivateGame(game: Game) {
        viewModelScope.launch {
            repository.updateGame(game.copy(status = "Completed"))
            _activeGameId.value = null
            
            NotificationHelper.triggerTeamNotification(
                context,
                "Game Completed & Deactivated 📊",
                "The game vs ${game.opponent} was completed and deactivated. You can now select or schedule another active game."
            )
            repository.insertAnnouncement(
                Announcement(
                    title = "Game Completed! vs ${game.opponent}",
                    content = "Final Score: We scored ${game.runsOurTeam} runs to ${game.opponent}'s ${game.runsOpponent}. Match is now ended and deactivated."
                )
            )
        }
    }

    // --- Lineup and Rotations Operations ---
    fun updateSingleLineupEntry(entry: LineupEntry) {
        viewModelScope.launch {
            repository.saveLineupEntries(listOf(entry))
        }
    }

    fun setSyncOn(enabled: Boolean) {
        _syncOn.value = enabled
    }

    fun optimizeLineup(aiMode: Boolean) {
        val activeGameObj = activeGame.value ?: return
        val currentPlayers = players.value
        if (currentPlayers.isEmpty()) return

        _isOptimizing.value = true
        _coachMessage.value = "Consulting BaseLineup Engine and planning rotations..."

        viewModelScope.launch {
            try {
                if (aiMode) {
                    val result = GeminiOptimizer.optimize(currentPlayers, activeGameObj)
                    applyOptimizedResult(result, activeGameObj.id)
                    _coachMessage.value = result.coachStrategyExplanation
                } else {
                    val result = GeminiOptimizer.runLocalOptimization(currentPlayers, activeGameObj)
                    applyOptimizedResult(result, activeGameObj.id)
                    _coachMessage.value = result.coachStrategyExplanation + " (Optimized offline)"
                }
            } catch (e: Exception) {
                Log.e("BaseballViewModel", "Error in optimizeLineup: ${e.message}")
                _coachMessage.value = "Failed to run optimization automatically. Reason: ${e.message}"
            } finally {
                _isOptimizing.value = false
            }
        }
    }

    private suspend fun applyOptimizedResult(result: GeminiOptimizer.OptimizedRosterResult, gameId: Int) {
        val databaseSavedLineups = repository.getLineupForGame(gameId)
        val entryMap = databaseSavedLineups.associateBy { it.playerId }

        val newEntries = result.players.map { opt ->
            val existing = entryMap[opt.playerId]
            LineupEntry(
                id = existing?.id ?: 0,
                gameId = gameId,
                playerId = opt.playerId,
                battingOrder = opt.battingOrder,
                posInning1 = opt.pos1,
                posInning2 = opt.pos2,
                posInning3 = opt.pos3,
                posInning4 = opt.pos4,
                posInning5 = opt.pos5,
                posInning6 = opt.pos6
            )
        }
        repository.saveLineupEntries(newEntries)
    }

    // --- Export Statistics to Spreadsheet CSV ---
    fun triggerExportSpreadsheet() {
        val gameObj = activeGame.value ?: return
        val allP = players.value
        val entries = activeLineup.value
        
        SpreadsheetExporter.exportGameAndStats(context, gameObj, allP, entries)
    }

    // --- Export Lineup to PDF ---
    fun triggerExportPdf() {
        val gameObj = activeGame.value ?: return
        val allP = players.value
        val entries = activeLineup.value
        
        val pdfFile = PdfExporter.generateLineupPdf(context, gameObj, allP, entries)
        if (pdfFile != null) {
            PdfExporter.exportAsPdf(context, pdfFile)
        }
    }

    // --- Print Lineup ---
    fun triggerPrintPdf() {
        val gameObj = activeGame.value ?: return
        val allP = players.value
        val entries = activeLineup.value
        
        val pdfFile = PdfExporter.generateLineupPdf(context, gameObj, allP, entries)
        if (pdfFile != null) {
            PdfExporter.printPdf(context, pdfFile)
        }
    }

    fun triggerExportCalendarSchedule() {
        val currentGames = games.value
        CalendarExporter.exportScheduleAsIcal(context, currentGames)
    }

    fun reorderLineup(fromIdx: Int, toIdx: Int) {
        viewModelScope.launch {
            val current = activeLineup.value
            if (fromIdx !in current.indices || toIdx !in current.indices) return@launch
            
            val item = current[fromIdx]
            val mutableList = current.toMutableList()
            mutableList.removeAt(fromIdx)
            mutableList.add(toIdx, item)
            
            val updatedEntries = mutableList.mapIndexed { index, entry ->
                entry.copy(battingOrder = index + 1)
            }
            repository.saveLineupEntries(updatedEntries)
        }
    }

    // --- Announcement Creation & Notification Broadcast ---
    fun postAnnouncement(title: String, content: String) {
        viewModelScope.launch {
            repository.insertAnnouncement(
                Announcement(
                    title = title,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
            )
            // Trigger Android System Notification
            NotificationHelper.triggerTeamNotification(context, title, content)
        }
    }
}

class BaseballViewModelFactory(
    private val application: Application,
    private val repository: BaseballRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BaseballViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BaseballViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
