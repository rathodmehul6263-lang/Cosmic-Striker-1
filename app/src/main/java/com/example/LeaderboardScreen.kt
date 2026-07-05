package com.example

import android.util.Log
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

data class OnlineLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val highestLevel: Long,
    val totalKills: Long,
    val coins: Long,
    val profilePictureBase64: String?,
    val isCurrentUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUserId: String?,
    onClose: () -> Unit,
    onRankCalculated: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var leaderboardEntries by remember { mutableStateOf<List<OnlineLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch from Firestore and sort locally in memory to support tie-breakers perfectly without custom indexes
    fun fetchLeaderboard() {
        isLoading = true
        errorMessage = null
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("leaderboard")
                .get()
                .addOnSuccessListener { result ->
                    val list = mutableListOf<OnlineLeaderboardEntry>()
                    for (document in result) {
                        val uid = document.id
                        val name = document.getString("displayName") ?: "Unknown Pilot"
                        val level = document.getLong("highestLevel") ?: 1L
                        val kills = document.getLong("totalKills") ?: 0L
                        val coinsValue = document.getLong("coins") ?: 0L
                        val base64Pic = document.getString("profilePictureBase64")
                        val isCurrent = (currentUserId != null && uid == currentUserId)
                        
                        list.add(
                            OnlineLeaderboardEntry(
                                rank = 0, // Calculated after sorting
                                userId = uid,
                                displayName = name,
                                highestLevel = level,
                                totalKills = kills,
                                coins = coinsValue,
                                profilePictureBase64 = base64Pic,
                                isCurrentUser = isCurrent
                            )
                        )
                    }
                    
                    // Sort locally based on requirements:
                    // 1. Highest Level (descending)
                    // 2. Total Kills (descending)
                    // 3. Highest Coins (descending) - as tie-breaker
                    val comparator = compareByDescending<OnlineLeaderboardEntry> { it.highestLevel }
                        .thenByDescending { it.totalKills }
                        .thenByDescending { it.coins }

                    val sortedList = list.sortedWith(comparator)
                        .mapIndexed { index, entry ->
                            entry.copy(rank = index + 1)
                        }

                    leaderboardEntries = sortedList
                    isLoading = false

                    // Sync the calculated rank of the logged-in user to the shared state and preferences
                    if (currentUserId != null) {
                        val currentUserEntry = sortedList.firstOrNull { it.userId == currentUserId }
                        val rankStr = if (currentUserEntry != null) {
                            "#${currentUserEntry.rank}"
                        } else {
                            "--"
                        }
                        if (rankStr != "--") {
                            val prefs = context.getSharedPreferences("cosmic_striker_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("cached_global_rank", rankStr).apply()
                            onRankCalculated?.invoke(rankStr)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LeaderboardScreen", "Error reading online leaderboard", e)
                    errorMessage = e.message ?: "Failed to connect to the cosmic network."
                    isLoading = false
                }
        } catch (e: Exception) {
            Log.e("LeaderboardScreen", "Firestore exception in fetchLeaderboard", e)
            errorMessage = e.message ?: "An unexpected cosmic anomaly occurred."
            isLoading = false
        }
    }

    // Refresh automatically when opened
    LaunchedEffect(Unit) {
        fetchLeaderboard()
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
                val currentUserEntry = leaderboardEntries.firstOrNull { it.isCurrentUser }
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
                            OnlineLeaderboardRow(entry = entry)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
fun OnlineLeaderboardRow(entry: OnlineLeaderboardEntry) {
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

    val borderStroke = if (entry.isCurrentUser) {
        BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080))))
    } else {
        BorderStroke(1.dp, Color(0x11FFFFFF))
    }

    val bgGradient = if (entry.isCurrentUser) {
        Brush.horizontalGradient(listOf(Color(0x3300F0FF), Color(0x33FF0080)))
    } else {
        Brush.horizontalGradient(listOf(Color(0x0AFFFFFF), Color(0x02FFFFFF)))
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
                    text = entry.displayName,
                    color = if (entry.isCurrentUser) Color(0xFF00F0FF) else Color.White,
                    fontWeight = if (entry.isCurrentUser) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "UID: ${entry.userId}",
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
                text = "Level: ${entry.highestLevel}",
                color = Color(0xFF00F0FF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Kills: ${entry.totalKills}",
                color = Color(0xFFFF0080),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
