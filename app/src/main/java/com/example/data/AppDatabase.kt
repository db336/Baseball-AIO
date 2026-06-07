package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BaseballDao {
    // --- Players ---
    @Query("SELECT * FROM players ORDER BY name ASC")
    fun getAllPlayers(): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE id = :id LIMIT 1")
    suspend fun getPlayerById(id: Int): Player?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long

    @Update
    suspend fun updatePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayer(id: Int)

    // --- Games ---
    @Query("SELECT * FROM games ORDER BY gameDate DESC, gameTime DESC")
    fun getAllGames(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getGameById(id: Int): Game?

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun getGameFlowById(id: Int): Flow<Game?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game): Long

    @Update
    suspend fun updateGame(game: Game)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: Int)

    // --- Lineups ---
    @Query("SELECT * FROM lineup_entries WHERE gameId = :gameId ORDER BY battingOrder ASC")
    fun getLineupFlowForGame(gameId: Int): Flow<List<LineupEntry>>

    @Query("SELECT * FROM lineup_entries WHERE gameId = :gameId")
    suspend fun getLineupForGame(gameId: Int): List<LineupEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineupEntries(entries: List<LineupEntry>)

    @Query("DELETE FROM lineup_entries WHERE gameId = :gameId")
    suspend fun deleteLineupForGame(gameId: Int)

    // --- Announcements ---
    @Query("SELECT * FROM announcements ORDER BY timestamp DESC")
    fun getAllAnnouncements(): Flow<List<Announcement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: Announcement): Long

    @Query("DELETE FROM announcements")
    suspend fun clearAnnouncements()
}

@Database(entities = [Player::class, Game::class, LineupEntry::class, Announcement::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun baseballDao(): BaseballDao
}
