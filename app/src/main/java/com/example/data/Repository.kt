package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BaseballRepository(private val db: AppDatabase) {
    private val dao = db.baseballDao()

    val allPlayers: Flow<List<Player>> = dao.getAllPlayers()
    val allGames: Flow<List<Game>> = dao.getAllGames()
    val allAnnouncements: Flow<List<Announcement>> = dao.getAllAnnouncements()
    val allLineupEntries: Flow<List<LineupEntry>> = dao.getAllLineups()

    suspend fun getPlayerById(id: Int): Player? = dao.getPlayerById(id)
    suspend fun insertPlayer(player: Player) = dao.insertPlayer(player)
    suspend fun updatePlayer(player: Player) = dao.updatePlayer(player)
    suspend fun deletePlayer(id: Int) = dao.deletePlayer(id)

    suspend fun getGameById(id: Int): Game? = dao.getGameById(id)
    fun getGameFlowById(id: Int): Flow<Game?> = dao.getGameFlowById(id)
    suspend fun insertGame(game: Game): Int = dao.insertGame(game).toInt()
    suspend fun updateGame(game: Game) = dao.updateGame(game)
    suspend fun deleteGame(id: Int) = dao.deleteGame(id)

    fun getLineupFlowForGame(gameId: Int): Flow<List<LineupEntry>> = dao.getLineupFlowForGame(gameId)
    suspend fun getLineupForGame(gameId: Int): List<LineupEntry> = dao.getLineupForGame(gameId)
    suspend fun saveLineupEntries(entries: List<LineupEntry>) {
        dao.insertLineupEntries(entries)
    }
    suspend fun deleteLineupForGame(gameId: Int) = dao.deleteLineupForGame(gameId)

    suspend fun insertAnnouncement(announcement: Announcement) = dao.insertAnnouncement(announcement)
    suspend fun clearAnnouncements() = dao.clearAnnouncements()

    // Seeds sample roster and initial schedule if database is empty
    suspend fun seedIfNeeded() {
        val currentPlayers = allPlayers.first()
        if (currentPlayers.isEmpty()) {
            val samplePlayers = listOf(
                Player(name = "Ethan Cross", jerseyNumber = "99", preferredPosition = "P", atBats = 39, hits = 15, walks = 4, runs = 12, rbis = 9, strikeouts = 5, note = "Starting Pitcher. Solid knuckleball."),
                Player(name = "Alex Mercer", jerseyNumber = "14", preferredPosition = "CF", atBats = 48, hits = 17, walks = 5, runs = 15, rbis = 12, strikeouts = 3, note = "Leadoff hitter. Great speed in the outfield."),
                Player(name = "Carter Collins", jerseyNumber = "22", preferredPosition = "C", atBats = 34, hits = 14, walks = 6, runs = 8, rbis = 14, strikeouts = 2, note = "Excellent framing and pitch calling. Heavy slugger."),
                Player(name = "Danny Vance", jerseyNumber = "5", preferredPosition = "SS", atBats = 45, hits = 15, walks = 3, runs = 10, rbis = 8, strikeouts = 6, note = "Quick arm and glove. Good bunting skill."),
                Player(name = "Hunter Hayes", jerseyNumber = "24", preferredPosition = "3B", atBats = 42, hits = 13, walks = 5, runs = 9, rbis = 11, strikeouts = 8, note = "Strong arm across the diamond."),
                Player(name = "Gavin Stone", jerseyNumber = "11", preferredPosition = "2B", atBats = 44, hits = 12, walks = 4, runs = 11, rbis = 7, strikeouts = 4, note = "Extremely agile on double plays."),
                Player(name = "Flynn Ryder", jerseyNumber = "18", preferredPosition = "1B", atBats = 40, hits = 10, walks = 7, runs = 7, rbis = 10, strikeouts = 9, note = "Tall kid. Great reach for catching off-line throws."),
                Player(name = "Brady Thomas", jerseyNumber = "7", preferredPosition = "LF", atBats = 38, hits = 11, walks = 5, runs = 6, rbis = 5, strikeouts = 7, note = "Good covering deep left."),
                Player(name = "Jack Wilson", jerseyNumber = "2", preferredPosition = "RF", atBats = 36, hits = 8, walks = 3, runs = 5, rbis = 4, strikeouts = 11, note = "Enthusiastic and fast runner."),
                Player(name = "Mason Brooks", jerseyNumber = "42", preferredPosition = "RF", atBats = 30, hits = 8, walks = 2, runs = 6, rbis = 6, strikeouts = 6, note = "Good contact rate against lefties."),
                Player(name = "Nolan Cole", jerseyNumber = "10", preferredPosition = "LF", atBats = 22, hits = 4, walks = 4, runs = 3, rbis = 2, strikeouts = 5, note = "Improving swing mechanics."),
                Player(name = "Owen Reed", jerseyNumber = "4", preferredPosition = "SS", atBats = 20, hits = 6, walks = 3, runs = 4, rbis = 3, strikeouts = 4, note = "Solid backup utility player.")
            )
            for (p in samplePlayers) {
                dao.insertPlayer(p)
            }

            // Seed initial announcements
            val sampleAnnouncements = listOf(
                Announcement(
                    title = "Playoffs Schedule Update 🏆",
                    content = "The league playoff bracket has been locked! We play the Thunderballs this Wednesday at 6 PM. Roster RSVP completed.",
                    timestamp = System.currentTimeMillis() - 3600000
                ),
                Announcement(
                    title = "Game Time Moved (Rain Delayed) 🌧️",
                    content = "League directors moved tonight's game schedule to 5:45 PM instead of 5:30 PM due to pre-game field touch-ups. Wear away jerseys.",
                    timestamp = System.currentTimeMillis() - (3600000 * 24)
                ),
                Announcement(
                    title = "Continuous Batting Rule Enforced",
                    content = "Reminder: For our District division, all players on the roster bat continuously, and defense requires a minimum of 2 innings in the field.",
                    timestamp = System.currentTimeMillis() - (3600000 * 48)
                )
            )
            for (a in sampleAnnouncements) {
                dao.insertAnnouncement(a)
            }

            // Seed an initial game
            val insertedGameId = dao.insertGame(
                Game(
                    opponent = "Thunderballs",
                    gameDate = "June 10, 2026",
                    gameTime = "6:00 PM",
                    status = "Upcoming",
                    minInningsDefense = 2,
                    maxInningsPitcher = 2,
                    continuousBatting = true,
                    runLimitPerInning = 5
                )
            ).toInt()

            // Generate a starting lineup entry for this game
            val playersList = dao.getAllPlayers().first()
            val initialEntries = playersList.mapIndexed { idx, player ->
                val order = idx + 1
                LineupEntry(
                    gameId = insertedGameId,
                    playerId = player.id,
                    battingOrder = order,
                    posInning1 = when (player.preferredPosition) {
                        "P" -> "P"
                        "C" -> "C"
                        "1B" -> "1B"
                        "2B" -> "2B"
                        "3B" -> "3B"
                        "SS" -> "SS"
                        "CF" -> "CF"
                        "LF" -> "LF"
                        else -> "BENCH"
                    },
                    posInning2 = "BENCH" // let auto-builder fill or manual
                )
            }
            dao.insertLineupEntries(initialEntries)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BaseballRepository? = null

        fun getInstance(context: Context): BaseballRepository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baselineup_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                val repo = BaseballRepository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
