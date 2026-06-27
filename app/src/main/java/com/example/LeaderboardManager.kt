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
            .sortedByDescending { it.score }
    }

    fun addScore(score: Int): Boolean {
        val previousHigh = getHighScore()
        val currentScores = getTopScores().toMutableList()
        currentScores.add(LeaderboardEntry(score, System.currentTimeMillis()))
        
        val top10 = currentScores
            .sortedByDescending { it.score }
            .take(10)
        
        val serialized = top10.joinToString("|") { "${it.score},${it.timestamp}" }
        prefs.edit().putString("top_scores_raw", serialized).apply()
        
        return score > previousHigh && score > 0
    }

    fun getHighScore(): Int {
        return getTopScores().firstOrNull()?.score ?: 0
    }
}
