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
import androidx.compose.ui.draw.clip
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
import com.example.data.Announcement
import com.example.data.Game
import com.example.data.LineupEntry
import com.example.data.Player
import com.example.ui.theme.*

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
    
    val isOptimizing by viewModel.isOptimizing.collectAsStateWithLifecycle()
    val coachMessage by viewModel.coachMessage.collectAsStateWithLifecycle()
    val syncOn by viewModel.syncOn.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf("lineups") } // "roster", "lineups", "live", "feed"

    // Dialog state
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var showPostFeedDialog by remember { mutableStateOf(false) }

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
                            val displayTitle = game?.let { "Wildcats vs ${it.opponent}" } ?: "BaseLineup Studio"
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
                    IconButton(onClick = { showAddGameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = "New Game Matchup",
                            tint = SleekPrimary
                        )
                    }
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
                    Triple("live", "Live Game", Icons.Default.SportsBaseball),
                    Triple("feed", "Team Feed", Icons.Default.Forum)
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
                        onAddClick = { showAddPlayerDialog = true },
                        onUpdatePlayer = { viewModel.updatePlayer(it) },
                        onDeletePlayer = { viewModel.deletePlayer(it) },
                        onUpdateStats = { p, ab, h, w, r, rbi, so -> 
                            viewModel.updatePlayerStats(p, ab, h, w, r, rbi, so) 
                        }
                    )
                    "lineups" -> LineupsTab(
                        players = players,
                        activeGame = activeGame,
                        activeLineup = activeLineup,
                        isOptimizing = isOptimizing,
                        coachMessage = coachMessage,
                        gamesList = games,
                        onSelectGame = { viewModel.selectActiveGame(it) },
                        onOptimize = { ai -> viewModel.optimizeLineup(aiMode = ai) },
                        onUpdateEntry = { viewModel.updateSingleLineupEntry(it) }
                    )
                    "live" -> LiveDashboardTab(
                        viewModel = viewModel,
                        activeGame = activeGame,
                        players = players,
                        activeLineup = activeLineup
                    )
                    "feed" -> TeamFeedTab(
                        announcements = announcements,
                        syncOn = syncOn,
                        onSyncToggle = { viewModel.setSyncOn(it) },
                        onPostClick = { showPostFeedDialog = true }
                    )
                }

                // Dialog integrations
                if (showAddPlayerDialog) {
                    AddPlayerDialog(
                        onDismiss = { showAddPlayerDialog = false },
                        onSave = { name, jersey, pref, note ->
                            viewModel.addPlayer(name, jersey, pref, note)
                            showAddPlayerDialog = false
                        }
                    )
                }

                if (showAddGameDialog) {
                    AddGameDialog(
                        onDismiss = { showAddGameDialog = false },
                        onSave = { opp, date, time, minIn, maxPi, cont, limit ->
                            viewModel.addGame(opp, date, time, minIn, maxPi, cont, limit)
                            showAddGameDialog = false
                        }
                    )
                }

                if (showPostFeedDialog) {
                    PostFeedDialog(
                        onDismiss = { showPostFeedDialog = false },
                        onPost = { title, content ->
                            viewModel.postAnnouncement(title, content)
                            showPostFeedDialog = false
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
    onAddClick: () -> Unit,
    onUpdatePlayer: (Player) -> Unit,
    onDeletePlayer: (Int) -> Unit,
    onUpdateStats: (Player, Int, Int, Int, Int, Int, Int) -> Unit
) {
    var selectedPlayerForStats by remember { mutableStateOf<Player?>(null) }

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
            Column {
                Text(
                    text = "Player Roster",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BaseballWhite
                )
                Text(
                    text = "${players.size} active players on the squad",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
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
                onSave = { ab, h, w, r, rbi, so ->
                    onUpdateStats(player, ab, h, w, r, rbi, so)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Text(
                    text = player.name,
                    fontWeight = FontWeight.Bold,
                    color = BaseballWhite,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Pref: ${player.preferredPosition}", fontSize = 10.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            labelColor = TurfLime
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AVG: ${player.formattedBattingAverage()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ClayAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (player.note.isNotEmpty()) {
                    Text(
                        text = player.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Availability Switch (Parents/Coaches can set availability)
                IconButton(onClick = { onUpdate(player.copy(isAvailable = !player.isAvailable)) }) {
                    Icon(
                        imageVector = if (player.isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = "Toggle availability",
                        tint = if (player.isAvailable) OutfieldGreen else Color.Red
                    )
                }
                IconButton(onClick = onStatsClick) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Edit Stats",
                        tint = TurfLime
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Player",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}


// ========================== DIALOGS ==========================
@Composable
fun AddPlayerDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var jersey by remember { mutableStateOf("") }
    var prefPos by remember { mutableStateOf("LF") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Baseball Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Coaching/Availability Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty() && jersey.isNotEmpty()) onSave(name, jersey, prefPos, note) },
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
    onSave: (Int, Int, Int, Int, Int, Int) -> Unit
) {
    var ab by remember { mutableStateOf(player.atBats.toString()) }
    var hits by remember { mutableStateOf(player.hits.toString()) }
    var walks by remember { mutableStateOf(player.walks.toString()) }
    var runs by remember { mutableStateOf(player.runs.toString()) }
    var rbis by remember { mutableStateOf(player.rbis.toString()) }
    var so by remember { mutableStateOf(player.strikeouts.toString()) }

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
                        so.toIntOrNull() ?: 0
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
    isOptimizing: Boolean,
    coachMessage: String,
    gamesList: List<Game>,
    onSelectGame: (Int) -> Unit,
    onOptimize: (Boolean) -> Unit,
    onUpdateEntry: (LineupEntry) -> Unit
) {
    var showSelectGameDropdown by remember { mutableStateOf(false) }
    
    // In-line validation checks:
    // 1. Double assignments (two players on same position in same inning)
    val doubleAssignments = remember(activeLineup) {
        val inn1 = mutableSetOf<String>()
        val inn2 = mutableSetOf<String>()
        val inn3 = mutableSetOf<String>()
        val inn4 = mutableSetOf<String>()
        val inn5 = mutableSetOf<String>()
        val inn6 = mutableSetOf<String>()
        val duplicates = mutableListOf<String>()

        activeLineup.forEach { entry ->
            if (entry.posInning1 != "BENCH" && !inn1.add(entry.posInning1)) duplicates.add("Inning 1: Duplicate ${entry.posInning1}")
            if (entry.posInning2 != "BENCH" && !inn2.add(entry.posInning2)) duplicates.add("Inning 2: Duplicate ${entry.posInning2}")
            if (entry.posInning3 != "BENCH" && !inn3.add(entry.posInning3)) duplicates.add("Inning 3: Duplicate ${entry.posInning3}")
            if (entry.posInning4 != "BENCH" && !inn4.add(entry.posInning4)) duplicates.add("Inning 4: Duplicate ${entry.posInning4}")
            if (entry.posInning5 != "BENCH" && !inn5.add(entry.posInning5)) duplicates.add("Inning 5: Duplicate ${entry.posInning5}")
            if (entry.posInning6 != "BENCH" && !inn6.add(entry.posInning6)) duplicates.add("Inning 6: Duplicate ${entry.posInning6}")
        }
        duplicates.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Active Game selector banner
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
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Optimizer trigger board
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
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
                        onClick = { onOptimize(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
                            .testTag("instant_optimize_btn")
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SleekPrimary)
                        } else {
                            Icon(imageVector = Icons.Default.Bolt, contentDescription = "Optimize", tint = SleekPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Local Fast", color = SleekTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

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
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live double-position validation feedback bar
        if (doubleAssignments.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, Color.Red, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Rotation Warning (Duplicate Assignments):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        doubleAssignments.forEach { warning ->
                            Text(text = warning, fontSize = 11.sp, color = BaseballWhite)
                        }
                    }
                }
            }
        }

        // Rostering Matrix view
        Text(
            text = "Squad Defensive Inning Rotation Matrix",
            color = BaseballWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(text = "Tap on any Inning badge below to manually edit playing position.", color = TextSecondary, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(8.dp))

        if (activeLineup.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select or create a scheduled game first to design lineups.", color = TextSecondary)
            }
        } else {
            val playerMap = players.associateBy { it.id }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeLineup) { entry ->
                    val player = playerMap[entry.playerId]
                    if (player != null) {
                        InningMatrixCard(
                            player = player,
                            entry = entry,
                            onUpdateEntry = onUpdateEntry
                        )
                    }
                }
            }
        }
    }
}

val SmartGrey = Color(0xFF475569)

@Composable
fun InningMatrixCard(
    player: Player,
    entry: LineupEntry,
    onUpdateEntry: (LineupEntry) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StadiumSlateSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Player Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                // Adjust Batting Order
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (entry.battingOrder > 1) {
                                onUpdateEntry(entry.copy(battingOrder = entry.battingOrder - 1))
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = {
                            onUpdateEntry(entry.copy(battingOrder = entry.battingOrder + 1))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "", tint = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // The list of Innings positions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val innings = listOf(
                    "I1" to entry.posInning1,
                    "I2" to entry.posInning2,
                    "I3" to entry.posInning3,
                    "I4" to entry.posInning4,
                    "I5" to entry.posInning5,
                    "I6" to entry.posInning6
                )

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
                                        val updated = when (idx) {
                                            0 -> entry.copy(posInning1 = p)
                                            1 -> entry.copy(posInning2 = p)
                                            2 -> entry.copy(posInning3 = p)
                                            3 -> entry.copy(posInning4 = p)
                                            4 -> entry.copy(posInning5 = p)
                                            else -> entry.copy(posInning6 = p)
                                        }
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
                        val pos = when (activeGame.currentInning) {
                            1 -> entry.posInning1
                            2 -> entry.posInning2
                            3 -> entry.posInning3
                            4 -> entry.posInning4
                            5 -> entry.posInning5
                            else -> entry.posInning6
                        }
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
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Boolean, Int) -> Unit
) {
    var opponent by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("June 10, 2026") }
    var time by remember { mutableStateOf("6:00 PM") }
    var minInnings by remember { mutableStateOf("2") }
    var maxPitcher by remember { mutableStateOf("2") }
    var continuous by remember { mutableStateOf(true) }
    var runLimit by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule New Game") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponent Team Name") },
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
                            minInnings.toIntOrNull() ?: 2,
                            maxPitcher.toIntOrNull() ?: 2,
                            continuous,
                            runLimit.toIntOrNull() ?: 5
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OutfieldGreen)
            ) {
                Text("Confirm Game")
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
        }
    )
}
