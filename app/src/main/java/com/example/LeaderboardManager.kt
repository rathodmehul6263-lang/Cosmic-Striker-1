package com.example

import android.content.Context

data class LeaderboardEntry(
    val score: Int,
    val timestamp: Long
)

class LeaderboardManager(context: Context) {
    private val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)

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
}
