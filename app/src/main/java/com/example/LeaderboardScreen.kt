package com.example

import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUserId: String?,
    onClose: () -> Unit,
    onRankCalculated: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var leaderboardEntries by remember { mutableStateOf<List<LeaderboardUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repository = remember { LeaderboardRepository(context) }

    var refreshTrigger by remember { mutableStateOf(0) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var isDiagnosticsRunning by remember { mutableStateOf(false) }
    var diagnosticsLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var diagnosticsSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun fetchLeaderboard() {
        refreshTrigger++
    }

    // Subscribe to real-time updates and handle updates reactively
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        errorMessage = null
        repository.getTop100LeaderboardRealtime()
            .collect { result ->
                if (result.isSuccess) {
                    val list = result.getOrThrow()
                    leaderboardEntries = list
                    isLoading = false

                    if (currentUserId != null) {
                        val currentUserEntry = list.firstOrNull { it.uid == currentUserId }
                        val rankStr = if (currentUserEntry != null) {
                            "#${currentUserEntry.rank}"
                        } else {
                            val userScore = LeaderboardManager(context).getHighScore()
                            val rankResult = repository.getPlayerGlobalRank(userScore)
                            if (rankResult.isSuccess) {
                                "#${rankResult.getOrThrow()}"
                            } else {
                                "--"
                            }
                        }
                        if (rankStr != "--") {
                            val prefs = context.getSharedPreferences("cosmic_striker_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("cached_global_rank", rankStr).apply()
                            onRankCalculated?.invoke(rankStr)
                        }
                    }
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to connect to the cosmic network."
                    isLoading = false
                }
            }
    }

    // Self-contained, robust interactive diagnostic verification tool
    fun runFirebaseDiagnostics() {
        if (isDiagnosticsRunning) return
        isDiagnosticsRunning = true
        diagnosticsLogs = listOf("⚡ INITIATING QUANTUM SECURE LINK DIAGNOSTICS...")
        diagnosticsSuccess = null

        scope.launch {
            try {
                // 1. Google Play Services / Firebase Options check
                val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
                val projectId = firebaseApp.options.projectId ?: "Unknown"
                diagnosticsLogs = diagnosticsLogs + "✓ Firebase Initialized. Project ID: $projectId"

                // 2. Auth State and Anonymous Sign-In check
                diagnosticsLogs = diagnosticsLogs + "⏳ Resolving Neural ID Link (FirebaseAuth)..."
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                var user = auth.currentUser
                if (user == null) {
                    diagnosticsLogs = diagnosticsLogs + "⚠ No active neural identity token. Authenticating anonymously..."
                    auth.signInAnonymously().await()
                    user = auth.currentUser
                }

                if (user == null) {
                    throw IllegalStateException("FirebaseAuth.currentUser is still null after anonymous authentication!")
                }
                val resolvedUid = user.uid
                diagnosticsLogs = diagnosticsLogs + "✓ Identity Secured. Secure UID: $resolvedUid"

                // 3. Write and immediately read back verification document (Using authenticated UID as document ID)
                diagnosticsLogs = diagnosticsLogs + "⏳ Transmitting verification document to cloud sector 'leaderboard/$resolvedUid'..."
                
                val scoreManager = LeaderboardManager(context)
                val highscore = scoreManager.getHighScore()
                val killsStat = context.getSharedPreferences("cosmic_striker_prefs", android.content.Context.MODE_PRIVATE)
                    .getInt("total_kills_stat", 0)
                val coinsStat = context.getSharedPreferences("cosmic_striker_prefs", android.content.Context.MODE_PRIVATE)
                    .getInt("total_coins_stat", 0)

                // Perform the write and immediate read-back
                val verifyResult = repository.verifyFirestoreWriteAndRead(
                    uid = resolvedUid,
                    playerName = AuthManager.currentUser?.name ?: "Pilot Verification-Test",
                    kills = killsStat,
                    level = 1,
                    score = highscore,
                    coins = coinsStat
                )

                if (verifyResult.isSuccess) {
                    val readData = verifyResult.getOrThrow()
                    diagnosticsLogs = diagnosticsLogs + "✓ Document written to 'leaderboard/$resolvedUid' successfully."
                    diagnosticsLogs = diagnosticsLogs + "⏳ Initiating instant verification read-back from cloud..."
                    diagnosticsLogs = diagnosticsLogs + "✓ Verification read-back complete! Verified data fields:"
                    diagnosticsLogs = diagnosticsLogs + "   - playerName: ${readData["playerName"]}"
                    diagnosticsLogs = diagnosticsLogs + "   - score: ${readData["score"]}"
                    diagnosticsLogs = diagnosticsLogs + "   - kills: ${readData["kills"]}"
                    diagnosticsLogs = diagnosticsLogs + "   - level: ${readData["level"]}"
                    diagnosticsLogs = diagnosticsLogs + "   - coins: ${readData["coins"]}"
                    diagnosticsLogs = diagnosticsLogs + "   - timestamp: ${readData["timestamp"]}"
                    diagnosticsLogs = diagnosticsLogs + "\n🎉 FIRESTORE CONNECTION SUCCESSFUL! Leaderboard collection automatically verified."
                    diagnosticsSuccess = true
                } else {
                    val ex = verifyResult.exceptionOrNull() ?: Exception("Unknown read/write exception")
                    throw ex
                }
            } catch (e: Exception) {
                Log.e("LeaderboardScreen", "Diagnostics failure: ", e)
                diagnosticsLogs = diagnosticsLogs + "❌ DIAGNOSTICS FAILURE!"
                diagnosticsLogs = diagnosticsLogs + "Exception: ${e.javaClass.simpleName}"
                diagnosticsLogs = diagnosticsLogs + "Message: ${e.message}"
                diagnosticsSuccess = false
            } finally {
                isDiagnosticsRunning = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03030C))
    ) {
        // Decorative space background
        StarfieldBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cyber Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Return to Menu",
                        tint = Color.White
                    )
                }

                Text(
                    text = "🏆 COLD FLIGHT LEADERS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F0FF),
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { fetchLeaderboard() },
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Scores",
                        tint = Color.White
                    )
                }
            }

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080), Color(0xFF00F0FF))))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-headline / Connection Status info
            val activeUser = AuthManager.currentUser
            if (activeUser != null) {
                val currentUserEntry = leaderboardEntries.firstOrNull { it.uid == currentUserId }
                val personalRankText = if (isLoading) {
                    "Loading..."
                } else if (currentUserEntry != null) {
                    "#${currentUserEntry.rank}"
                } else {
                    "--"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x2200F0FF)),
                    border = BorderStroke(1.dp, Color(0x4400F0FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Green, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connected as: ${activeUser.name}",
                                color = Color(0xFF8FA0DD),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "GLOBAL RANK: $personalRankText",
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Leaderboard Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x1A000000), shape = RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), shape = RoundedCornerShape(16.dp))
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00F0FF))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ESTABLISHING NEURAL LINK...",
                                color = Color(0xFF00F0FF),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🛰️ COMN TRNS FALLBACK",
                                color = Color(0xFFFF0080),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown quantum interference.",
                                color = Color(0xFF8FA0DD),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { fetchLeaderboard() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A0033)),
                                border = BorderStroke(1.dp, Color(0xFFFF0080)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "RETRY SYNC",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (leaderboardEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO HIGH SCORE DATA DETECTED IN DEEP SPACE.\nESTABLISH MISSION RECORDS TO BEGIN.",
                            color = Color(0xFF8FA0DD),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(leaderboardEntries) { entry ->
                            OnlineLeaderboardRow(entry = entry, currentUserId = currentUserId)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Firebase Diagnostics Panel
            if (com.example.BuildConfig.DEBUG) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x15000000)),
                    border = BorderStroke(1.dp, if (diagnosticsSuccess == true) Color.Green else if (diagnosticsSuccess == false) Color.Red else Color(0x33FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🧪 CONNECTION VERIFIER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diagnosticsSuccess == true) Color.Green else if (diagnosticsSuccess == false) Color.Red else Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = {
                                    showDiagnostics = !showDiagnostics
                                    if (showDiagnostics && diagnosticsLogs.isEmpty()) {
                                        runFirebaseDiagnostics()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (showDiagnostics) "HIDE" else "RUN VERIFY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (showDiagnostics) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .background(Color(0xFF010105), shape = RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, Color(0x11FFFFFF)), shape = RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(diagnosticsLogs) { logLine ->
                                        Text(
                                            text = logLine,
                                            color = if (logLine.startsWith("✓")) Color.Green 
                                                   else if (logLine.startsWith("❌") || logLine.startsWith("Exception:") || logLine.startsWith("Message:")) Color.Red 
                                                   else if (logLine.startsWith("⚠")) Color.Yellow 
                                                   else Color(0xFF8FA0DD),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!isDiagnosticsRunning) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { runFirebaseDiagnostics() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00F0FF)),
                                    border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.End).height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "RE-RUN TEST",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action Button
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(BorderStroke(1.dp, Color(0xFFFF0080)), shape = RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4A0033), Color(0xFF1E002F))), shape = RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "DISMISS CONSOLE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun OnlineLeaderboardRow(entry: LeaderboardUser, currentUserId: String?) {
    val isCurrentUser = !currentUserId.isNullOrEmpty() && entry.uid == currentUserId
    val rankColor = when (entry.rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color(0xFF8FA0DD) // Normal
    }

    val rankText = when (entry.rank) {
        1 -> "🏆 #1"
        2 -> "🥈 #2"
        3 -> "🥉 #3"
        else -> "   #${entry.rank}"
    }

    val borderStroke = when (entry.rank) {
        1 -> BorderStroke(2.dp, Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))) // Gold Glow
        2 -> BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFC0C0C0), Color(0xFFE2E8F0)))) // Silver Glow
        3 -> BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFCD7F32), Color(0xFFB45309)))) // Bronze Glow
        else -> {
            if (isCurrentUser) {
                BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080))))
            } else {
                BorderStroke(1.dp, Color(0x11FFFFFF))
            }
        }
    }

    val bgGradient = when (entry.rank) {
        1 -> Brush.horizontalGradient(listOf(Color(0x22FFD700), Color(0x0FFF8C00)))
        2 -> Brush.horizontalGradient(listOf(Color(0x22C0C0C0), Color(0x0FE2E8F0)))
        3 -> Brush.horizontalGradient(listOf(Color(0x22CD7F32), Color(0x0FB45309)))
        else -> {
            if (isCurrentUser) {
                Brush.horizontalGradient(listOf(Color(0x3300F0FF), Color(0x33FF0080)))
            } else {
                Brush.horizontalGradient(listOf(Color(0x0AFFFFFF), Color(0x02FFFFFF)))
            }
        }
    }

    // Decode base64 image if available
    val decodedBitmap = remember(entry.profilePictureBase64) {
        if (!entry.profilePictureBase64.isNullOrEmpty()) {
            try {
                val bytes = android.util.Base64.decode(entry.profilePictureBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgGradient, shape = RoundedCornerShape(8.dp))
            .border(borderStroke, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Rank Number
            Text(
                text = rankText,
                color = rankColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(60.dp)
            )

            // Profile Picture
            if (decodedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFF00F0FF), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1E293B), shape = CircleShape)
                        .border(1.dp, Color(0xFFFF0080), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Player Details (Name, UID)
            Column {
                Text(
                    text = entry.playerName,
                    color = if (isCurrentUser) Color(0xFF00F0FF) else Color.White,
                    fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Player ID: ${entry.playerId}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Stats (Level, Kills)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Level: ${entry.level}",
                color = Color(0xFF00F0FF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Kills: ${entry.kills}",
                color = Color(0xFFFF0080),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
