package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

data class LeaderboardEntry(
    val score: Int,
    val timestamp: Long
)

class LeaderboardManager(context: Context) {
    private val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)

    fun updateOnlineLeaderboard(
        userId: String,
        displayName: String,
        newScore: Int,
        killsEarned: Int,
        currentCoins: Int
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("leaderboard").document(userId)
            val localTotalKills = prefs.getInt("total_kills_stat", 0).toLong()
            docRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val currentHighest = document.getLong("highestScore") ?: 0L
                    val currentKills = document.getLong("totalKills") ?: 0L
                    val finalHighest = maxOf(currentHighest, newScore.toLong())
                    val finalKills = maxOf(localTotalKills, currentKills)

                    val updates = hashMapOf<String, Any>(
                        "displayName" to displayName,
                        "highestScore" to finalHighest,
                        "totalKills" to finalKills,
                        "coins" to currentCoins,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updates)
                        .addOnSuccessListener {
                            Log.d("LeaderboardManager", "Online leaderboard updated successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("LeaderboardManager", "Failed to update online leaderboard", e)
                        }
                } else {
                    val data = hashMapOf<String, Any>(
                        "displayName" to displayName,
                        "highestScore" to newScore,
                        "totalKills" to localTotalKills,
                        "coins" to currentCoins,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(data)
                        .addOnSuccessListener {
                            Log.d("LeaderboardManager", "Online leaderboard created successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("LeaderboardManager", "Failed to create online leaderboard", e)
                        }
                }
            }.addOnFailureListener { e ->
                Log.e("LeaderboardManager", "Error reading online leaderboard document", e)
                val data = hashMapOf<String, Any>(
                    "displayName" to displayName,
                    "highestScore" to newScore,
                    "totalKills" to localTotalKills,
                    "coins" to currentCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("LeaderboardManager", "Online leaderboard set fallback succeeded")
                    }
                    .addOnFailureListener { ex ->
                        Log.e("LeaderboardManager", "Online leaderboard set fallback failed", ex)
                    }
            }
        } catch (e: Exception) {
            Log.e("LeaderboardManager", "Firestore error in updateOnlineLeaderboard", e)
        }
    }

    fun getTopScores(): List<LeaderboardEntry> {
        val raw = prefs.getString("top_scores_raw", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|")
            .mapNotNull { entryStr ->
                val parts = entryStr.split(",")
                if (parts.size == 2) {
                    val score = parts[0].toIntOrNull()
                    val timestamp = parts[1].toLongOrNull()
                    if (score != null && timestamp != null) {
                        LeaderboardEntry(score, timestamp)
                    } else null
                } else null
            }
            .distinctBy { Pair(it.score, it.timestamp) }
            .sortedByDescending { it.score }
    }

    fun addScore(score: Int, timestamp: Long = System.currentTimeMillis()): Boolean {
        if (score <= 0) return false
        val previousHigh = getHighScore()
        val currentScores = getTopScores().toMutableList()
        
        // Prevent duplicate entries with the exact same score and timestamp
        val isDuplicate = currentScores.any { it.score == score && it.timestamp == timestamp }
        if (isDuplicate) {
            return false
        }
        
        currentScores.add(LeaderboardEntry(score, timestamp))
        
        // Keep only the Top 10 highest unique game entries
        val top10 = currentScores
            .distinctBy { Pair(it.score, it.timestamp) }
            .sortedByDescending { it.score }
            .take(10)
        
        val serialized = top10.joinToString("|") { "${it.score},${it.timestamp}" }
        prefs.edit().putString("top_scores_raw", serialized).apply()
        
        return score > previousHigh
    }

    fun getHighScore(): Int {
        return getTopScores().firstOrNull()?.score ?: 0
    }

    fun getCachedRank(): String {
        return prefs.getString("cached_global_rank", "--") ?: "--"
    }

    fun fetchAndCacheGlobalRank(userId: String?, onComplete: (String) -> Unit) {
        if (userId.isNullOrEmpty()) {
            onComplete("--")
            return
        }
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("leaderboard")
                .get()
                .addOnSuccessListener { result ->
                    val list = mutableListOf<Triple<String, Long, Long>>() // (userId, highestLevel, totalKills)
                    val userCoinsMap = mutableMapOf<String, Long>()
                    
                    for (document in result) {
                        val uid = document.id
                        val level = document.getLong("highestLevel") ?: 1L
                        val kills = document.getLong("totalKills") ?: 0L
                        val coins = document.getLong("coins") ?: 0L
                        list.add(Triple(uid, level, kills))
                        userCoinsMap[uid] = coins
                    }
                    
                    // Sort locally based on requirements:
                    // 1. Highest Level (descending)
                    // 2. Total Kills (descending)
                    // 3. Highest Coins (descending) - as tie-breaker
                    val sortedList = list.sortedWith(
                        compareByDescending<Triple<String, Long, Long>> { it.second } // level
                            .thenByDescending { it.third } // kills
                            .thenByDescending { userCoinsMap[it.first] ?: 0L } // coins
                    )
                    
                    val rankIndex = sortedList.indexOfFirst { it.first == userId }
                    val rankStr = if (rankIndex != -1) {
                        "#${rankIndex + 1}"
                    } else {
                        "--"
                    }
                    
                    prefs.edit().putString("cached_global_rank", rankStr).apply()
                    onComplete(rankStr)
                }
                .addOnFailureListener { e ->
                    Log.e("LeaderboardManager", "Error calculating global rank", e)
                    onComplete(getCachedRank())
                }
        } catch (e: Exception) {
            Log.e("LeaderboardManager", "Exception calculating global rank", e)
            onComplete(getCachedRank())
        }
    }
}
