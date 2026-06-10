package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import kotlin.math.roundToInt
import com.example.data.Announcement
import com.example.data.Game
import com.example.data.LineupEntry
import com.example.data.Player
import com.example.data.getPosForInning
import com.example.data.setPosForInning
import com.example.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.utils.RosterCsvHelper

// Supported defensive choices
val POSITIONS = listOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "BENCH")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: BaseballViewModel) {
    val players by viewModel.players.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    
    val activeGameId by viewModel.activeGameId.collectAsStateWithLifecycle()
    val activeGame by viewModel.activeGame.collectAsStateWithLifecycle()
    val activeLineup by viewModel.activeLineup.collectAsStateWithLifecycle()
    val allLineupEntries by viewModel.allLineupEntries.collectAsStateWithLifecycle()
    
    val isOptimizing by viewModel.isOptimizing.collectAsStateWithLifecycle()
    val coachMessage by viewModel.coachMessage.collectAsStateWithLifecycle()
    val syncOn by viewModel.syncOn.collectAsStateWithLifecycle()

    val teamName by viewModel.teamName.collectAsStateWithLifecycle()
    val teamDivision by viewModel.teamDivision.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf("lineups") } // "roster", "lineups"
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var gameToEdit by remember { mutableStateOf<Game?>(null) }
    var showEditTeamDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val game = activeGame
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SleekPrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsBaseball,
                                contentDescription = "Baseball Icon",
                                tint = SleekOnPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val displayTitle = game?.let { "$teamName vs ${it.opponent}" } ?: "$teamName Studio"
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (game?.status == "Live") {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFFEF4444), CircleShape)
                                    )
                                    Text(
                                        text = "LIVE • Inning ${game.currentInning} (Top)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = Color(0xFFEF4444)
                                    )
                                } else {
                                    Text(
                                        text = game?.let { "${it.gameDate} @ ${it.gameTime}" } ?: "Play ball • Offline roster scheduler",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = SleekTextMuted
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    activeGame?.let {
                        IconButton(
                            onClick = { viewModel.triggerExportSpreadsheet() },
                            modifier = Modifier.testTag("export_spreadsheet")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Spreadsheet Report",
                                tint = SleekPrimary
                            )
                        }
                    }
                    /*
                    IconButton(onClick = { showAddGameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = "New Game Matchup",
                            tint = SleekPrimary
                        )
                    }
                    */
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF3F4F9),
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                listOf(
                    Triple("roster", "Roster", Icons.Default.People),
                    Triple("lineups", "Lineups", Icons.Default.FormatListNumbered),
                    Triple("schedule", "Schedule", Icons.Default.CalendarMonth)
                ).forEach { (id, label, icon) ->
                    val selected = currentTab == id
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = id },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SleekOnPrimaryContainer,
                            selectedTextColor = SleekOnPrimaryContainer,
                            unselectedIconColor = SleekTextSecondary,
                            unselectedTextColor = SleekTextSecondary,
                            indicatorColor = SleekPrimaryContainer
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal scrolling row of Quick Stats Chips
            QuickStatsRow(
                activePlayersCount = players.filter { it.isAvailable }.size,
                activeGame = activeGame,
                onOptimizeClick = { viewModel.optimizeLineup(aiMode = false) }
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentTab) {
                    "roster" -> RosterTab(
                        players = players,
                        teamName = teamName,
                        teamDivision = teamDivision,
                        onEditTeamClick = { showEditTeamDialog = true },
                        onAddClick = { showAddPlayerDialog = true },
                        onUpdatePlayer = { viewModel.updatePlayer(it) },
                        onDeletePlayer = { viewModel.deletePlayer(it) },
                        onUpdateStats = { p, ab, h, w, r, rbi, so, pit, rst -> 
                            viewModel.updatePlayerStats(p, ab, h, w, r, rbi, so, pit, rst) 
                        },
                        onImportRoster = { list, replace -> viewModel.importRoster(list, replace) }
                    )
                    "lineups" -> LineupsTab(
                        players = players,
                        activeGame = activeGame,
                        activeLineup = activeLineup,
                        allLineupEntries = allLineupEntries,
                        isOptimizing = isOptimizing,
                        coachMessage = coachMessage,
                        gamesList = games,
                        onSelectGame = { viewModel.selectActiveGame(it) },
                        onOptimize = { ai, lockedInnings -> viewModel.optimizeLineup(aiMode = ai, lockedInnings = lockedInnings) },
                        onUpdateEntry = { viewModel.updateSingleLineupEntry(it) },
                        onPrintPdf = { viewModel.triggerPrintPdf() },
                        onExportPdf = { viewModel.triggerExportPdf() },
                        onExportSpreadsheet = { viewModel.triggerExportSpreadsheet() },
                        onAddGameClick = { 
                            gameToEdit = null
                            showAddGameDialog = true 
                        },
                        onReorder = { from, to -> viewModel.reorderLineup(from, to) }
                    )
                    "schedule" -> ScheduleTab(
                        games = games,
                        activeGameId = activeGameId,
                        teamName = teamName,
                        onSelectGame = { viewModel.selectActiveGame(it) },
                        onDeleteGame = { viewModel.deleteGame(it) },
                        onAddGameClick = { 
                            gameToEdit = null
                            showAddGameDialog = true 
                        },
                        onExportCalendar = { viewModel.triggerExportCalendarSchedule() },
                        onEditGameClick = { game ->
                            gameToEdit = game
                            showAddGameDialog = true
                        }
                    )
                }

                // Dialog integrations
                if (showEditTeamDialog) {
                    EditTeamDialog(
                        currentName = teamName,
                        currentDivision = teamDivision,
                        onDismiss = { showEditTeamDialog = false },
                        onSave = { name, div ->
                            viewModel.updateTeamInfo(name, div)
                            showEditTeamDialog = false
                        }
                    )
                }

                if (showAddPlayerDialog) {
                    AddPlayerDialog(
                        onDismiss = { showAddPlayerDialog = false },
                        onSave = { name, jersey, pref, sec1, sec2, note ->
                            viewModel.addPlayer(name, jersey, pref, sec1, sec2, note)
                            showAddPlayerDialog = false
                        }
                    )
                }

                if (showAddGameDialog) {
                    AddGameDialog(
                        initialGame = gameToEdit,
                        onDismiss = { 
                            showAddGameDialog = false 
                            gameToEdit = null
                        },
                        onSave = { opp, date, time, minIn, maxPi, cont, limit, equalBench, maxBench, totalInn ->
                            if (gameToEdit == null) {
                                viewModel.addGame(opp, date, time, minIn, maxPi, cont, limit, equalBench, maxBench, totalInn)
                            } else {
                                val updatedGame = gameToEdit!!.copy(
                                    opponent = opp,
                                    gameDate = date,
                                    gameTime = time,
                                    minInningsDefense = minIn,
                                    maxInningsPitcher = maxPi,
                                    continuousBatting = cont,
                                    runLimitPerInning = limit,
                                    equalBenchRule = equalBench,
                                    maxConsecutiveBench = maxBench,
                                    totalInnings = totalInn
                                )
                                viewModel.updateGame(updatedGame)
                            }
                            showAddGameDialog = false
                            gameToEdit = null
                        }
                    )
                }
            }
        }
    }
}

// ========================== QUICK STATS ROW ==========================
@Composable
fun QuickStatsRow(
    activePlayersCount: Int,
    activeGame: Game?,
    onOptimizeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optimize Pill (Brand Accent Blue)
        Surface(
            onClick = onOptimizeClick,
            color = SleekPrimaryDark,
            shape = CircleShape,
            modifier = Modifier.clickable { onOptimizeClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Optimize",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Optimize Lineup",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Player count Badge
        Surface(
            color = Color.White,
            shape = CircleShape,
            border = BorderStroke(1.dp, SleekBorderMedium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Active Roster",
                    tint = SleekTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$activePlayersCount Active",
                    color = SleekTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active scheduling status/Live Matchup status Pill
        val statusLabel = if (activeGame?.status == "Live") "LIVE • Inning ${activeGame.currentInning}" else if (activeGame != null) "Game Scheduled" else "No Game Scheduled"
        val statusColor = if (activeGame?.status == "Live") Color(0xFFEF4444) else if (activeGame != null) SleekPrimary else Color(0xFF6B7280)
        
        Surface(
            color = Color.White,
            shape = CircleShape,
            border = BorderStroke(1.dp, SleekBorderMedium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = statusLabel,
                    color = SleekTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ========================== ROSTER TAB ==========================
@Composable
fun RosterTab(
    players: List<Player>,
    teamName: String,
    teamDivision: String,
    onEditTeamClick: () -> Unit,
    onAddClick: () -> Unit,
    onUpdatePlayer: (Player) -> Unit,
    onDeletePlayer: (Int) -> Unit,
    onUpdateStats: (Player, Int, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onImportRoster: (List<Player>, Boolean) -> Unit
) {
    var selectedPlayerForStats by remember { mutableStateOf<Player?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Team Info Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(2.dp, TurfLime, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsBaseball,
                            contentDescription = "Team Logo Badge",
                            tint = TurfLime,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = teamName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BaseballWhite
                        )
                        Text(
                            text = "Division: $teamDivision  |  Roster Size: ${players.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                IconButton(
                    onClick = onEditTeamClick,
                    modifier = Modifier.testTag("edit_team_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Team Information",
                        tint = ClayAmber
                    )
                }
            }
        }

        val activeCount = players.count { it.isAvailable }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Player Roster",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BaseballWhite
                )
                Text(
                    text = "$activeCount active players on the squad",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (activeCount < 9 && players.isNotEmpty()) {
                    Text(
                        text = "⚠ Less than 9 active players!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B), // warning amber/yellow
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                modifier = Modifier.testTag("add_player_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Player", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showImportDialog by remember { mutableStateOf(false) }
            var showExportDialog by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f).testTag("import_csv_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OutfieldGreen),
                border = BorderStroke(1.dp, OutfieldGreen.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.Upload, contentDescription = "Import", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f).testTag("export_csv_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TurfLime),
                border = BorderStroke(1.dp, TurfLime.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (showImportDialog) {
                RosterImportDialog(
                    onDismiss = { showImportDialog = false },
                    onImport = onImportRoster
                )
            }

            if (showExportDialog) {
                RosterExportDialog(
                    players = players,
                    onDismiss = { showExportDialog = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (players.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Empty Roster",
                        modifier = Modifier.size(64.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No players added to the team yet.", color = BaseballWhite)
                    Text("Click 'Add Player' to define your squad roster.", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(players) { player ->
                    RosterItemCard(
                        player = player,
                        onUpdate = onUpdatePlayer,
                        onDelete = { onDeletePlayer(player.id) },
                        onStatsClick = { selectedPlayerForStats = player }
                    )
                }
            }
        }

        // Stats Editor Modal Sheet/Dialog
        selectedPlayerForStats?.let { player ->
            StatsEditorDialog(
                player = player,
                onDismiss = { selectedPlayerForStats = null },
                onSave = { ab, h, w, r, rbi, so, pit, rst ->
                    onUpdateStats(player, ab, h, w, r, rbi, so, pit, rst)
                    selectedPlayerForStats = null
                }
            )
        }
    }
}

@Composable
fun RosterItemCard(
    player: Player,
    onUpdate: (Player) -> Unit,
    onDelete: () -> Unit,
    onStatsClick: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    text = "Delete Player?",
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${player.name} (#${player.jerseyNumber}) from the roster? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = StadiumSlateSurface
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Upper right PitchSmart status badge
            val eligible = player.pitchSmartEligible()
            val requiredRest = player.pitchSmartRequiredRest()
            val daysRestLeft = (requiredRest - player.daysSinceLastPitched).coerceAtLeast(0)

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (eligible) Color(0xFF15803D) else Color(0xFFB91C1C),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (eligible) "PitchSmart: Eligible" else "Ineligible (Rest: ${daysRestLeft}d left)",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (player.lastGamePitchCount > 0) {
                    Text(
                        text = "${player.lastGamePitchCount} pitches | rest: ${player.daysSinceLastPitched}d",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Main Content Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                // Top Row: Jersey, Name/AVG, and Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Jersey Bubble
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(2.dp, OutfieldGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${player.jerseyNumber}",
                            fontWeight = FontWeight.Black,
                            color = OutfieldGreen,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Player Name
                        Text(
                            text = player.name,
                            fontWeight = FontWeight.Bold,
                            color = BaseballWhite,
                            fontSize = 16.sp
                        )

                        // Batting Average BELOW Player Name
                        Text(
                            text = "AVG: ${player.formattedBattingAverage()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClayAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    // Controls
                    Row(
                        modifier = Modifier.padding(top = 30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Availability Switch
                        IconButton(onClick = { onUpdate(player.copy(isAvailable = !player.isAvailable)) }) {
                            Icon(
                                imageVector = if (player.isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = "Toggle availability",
                                tint = if (player.isAvailable) OutfieldGreen else Color.Red
                            )
                        }
                        var showEditDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Player Info",
                                tint = ClayAmber
                            )
                        }
                        if (showEditDialog) {
                            EditPlayerInfoDialog(
                                player = player,
                                onDismiss = { showEditDialog = false },
                                onSave = { name, jersey, preferred, sec1, sec2, note ->
                                    onUpdate(player.copy(
                                        name = name,
                                        jerseyNumber = jersey,
                                        preferredPosition = preferred,
                                        secondaryPosition1 = sec1,
                                        secondaryPosition2 = sec2,
                                        note = note
                                    ))
                                    showEditDialog = false
                                }
                            )
                        }
                        IconButton(onClick = onStatsClick) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Edit Stats",
                                tint = TurfLime
                            )
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Player",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Separated Position Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pref badge
                    Box(
                        modifier = Modifier
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(1.dp, TurfLime.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "Pref: ${player.preferredPosition}",
                            color = TurfLime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    // Sec 1 badge
                    if (player.secondaryPosition1.trim().uppercase() != "BENCH" && player.secondaryPosition1.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .border(1.dp, TurfLime.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = "Sec 1: ${player.secondaryPosition1.trim().uppercase()}",
                                color = TurfLime.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Sec 2 badge
                    if (player.secondaryPosition2.trim().uppercase() != "BENCH" && player.secondaryPosition2.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .border(1.dp, TurfLime.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = "Sec 2: ${player.secondaryPosition2.trim().uppercase()}",
                                color = TurfLime.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EditPlayerInfoDialog(
    player: Player,
    onDismiss: () -> Unit,
    onSave: (name: String, jersey: String, preferred: String, secondary1: String, secondary2: String, note: String) -> Unit
) {
    var nameText by remember { mutableStateOf(player.name) }
    var jerseyText by remember { mutableStateOf(player.jerseyNumber) }
    var preferredText by remember { mutableStateOf(player.preferredPosition) }
    var secondary1Text by remember { mutableStateOf(player.secondaryPosition1) }
    var secondary2Text by remember { mutableStateOf(player.secondaryPosition2) }
    var noteText by remember { mutableStateOf(player.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Player Info: ${player.name}",
                style = MaterialTheme.typography.titleMedium,
                color = BaseballWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Player Name") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_player_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = StadiumGrayBorder
                    )
                )
                OutlinedTextField(
                    value = jerseyText,
                    onValueChange = { jerseyText = it },
                    label = { Text("Jersey Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_player_jersey"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = StadiumGrayBorder
                    )
                )
                OutlinedTextField(
                    value = preferredText,
                    onValueChange = { preferredText = it.uppercase() },
                    label = { Text("Preferred Position (e.g., P, C, SS)") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_player_preferred"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = StadiumGrayBorder
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = secondary1Text,
                        onValueChange = { secondary1Text = it.uppercase() },
                        label = { Text("Secondary Pos 1") },
                        modifier = Modifier.weight(1f).testTag("edit_player_sec1"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BaseballWhite,
                            unfocusedTextColor = BaseballWhite,
                            focusedLabelColor = TurfLime,
                            unfocusedLabelColor = TextSecondary,
                            focusedBorderColor = TurfLime,
                            unfocusedBorderColor = StadiumGrayBorder
                        )
                    )
                    OutlinedTextField(
                        value = secondary2Text,
                        onValueChange = { secondary2Text = it.uppercase() },
                        label = { Text("Secondary Pos 2") },
                        modifier = Modifier.weight(1f).testTag("edit_player_sec2"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BaseballWhite,
                            unfocusedTextColor = BaseballWhite,
                            focusedLabelColor = TurfLime,
                            unfocusedLabelColor = TextSecondary,
                            focusedBorderColor = TurfLime,
                            unfocusedBorderColor = StadiumGrayBorder
                        )
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Coach/Player Notes") },
                    placeholder = { Text("Enter custom player notes...") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = StadiumGrayBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotEmpty() && jerseyText.isNotEmpty()) {
                        onSave(nameText, jerseyText, preferredText, secondary1Text, secondary2Text, noteText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TurfLime),
                modifier = Modifier.testTag("save_player_info_btn")
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = StadiumSlateSurface
    )
}

@Composable
fun EditTeamDialog(
    currentName: String,
    currentDivision: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var teamNameInput by remember { mutableStateOf(currentName) }
    var teamDivisionInput by remember { mutableStateOf(currentDivision) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Team Information",
                fontWeight = FontWeight.Bold,
                color = BaseballWhite
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = teamNameInput,
                    onValueChange = { teamNameInput = it },
                    label = { Text("Team Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                    )
                )

                OutlinedTextField(
                    value = teamDivisionInput,
                    onValueChange = { teamDivisionInput = it },
                    label = { Text("Age Division (e.g., 18U, 12U)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (teamNameInput.isNotBlank()) {
                        onSave(teamNameInput, teamDivisionInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = StadiumSlateSurface
    )
}


// ========================== DIALOGS ==========================
@Composable
fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, jersey: String, preferred: String, secondary1: String, secondary2: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var jersey by remember { mutableStateOf("") }
    var prefPos by remember { mutableStateOf("LF") }
    var secPos1 by remember { mutableStateOf("BENCH") }
    var secPos2 by remember { mutableStateOf("BENCH") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Baseball Player") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth().testTag("add_player_name")
                )
                OutlinedTextField(
                    value = jersey,
                    onValueChange = { jersey = it },
                    label = { Text("Jersey Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_player_jersey")
                )
                OutlinedTextField(
                    value = prefPos,
                    onValueChange = { prefPos = it.uppercase() },
                    label = { Text("Preferred Position (e.g. CF, P, C, SS)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_player_preferred")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = secPos1,
                        onValueChange = { secPos1 = it.uppercase() },
                        label = { Text("Secondary Pos 1") },
                        modifier = Modifier.weight(1f).testTag("add_player_sec1")
                    )
                    OutlinedTextField(
                        value = secPos2,
                        onValueChange = { secPos2 = it.uppercase() },
                        label = { Text("Secondary Pos 2") },
                        modifier = Modifier.weight(1f).testTag("add_player_sec2")
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Coaching/Availability Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("add_player_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && jersey.isNotEmpty()) {
                        onSave(name, jersey, prefPos, secPos1, secPos2, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                modifier = Modifier.testTag("submit_player_btn")
            ) {
                Text("Add Player")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StatsEditorDialog(
    player: Player,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int, Int, Int, Int, Int) -> Unit
) {
    var ab by remember { mutableStateOf(player.atBats.toString()) }
    var hits by remember { mutableStateOf(player.hits.toString()) }
    var walks by remember { mutableStateOf(player.walks.toString()) }
    var runs by remember { mutableStateOf(player.runs.toString()) }
    var rbis by remember { mutableStateOf(player.rbis.toString()) }
    var so by remember { mutableStateOf(player.strikeouts.toString()) }
    var lastGamePitchCount by remember { mutableStateOf(player.lastGamePitchCount.toString()) }
    var daysSinceLastPitched by remember { mutableStateOf((if (player.daysSinceLastPitched == 5) 0 else player.daysSinceLastPitched).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Stats for ${player.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ab,
                        onValueChange = { ab = it },
                        label = { Text("At Bats (AB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hits,
                        onValueChange = { hits = it },
                        label = { Text("Hits (H)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = walks,
                        onValueChange = { walks = it },
                        label = { Text("Walks (BB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = runs,
                        onValueChange = { runs = it },
                        label = { Text("Runs (R)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rbis,
                        onValueChange = { rbis = it },
                        label = { Text("RBIs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = so,
                        onValueChange = { so = it },
                        label = { Text("Strikeouts") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PitchSmart 18U Tracking",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lastGamePitchCount,
                        onValueChange = { lastGamePitchCount = it },
                        label = { Text("Last Game Pitches") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = daysSinceLastPitched,
                        onValueChange = { daysSinceLastPitched = it },
                        label = { Text("Days Rest Elapsed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ab.toIntOrNull() ?: 0,
                        hits.toIntOrNull() ?: 0,
                        walks.toIntOrNull() ?: 0,
                        runs.toIntOrNull() ?: 0,
                        rbis.toIntOrNull() ?: 0,
                        so.toIntOrNull() ?: 0,
                        lastGamePitchCount.toIntOrNull() ?: 0,
                        daysSinceLastPitched.toIntOrNull() ?: 0
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Text("Save Stats")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ========================== LINEUPS TAB ==========================
@Composable
fun LineupsTab(
    players: List<Player>,
    activeGame: Game?,
    activeLineup: List<LineupEntry>,
    allLineupEntries: List<LineupEntry>,
    isOptimizing: Boolean,
    coachMessage: String,
    gamesList: List<Game>,
    onSelectGame: (Int) -> Unit,
    onOptimize: (Boolean, List<Boolean>) -> Unit,
    onUpdateEntry: (LineupEntry) -> Unit,
    onPrintPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportSpreadsheet: () -> Unit,
    onAddGameClick: () -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    var showSelectGameDropdown by remember { mutableStateOf(false) }
    var dismissFairPlayWarnings by remember(activeLineup, activeGame) { mutableStateOf(false) }
    var lockedInnings by remember(activeGame) { mutableStateOf(List(10) { false }) }
    
    // In-line validation checks:
    val playerMap = remember(players) { players.associateBy { it.id } }
    val totalInnings = activeGame?.totalInnings ?: 6

    // PitchSmart safety checker for 18U + Same-day limit check
    val pitchSmartViolations = remember(activeLineup, players, activeGame, gamesList, allLineupEntries) {
        val violations = mutableListOf<String>()
        val dateMatches = activeGame?.gameDate ?: ""
        if (dateMatches.isNotEmpty()) {
            val sameDayGames = gamesList.filter { it.gameDate == dateMatches }
            val sameDayGameIds = sameDayGames.map { it.id }.toSet()

            activeLineup.forEach { entry ->
                val player = playerMap[entry.playerId]
                if (player != null) {
                    val totalInns = activeGame?.totalInnings ?: 6
                    val isCurrentlyPitchingInCurrentGame = 
                        (1..totalInns).any { entry.getPosForInning(it) == "P" }

                    // 1. Standard PitchSmart rest check
                    if (!player.pitchSmartEligible() && isCurrentlyPitchingInCurrentGame) {
                        val daysLeft = (player.pitchSmartRequiredRest() - player.daysSinceLastPitched).coerceAtLeast(0)
                        violations.add("PitchSmart 18U Safety warning: ${player.name} is scheduled to PITCH but requires safety rest ($daysLeft days remaining).")
                    }

                    // 2. 7 innings calendar-day limit across all same-day game entries
                    var totalPitchingInningsToday = 0
                    allLineupEntries.forEach { sameEntry ->
                        if (sameEntry.playerId == player.id && sameDayGameIds.contains(sameEntry.gameId)) {
                            val matchingGame = sameDayGames.find { it.id == sameEntry.gameId }
                            val limitInns = matchingGame?.totalInnings ?: 6
                            for (i in 1..limitInns) {
                                if (sameEntry.getPosForInning(i) == "P") totalPitchingInningsToday++
                            }
                        }
                    }

                    if (totalPitchingInningsToday >= 7 && isCurrentlyPitchingInCurrentGame) {
                        violations.add("PitchSmart Safety violation: ${player.name} is INELIGIBLE as a pitcher today because they are scheduled to pitch $totalPitchingInningsToday innings on this calendar day ($dateMatches). Limit is 7.")
                    }
                }
            }
        }
        violations
    }

    // Pitch/catch safety check: players can not pitch and catch in the same game or calendar day
    val pitchCatchViolations = remember(activeLineup, players, activeGame, gamesList, allLineupEntries) {
        val violations = mutableListOf<String>()
        val dateMatches = activeGame?.gameDate ?: ""
        if (dateMatches.isNotEmpty()) {
            val sameDayGames = gamesList.filter { it.gameDate == dateMatches }
            val sameDayGameIds = sameDayGames.map { it.id }.toSet()

            activeLineup.forEach { entry ->
                val player = playerMap[entry.playerId]
                if (player != null) {
                    val totalInns = activeGame?.totalInnings ?: 6
                    val isCurrentlyPitchingInCurrentGame = (1..totalInns).any { entry.getPosForInning(it) == "P" }
                    val isCurrentlyCatchingInCurrentGame = (1..totalInns).any { entry.getPosForInning(it) == "C" }

                    // 1. Same game check
                    if (isCurrentlyPitchingInCurrentGame && isCurrentlyCatchingInCurrentGame) {
                        violations.add("Safety rule violation: ${player.name} is scheduled to both PITCH and CATCH in this game.")
                    }

                    // 2. Calendar day check (across all games on same-day)
                    var hasPitchedToday = false
                    var hasCaughtToday = false

                    allLineupEntries.forEach { sameEntry ->
                        if (sameEntry.playerId == player.id && sameDayGameIds.contains(sameEntry.gameId)) {
                            val matchingGame = sameDayGames.find { it.id == sameEntry.gameId }
                            val limitInns = matchingGame?.totalInnings ?: 6
                            for (i in 1..limitInns) {
                                val pos = sameEntry.getPosForInning(i)
                                if (pos == "P") hasPitchedToday = true
                                if (pos == "C") hasCaughtToday = true
                            }
                        }
                    }

                    if (hasPitchedToday && hasCaughtToday) {
                        violations.add("Safety rule violation: ${player.name} is scheduled to both PITCH and CATCH on this calendar day ($dateMatches).")
                    }
                }
            }
        }
        violations.distinct()
    }

    // 1. Double assignments (two players on same position in same inning)
    val doubleAssignments = remember(activeLineup, activeGame) {
        val totalInns = activeGame?.totalInnings ?: 6
        val innSets = Array(11) { mutableSetOf<String>() }
        val duplicates = mutableListOf<String>()

        activeLineup.forEach { entry ->
            for (i in 1..totalInns) {
                val pos = entry.getPosForInning(i)
                if (pos != "BENCH" && !innSets[i].add(pos)) {
                    duplicates.add("Inning $i: Duplicate $pos")
                }
            }
        }
        duplicates.distinct()
    }

    // 2. Equal bench rule validation
    val equalBenchViolations = remember(activeLineup, activeGame) {
        if (activeGame?.equalBenchRule != true) emptyList()
        else {
            val totalInns = activeGame.totalInnings
            val sitCounts = mutableMapOf<Int, Int>()
            activeLineup.forEach { entry ->
                var sits = 0
                for (i in 1..totalInns) {
                    if (entry.getPosForInning(i) == "BENCH") sits++
                }
                sitCounts[entry.playerId] = sits
            }
            val zeros = sitCounts.filter { it.value == 0 }.keys
            val twos = sitCounts.filter { it.value >= 2 }.keys
            if (zeros.isNotEmpty() && twos.isNotEmpty()) {
                val zeroNames = zeros.mapNotNull { playerMap[it]?.name }.joinToString(", ")
                val twoNames = twos.mapNotNull { playerMap[it]?.name }.joinToString(", ")
                listOf("Fair Play Bench Violation: $twoNames sat 2+ times, but $zeroNames have never sat.")
            } else {
                emptyList()
            }
        }
    }

    // 3. Consecutive bench rule validation
    val consecutiveBenchViolations = remember(activeLineup, activeGame) {
        val maxLimit = activeGame?.maxConsecutiveBench ?: 1
        val totalInns = activeGame?.totalInnings ?: 6
        val violations = mutableListOf<String>()
        activeLineup.forEach { entry ->
            val positions = (1..totalInns).map { entry.getPosForInning(it) }
            var currentConsec = 0
            var maxConsec = 0
            positions.forEach { pos ->
                if (pos == "BENCH") {
                    currentConsec++
                    if (currentConsec > maxConsec) {
                        maxConsec = currentConsec
                    }
                } else {
                    currentConsec = 0
                }
            }
            if (maxConsec > maxLimit) {
                val playerName = playerMap[entry.playerId]?.name ?: "Player"
                violations.add("$playerName sat $maxConsec consecutive innings on bench (Max limit: $maxLimit).")
            }
        }
        violations
    }

    // 4. Missing positions check (all 9 field positions must be filled each inning)
    val unfilledPositionsWarnings = remember(activeLineup, activeGame) {
        val totalInns = activeGame?.totalInnings ?: 6
        val requiredPositions = listOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")
        val warnings = mutableListOf<String>()
        if (activeLineup.isNotEmpty()) {
            for (inning in 1..totalInns) {
                val assigned = activeLineup.map { entry ->
                    entry.getPosForInning(inning)
                }.toSet()
                val missing = requiredPositions.filter { !assigned.contains(it) }
                if (missing.isNotEmpty()) {
                    warnings.add("Inning $inning is missing: ${missing.joinToString(", ")}")
                }
            }
        }
        warnings
    }

    val allLineupWarnings = remember(doubleAssignments, equalBenchViolations, consecutiveBenchViolations, unfilledPositionsWarnings, dismissFairPlayWarnings, pitchSmartViolations, pitchCatchViolations) {
        val list = mutableListOf<String>()
        list.addAll(doubleAssignments)
        if (!dismissFairPlayWarnings) {
            list.addAll(equalBenchViolations)
            list.addAll(consecutiveBenchViolations)
        }
        list.addAll(unfilledPositionsWarnings)
        list.addAll(pitchSmartViolations)
        list.addAll(pitchCatchViolations)
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
    ) {
        // 1. Active Game selector banner scroll item
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Selected Matchup", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = activeGame?.opponent?.let { "VS $it" } ?: "Select game schedule...",
                            fontWeight = FontWeight.Bold,
                            color = BaseballWhite,
                            fontSize = 18.sp
                        )
                    }

                    Box {
                        Button(
                            onClick = { showSelectGameDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumDarkBg)
                        ) {
                            Text("Switch Game", color = OutfieldGreen)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "", tint = OutfieldGreen)
                        }
                        DropdownMenu(
                            expanded = showSelectGameDropdown,
                            onDismissRequest = { showSelectGameDropdown = false }
                        ) {
                            gamesList.forEach { game ->
                                DropdownMenuItem(
                                    text = { Text("vs ${game.opponent} (${game.gameDate})") },
                                    onClick = {
                                        onSelectGame(game.id)
                                        showSelectGameDropdown = false
                                    }
                                )
                            }
                            HorizontalDivider(color = StadiumGrayBorder)
                            DropdownMenuItem(
                                text = { Text("+ Schedule New Game", color = TurfLime, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showSelectGameDropdown = false
                                    onAddGameClick()
                                }
                            )
                        }
                    }
                }
            }
        }

        // 1b. Share, Print, and Export toolbar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Dugout Lineup Share & Print",
                        fontWeight = FontWeight.Bold,
                        color = BaseballWhite,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Print PDF card button
                        Button(
                            onClick = { onPrintPdf() },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Card", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // Export PDF button
                        Button(
                            onClick = { onExportPdf() },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // Spreadsheet share button
                        Button(
                            onClick = { onExportSpreadsheet() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)), // Excel Green
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = "CSV", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV Sheet", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 2. Optimizer trigger board scroll item
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lineup AI & Algorithmic Optimizer",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = coachMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3F4F9), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOptimize(false, lockedInnings) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
                                .testTag("instant_optimize_btn")
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SleekPrimary)
                            } else {
                                Icon(imageVector = Icons.Default.Bolt, contentDescription = "Optimize", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local Fast Optimization", color = SleekTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        /*
                        Button(
                            onClick = { onOptimize(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryDark),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("gemini_ai_optimize_btn")
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Icon(imageVector = Icons.Default.Psychology, contentDescription = "AI Optimize", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gemini AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        */
                    }
                }
            }
        }

        // 3. Live lineup rules validation feedback bar scroll item
        if (allLineupWarnings.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Rotation & Fair-Play Warnings:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            allLineupWarnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                            
                            val hasFairPlay = remember(equalBenchViolations, consecutiveBenchViolations) {
                                equalBenchViolations.isNotEmpty() || consecutiveBenchViolations.isNotEmpty()
                            }
                            if (hasFairPlay && !dismissFairPlayWarnings) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { dismissFairPlayWarnings = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Dismiss Fair-Play Warnings",
                                        color = Color(0xFF991B1B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Rostering Matrix header items
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Squad Defensive Inning Rotation Matrix",
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Tap on any Inning badge below to manually edit playing position.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                if (activeLineup.isNotEmpty()) {
                    Text(
                        text = "Lock Innings:",
                        color = BaseballWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "Checked innings will NOT be modified by the lineup optimizer.",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .padding(start = 44.dp), // offset for the grab handle and batting order icons
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val inningsList = (1..totalInnings).toList()
                        inningsList.forEach { inn ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(text = "I$inn", fontSize = 10.sp, color = TextSecondary)
                                androidx.compose.material3.Checkbox(
                                    checked = lockedInnings.getOrNull(inn - 1) ?: false,
                                    onCheckedChange = { isChecked ->
                                        val newLocks = lockedInnings.toMutableList()
                                        if (inn - 1 < newLocks.size) {
                                            newLocks[inn - 1] = isChecked
                                            lockedInnings = newLocks
                                        }
                                    },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = OutfieldGreen, uncheckedColor = TextSecondary),
                                    modifier = Modifier.scale(0.8f) // Make checkboxes slightly smaller
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Active dynamic lineups items
        if (activeLineup.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select or create a scheduled game first to design lineups.", color = TextSecondary)
                }
            }
        } else {
            itemsIndexed(activeLineup) { index, entry ->
                val player = playerMap[entry.playerId]
                if (player != null) {
                    InningMatrixCard(
                        index = index,
                        player = player,
                        entry = entry,
                        onUpdateEntry = onUpdateEntry,
                        totalInnings = totalInnings,
                        onReorder = onReorder
                    )
                }
            }
        }
    }
}

val SmartGrey = Color(0xFF475569)

@Composable
fun InningMatrixCard(
    index: Int,
    player: Player,
    entry: LineupEntry,
    onUpdateEntry: (LineupEntry) -> Unit,
    totalInnings: Int,
    onReorder: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 110.dp.toPx() } // estimate card row height plus padding
    var dragOffsetY by remember { mutableStateOf(0f) }

    Card(
        colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .zIndex(if (dragOffsetY != 0f) 5f else 0f)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Player Title row with Drag Handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // GRAB BAR for Reordering
                    Box(
                        modifier = Modifier
                            .pointerInput(index) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        
                                        if (dragOffsetY > rowHeightPx) {
                                            onReorder(index, index + 1)
                                            dragOffsetY -= rowHeightPx
                                        } else if (dragOffsetY < -rowHeightPx) {
                                            onReorder(index, index - 1)
                                            dragOffsetY += rowHeightPx
                                        }
                                    },
                                    onDragEnd = {
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                            .padding(end = 10.dp)
                            .size(36.dp)
                            .background(StadiumDarkBg, RoundedCornerShape(6.dp))
                            .border(1.dp, StadiumGrayBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Grab Handle to Drag Up or Down",
                            tint = OutfieldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(OutfieldGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (entry.battingOrder > 0) entry.battingOrder.toString() else "-",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${player.name} (#${player.jerseyNumber})", fontWeight = FontWeight.Bold, color = BaseballWhite)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The list of Innings positions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val innings = (1..totalInnings).map { "I$it" to entry.getPosForInning(it) }

                innings.forEachIndexed { idx, pair ->
                    var isMenuExpanded by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StadiumDarkBg, RoundedCornerShape(6.dp))
                                .border(1.dp, StadiumGrayBorder, RoundedCornerShape(6.dp))
                                .clickable { isMenuExpanded = true }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(text = pair.first, fontSize = 9.sp, color = TextSecondary)
                            Text(
                                text = pair.second,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (pair.second != "BENCH") OutfieldGreen else TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            POSITIONS.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(text = p) },
                                    onClick = {
                                        val updated = entry.setPosForInning(idx + 1, p)
                                        onUpdateEntry(updated)
                                        isMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ========================== LIVE DASHBOARD SCREEN ==========================
@Composable
fun LiveDashboardTab(
    viewModel: BaseballViewModel,
    activeGame: Game?,
    players: List<Player>,
    activeLineup: List<LineupEntry>
) {
    if (activeGame == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please select or create an upcoming game first.", color = TextSecondary)
        }
        return
    }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Match details & active state title
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE SCORE TRACKER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClayAmber
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (activeGame.status == "Live") OutfieldGreen else SmartGrey,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = activeGame.status.uppercase(),
                                fontSize = 10.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HOME (US)", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = activeGame.runsOurTeam.toString(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = OutfieldGreen
                            )
                        }
                        Text("VS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(activeGame.opponent.uppercase(), fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = activeGame.runsOpponent.toString(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = BaseballWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inning: ${activeGame.currentInning} • Outs: ${activeGame.currentOuts}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BaseballWhite
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeGame.status == "Upcoming") {
                                Button(
                                    onClick = { viewModel.startLiveGame(activeGame) },
                                    colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                                    modifier = Modifier.testTag("start_live_game")
                                ) {
                                    Text("Play Ball! ⚾", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else if (activeGame.status == "Live") {
                                Button(
                                    onClick = { viewModel.completeGame(activeGame) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClayAmber),
                                    modifier = Modifier.testTag("finish_live_game")
                                ) {
                                    Text("Finish Game", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // FIELDERS BASE POSITION PATHS VISUALIZER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Baseball Diamond Background canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    ) {
                        val cx = size.width / 2
                        val cy = size.height / 2 + 10.dp.toPx()
                        
                        // Draw fields path lines
                        val path = Path().apply {
                            moveTo(cx, cy - 80.dp.toPx()) // 2nd base
                            lineTo(cx + 80.dp.toPx(), cy) // 1st base
                            lineTo(cx, cy + 80.dp.toPx()) // Home plate
                            lineTo(cx - 80.dp.toPx(), cy) // 3rd base
                            close()
                        }
                        drawPath(
                            path = path,
                            color = StadiumGrayBorder,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Baserunner Bubbles (First, Second, Third)
                    val leadColor = ClayAmber
                    val inactiveColor = SmartGrey

                    // Second Base (Top)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-70).dp)
                            .size(28.dp)
                            .background(
                                if (activeGame.runnerOnSecond) leadColor else inactiveColor,
                                CircleShape
                            )
                            .clickable {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    activeGame.runnerOnFirst,
                                    !activeGame.runnerOnSecond,
                                    activeGame.runnerOnThird
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("2B", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }

                    // First Base (Right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 70.dp)
                            .size(28.dp)
                            .background(
                                if (activeGame.runnerOnFirst) leadColor else inactiveColor,
                                CircleShape
                            )
                            .clickable {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    !activeGame.runnerOnFirst,
                                    activeGame.runnerOnSecond,
                                    activeGame.runnerOnThird
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1B", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }

                    // Third Base (Left)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (-70).dp)
                            .size(28.dp)
                            .background(
                                if (activeGame.runnerOnThird) leadColor else inactiveColor,
                                CircleShape
                            )
                            .clickable {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    activeGame.runnerOnFirst,
                                    activeGame.runnerOnSecond,
                                    !activeGame.runnerOnThird
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("3B", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }

                    // Pitcher Mound (Center)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .background(OutfieldGreen.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("P", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    // Home Plate (Bottom)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 90.dp)
                            .size(32.dp)
                            .background(BaseballWhite, RoundedCornerShape(4.dp))
                            .border(1.dp, StadiumGrayBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("H", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    // Floating description indicator
                    Text(
                        text = "Real-time Field Grid (Tap base paths to load runners)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }

        // LIVE SCOREBOARD BUTTON ACTIONS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LIVE GAME PLAY SCOREKEEPER", style = MaterialTheme.typography.titleSmall, color = BaseballWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Log Run Our Team
                        Button(
                            onClick = {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam + 1,
                                    activeGame.runsOpponent,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    activeGame.runnerOnFirst,
                                    activeGame.runnerOnSecond,
                                    activeGame.runnerOnThird
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                            modifier = Modifier.weight(1f).testTag("plus_run_home")
                        ) {
                            Text("+1 RUN (US)", color = Color.Black, fontWeight = FontWeight.Black)
                        }

                        // Log Run Opponent
                        Button(
                            onClick = {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent + 1,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    activeGame.runnerOnFirst,
                                    activeGame.runnerOnSecond,
                                    activeGame.runnerOnThird
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumDarkBg),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, SmartGrey, RoundedCornerShape(20.dp))
                                .testTag("plus_run_opp")
                        ) {
                            Text("+1 RUN (THEM)", color = BaseballWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Adjust Outs
                        Button(
                            onClick = {
                                val nextOuts = (activeGame.currentOuts + 1) % 3
                                val (finalOuts, nextInning) = if (activeGame.currentOuts == 2) {
                                    0 to (activeGame.currentInning + 1)
                                } else {
                                    nextOuts to activeGame.currentInning
                                }
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent,
                                    nextInning,
                                    finalOuts,
                                    activeGame.runnerOnFirst,
                                    activeGame.runnerOnSecond,
                                    activeGame.runnerOnThird
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumDarkBg),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, StadiumGrayBorder, RoundedCornerShape(20.dp))
                        ) {
                            Text("OUT +1", color = BaseballWhite)
                        }

                        // Clear Baserunners
                        Button(
                            onClick = {
                                viewModel.updateLiveGameScore(
                                    activeGame,
                                    activeGame.runsOurTeam,
                                    activeGame.runsOpponent,
                                    activeGame.currentInning,
                                    activeGame.currentOuts,
                                    false,
                                    false,
                                    false
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumDarkBg),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, StadiumGrayBorder, RoundedCornerShape(20.dp))
                        ) {
                            Text("CLEAR BASES", color = BaseballWhite)
                        }
                    }
                }
            }
        }

        // DEFENSIVE ROLES IN THE ACTIVE INNING
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SleekDarkBg),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .border(1.dp, SleekDarkBorder, RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEFENSIVE ROTATION (INNING ${activeGame.currentInning})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFFC4C7CF)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF60A5FA), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF4B5563), CircleShape))
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF4B5563), CircleShape))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val playerMap = players.associateBy { it.id }
                    val inningPositions = activeLineup.map { entry ->
                        val pos = entry.getPosForInning(activeGame.currentInning)
                        val player = playerMap[entry.playerId]
                        pos to (player?.name ?: "Unknown Player")
                    }.filter { it.first != "BENCH" }.sortedBy { it.first }

                    if (inningPositions.isEmpty()) {
                        Text(
                            text = "No rotation assigned for this inning yet. Generate lineup first.",
                            color = Color(0xFFC4C7CF),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        // Chunk positions into grid rows of 3 columns
                        val chunks = inningPositions.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunks.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (pos, name) ->
                                        val posFullName = when(pos) {
                                            "P" -> "Pitcher"
                                            "C" -> "Catcher"
                                            "1B" -> "1st Base"
                                            "2B" -> "2nd Base"
                                            "3B" -> "3rd Base"
                                            "SS" -> "Shortstop"
                                            "LF" -> "Left Field"
                                            "CF" -> "Center Field"
                                            "RF" -> "Right Field"
                                            else -> pos
                                        }
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = SleekDarkCardBg),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, SleekDarkBorder, RoundedCornerShape(12.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = posFullName,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFA1A1AA)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val displayName = if (name.contains(" ")) name.substringAfter(" ") else name
                                                Text(
                                                    text = displayName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (pos == "SS" || pos == "P") Color(0xFF93C5FD) else Color.White
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size < 3) {
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { /* Navigates or refreshes */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    ) {
                        Text("VIEW FIELD MAP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ========================== TEAM FEED & SYNC TAB ==========================
@Composable
fun TeamFeedTab(
    announcements: List<Announcement>,
    syncOn: Boolean,
    onSyncToggle: (Boolean) -> Unit,
    onPostClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Parents sync card
        Card(
            colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CRITICAL PARENT/COACH SYNC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OutfieldGreen
                        )
                        Text(
                            text = if (syncOn) "● REAL-TIME SYNC CONNECTED" else "SYNC PAUSED",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (syncOn) OutfieldGreen else TextSecondary
                        )
                    }
                    Switch(
                        checked = syncOn,
                        onCheckedChange = onSyncToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = OutfieldGreen)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StadiumDarkBg, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Parent Observers in App:", fontSize = 11.sp, color = TextSecondary)
                        Text("14 Connected Parents / Assistant Coaches", fontWeight = FontWeight.Bold, color = BaseballWhite, fontSize = 13.sp)
                    }
                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "", tint = OutfieldGreen)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Divider(color = StadiumGrayBorder)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Broadcast Web Link (Sync Channel):",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Text(
                    text = "https://ais-pre-xfsywcyujjjdwjd4akoooi-480680464476.us-east1.run.app",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SkyOutfield,
                    modifier = Modifier.clickable { /* action */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Official Announcements
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Official Announcements Feed",
                    fontWeight = FontWeight.Bold,
                    color = BaseballWhite,
                    fontSize = 16.sp
                )
                Text(
                    text = "Schedule changes & push updates",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = onPostClick,
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Icon(imageVector = Icons.Default.Campaign, contentDescription = "")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Broadcast", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (announcements.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No team notices posted yet.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(announcements) { announcement ->
                    AnnouncementCard(announcement = announcement)
                }
            }
        }
    }
}

@Composable
fun AnnouncementCard(announcement: Announcement) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = announcement.title,
                fontWeight = FontWeight.Bold,
                color = BaseballWhite,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = announcement.content,
                fontSize = 12.sp,
                color = BaseballWhite.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Posted " + java.text.DateFormat.getDateTimeInstance()
                        .format(java.util.Date(announcement.timestamp)),
                    fontSize = 9.sp,
                    color = TextSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SendToMobile,
                        contentDescription = "Notification sent",
                        tint = OutfieldGreen,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Push Alert Sent", fontSize = 9.sp, color = OutfieldGreen)
                }
            }
        }
    }
}


// ========================== ADDITIONAL SUB-MODAL DIALOGS ==========================
@Composable
fun AddGameDialog(
    initialGame: Game? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Boolean, Int, Boolean, Int, Int) -> Unit
) {
    var opponent by remember { mutableStateOf(initialGame?.opponent ?: "") }
    var date by remember { mutableStateOf(initialGame?.gameDate ?: "June 10, 2026") }
    var time by remember { mutableStateOf(initialGame?.gameTime ?: "6:00 PM") }
    var minInnings by remember { mutableStateOf((initialGame?.minInningsDefense ?: 4).toString()) }
    var maxPitcher by remember { mutableStateOf((initialGame?.maxInningsPitcher ?: 7).toString()) }
    var continuous by remember { mutableStateOf(initialGame?.continuousBatting ?: true) }
    var runLimit by remember { mutableStateOf((initialGame?.runLimitPerInning ?: 5).toString()) }
    var equalBenchRule by remember { mutableStateOf(initialGame?.equalBenchRule ?: true) }
    var maxConsecutiveBench by remember { mutableStateOf((initialGame?.maxConsecutiveBench ?: 1).toString()) }
    var totalInningsInput by remember { mutableStateOf((initialGame?.totalInnings ?: 7).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGame == null) "Schedule New Game" else "Edit Game") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponent Team Name (Required)") },
                    isError = opponent.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Game Date") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Game Time") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minInnings,
                    onValueChange = { minInnings = it },
                    label = { Text("Min Innings per Player (Defense)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxPitcher,
                    onValueChange = { maxPitcher = it },
                    label = { Text("Max Pitcher Innings Allowed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Continuous Batting Order")
                    Switch(checked = continuous, onCheckedChange = { continuous = it })
                }
                OutlinedTextField(
                    value = runLimit,
                    onValueChange = { runLimit = it },
                    label = { Text("Max Runs limit per inning") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = totalInningsInput,
                    onValueChange = { totalInningsInput = it },
                    label = { Text("Game Length (Innings: 1-10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Advanced Lineup Rules",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Equal Bench Rotation (Fair Play)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("No player sits twice before everyone sits once", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(checked = equalBenchRule, onCheckedChange = { equalBenchRule = it })
                }

                OutlinedTextField(
                    value = maxConsecutiveBench,
                    onValueChange = { maxConsecutiveBench = it },
                    label = { Text("Max Consecutive Bench Innings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (opponent.isNotEmpty()) {
                        onSave(
                            opponent,
                            date,
                            time,
                            minInnings.toIntOrNull() ?: 4,
                            maxPitcher.toIntOrNull() ?: 7,
                            continuous,
                            runLimit.toIntOrNull() ?: 5,
                            equalBenchRule,
                            maxConsecutiveBench.toIntOrNull() ?: 1,
                            (totalInningsInput.toIntOrNull() ?: 7).coerceIn(1, 10)
                        )
                    }
                },
                enabled = opponent.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Text(if (initialGame == null) "Confirm Game" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Remember scroll state helper

@Composable
fun PostFeedDialog(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast Team Alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notice Headline") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Announcement Message") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Sending this will instantly broadcast updates to synchronized parent apps and trigger system push alert sounds.",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty() && content.isNotEmpty()) onPost(title, content) },
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Text("Confirm & Broadcast")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = StadiumSlateSurface
    )
}

// ========================== SCHEDULE TAB ==========================
@Composable
fun ScheduleTab(
    games: List<Game>,
    activeGameId: Int?,
    teamName: String,
    onSelectGame: (Int) -> Unit,
    onDeleteGame: (Int) -> Unit,
    onAddGameClick: () -> Unit,
    onExportCalendar: () -> Unit,
    onEditGameClick: (Game) -> Unit
) {
    val context = LocalContext.current
    var gameToDeleteId by remember { mutableStateOf<Int?>(null) }
    var gameToDeleteOpponent by remember { mutableStateOf("") }

    if (gameToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { gameToDeleteId = null },
            title = {
                Text(
                    text = "Delete Game Matchup?",
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove the scheduled game against $gameToDeleteOpponent? This will also purge its active lineups and inning records.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        gameToDeleteId?.let { id -> onDeleteGame(id) }
                        gameToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDeleteId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = StadiumSlateSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                Text(
                    text = "Game Schedule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BaseballWhite
                )
                Text(
                    text = "${games.size} matchups scheduled",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(x = (-16).dp, y = (-12).dp)
            ) {
                Button(
                    onClick = onExportCalendar,
                    colors = ButtonDefaults.buttonColors(containerColor = ClayAmber),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Export Schedule", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export All", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Button(
                    onClick = onAddGameClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Schedule Game", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("New Game", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (games.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Empty Schedule",
                        modifier = Modifier.size(64.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No scheduled games yet.", color = BaseballWhite, fontWeight = FontWeight.Bold)
                    Text("Tap 'New Game' above to add a match.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games) { game ->
                    val isActive = game.id == activeGameId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) StadiumSlateSurface.copy(alpha = 1.0f) else StadiumSlateSurface.copy(alpha = 0.5f)
                        ),
                        border = if (isActive) BorderStroke(1.5.dp, OutfieldGreen) else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date Bubble
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isActive) OutfieldGreen else StadiumGrayBorder, RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.SportsBaseball,
                                        contentDescription = "Match",
                                        tint = if (isActive) OutfieldGreen else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = game.gameDate.replace("June ", "6/").replace("July ", "7/").replace(", 2026", ""),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BaseballWhite,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$teamName vs ${game.opponent}",
                                    fontWeight = FontWeight.Bold,
                                    color = BaseballWhite,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${game.gameDate} @ ${game.gameTime}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Innings Badge
                                    Box(
                                        modifier = Modifier
                                            .background(StadiumDarkBg, RoundedCornerShape(4.dp))
                                            .border(0.5.dp, StadiumGrayBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${game.totalInnings} Innings", fontSize = 9.sp, color = TurfLime, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    // Continuous Batting Badge
                                    Box(
                                        modifier = Modifier
                                            .background(StadiumDarkBg, RoundedCornerShape(4.dp))
                                            .border(0.5.dp, StadiumGrayBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(if (game.continuousBatting) "Continuous" else "Standard", fontSize = 9.sp, color = ClayAmber, fontWeight = FontWeight.Bold)
                                    }

                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when(game.status) {
                                                    "Live" -> Color(0xFFB91C1C)
                                                    "Completed" -> Color(0xFF15803D)
                                                    else -> Color(0xFF475569)
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(game.status.uppercase(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                // Google Calendar deep link button
                                TextButton(
                                    onClick = {
                                        val url = com.example.utils.CalendarExporter.generateGoogleCalendarUrl(context, game)
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp).padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Add Single to Google Calendar",
                                        tint = TurfLime,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add to Google Calendar", fontSize = 11.sp, color = TurfLime, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Selection & Delete Controls
                            Column(horizontalAlignment = Alignment.End) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .background(OutfieldGreen.copy(alpha = 0.15f), CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .border(1.dp, OutfieldGreen, CircleShape)
                                    ) {
                                        Text("ACTIVE", color = OutfieldGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                } else {
                                    TextButton(
                                        onClick = { onSelectGame(game.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = OutfieldGreen)
                                    ) {
                                        Text("ACTIVATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEditGameClick(game) },
                                        modifier = Modifier.size(32.dp).padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Game",
                                            tint = ClayAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            gameToDeleteId = game.id
                                            gameToDeleteOpponent = game.opponent
                                        },
                                        modifier = Modifier.size(32.dp).padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Game Matchup",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RosterImportDialog(
    onDismiss: () -> Unit,
    onImport: (List<Player>, Boolean) -> Unit
) {
    var csvInputText by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val text = inputStream.bufferedReader().readText()
                    if (text.isNotBlank()) {
                        csvInputText = text
                        Toast.makeText(context, "CSV file loaded!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val detectedPlayers = remember(csvInputText) {
        if (csvInputText.isBlank()) emptyList()
        else {
            try {
                RosterCsvHelper.parseFromCsv(csvInputText)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = OutfieldGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Import Player Roster (CSV)",
                    style = MaterialTheme.typography.titleMedium,
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Import players by selecting a .csv file or pasting CSV text. Column headers: Name, Jersey, PreferredPosition, SecondaryPosition1, SecondaryPosition2, Note...",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StadiumGrayBorder.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Import Mode:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TurfLime
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { replaceExisting = false }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !replaceExisting,
                                onClick = { replaceExisting = false },
                                colors = RadioButtonDefaults.colors(selectedColor = OutfieldGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Append to current roster", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BaseballWhite)
                                Text("Keep existing players and add the new ones", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { replaceExisting = true }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = replaceExisting,
                                onClick = { replaceExisting = true },
                                colors = RadioButtonDefaults.colors(selectedColor = Color.Red)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Replace roster completely", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BaseballWhite)
                                Text("Deletes all existing players of the squad", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select CSV File", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Text(
                    text = "Or Paste CSV text content manually:",
                    style = MaterialTheme.typography.bodySmall,
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                OutlinedTextField(
                    value = csvInputText,
                    onValueChange = { csvInputText = it },
                    placeholder = { Text("Name,Jersey,PreferredPosition,...\nJohn,42,SS,BENCH,BENCH\nJane,3,CF,RF,LF", color = TextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BaseballWhite,
                        unfocusedTextColor = BaseballWhite,
                        focusedLabelColor = TurfLime,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = TurfLime,
                        unfocusedBorderColor = StadiumGrayBorder
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                )

                if (detectedPlayers.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OutfieldGreen.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, OutfieldGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Parsed ${detectedPlayers.size} players! Ready to import.",
                            color = TurfLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (detectedPlayers.isNotEmpty()) {
                        onImport(detectedPlayers, replaceExisting)
                        onDismiss()
                    }
                },
                enabled = detectedPlayers.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TurfLime)
            ) {
                Text("Import Players", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = StadiumSlateSurface
    )
}

@Composable
fun RosterExportDialog(
    players: List<Player>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val csvText = remember(players) {
        RosterCsvHelper.exportToCsv(players)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = TurfLime,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Player Roster (CSV)",
                    style = MaterialTheme.typography.titleMedium,
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Share the roster sheet with other coaches or copy it directly into other devices.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Share Button
                Button(
                    onClick = { RosterCsvHelper.shareRosterCsv(context, players) },
                    colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share CSV File ...", fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Copy Button
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(csvText))
                        Toast.makeText(context, "Copied roster CSV to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TurfLime),
                    border = BorderStroke(1.dp, TurfLime)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy CSV Text", fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "CSV Preview Content:",
                    style = MaterialTheme.typography.bodySmall,
                    color = BaseballWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Read-only text box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        .border(1.dp, StadiumGrayBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = csvText,
                        color = BaseballWhite.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TurfLime)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StadiumSlateSurface
    )
}
