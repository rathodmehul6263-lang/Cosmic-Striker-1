package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * ============================================================================
 * FIRESTORE SECURITY RULES
 * ============================================================================
 * Place these rules in your firestore.rules file:
 *
 * rules_version = '2';
 * service cloud.firestore {
 *   match /databases/{database}/documents {
 *     match /leaderboard/{userId} {
 *       // Anyone can read the leaderboard
 *       allow read: if true;
 *
 *       // Only authenticated users can write/update their own document
 *       allow write: if request.auth != null && request.auth.uid == userId;
 *     }
 *   }
 * }
 * ============================================================================
 *
 * ============================================================================
 * FIRESTORE INDEXES DEFINITION
 * ============================================================================
 * Single-field indexes are created automatically by Firestore. For advanced
 * querying (like ties-broken or multi-criteria searches), define this compound
 * index in firestore.indexes.json:
 *
 * {
 *   "indexes": [
 *     {
 *       "collectionGroup": "leaderboard",
 *       "queryScope": "COLLECTION",
 *       "fields": [
 *         { "fieldPath": "kills", "order": "DESCENDING" },
 *         { "fieldPath": "updatedAt", "order": "DESCENDING" }
 *       ]
 *     }
 *   ]
 * }
 * ============================================================================
 */

data class LeaderboardUser(
    val rank: Int = 0,
    val uid: String = "",
    val playerName: String = "",
    val kills: Int = 0,
    val score: Int = 0,
    val level: Int = 1,
    val coins: Int = 0,
    val profilePictureBase64: String? = null,
    val updatedAt: Date? = null
)

class LeaderboardRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()

    init {
        // Enable offline support and local data caching (Offline support requirement)
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            Log.d("LeaderboardRepository", "Firestore settings already initialized or failed: ${e.message}")
        }
    }

    /**
     * Upload player score and stats to the global leaderboard.
     * Requirement: Update ONLY if the new score or kills are higher than previous values.
     */
    suspend fun uploadScoreAndDetails(
        uid: String,
        playerName: String,
        newKills: Int,
        newScore: Int,
        newLevel: Int,
        newCoins: Int
    ): Result<Unit> = runCatching {
        if (uid.isEmpty()) throw IllegalArgumentException("User ID cannot be empty")

        val docRef = db.collection("leaderboard").document(uid)

        // Try to read document first (with default Firestore fallback behavior: checks local cache if offline)
        val snapshot = try {
            docRef.get().await()
        } catch (e: Exception) {
            Log.e("LeaderboardRepository", "Read prior document error or offline: ${e.message}")
            null
        }

        var shouldUpdate = true
        if (snapshot != null && snapshot.exists()) {
            val existingKills = snapshot.getLong("kills")?.toInt() ?: snapshot.getLong("totalKills")?.toInt() ?: 0
            val existingScore = snapshot.getLong("score")?.toInt() ?: snapshot.getLong("highestScore")?.toInt() ?: 0

            // Conditional update requirement check
            if (newScore <= existingScore && newKills <= existingKills) {
                shouldUpdate = false
                Log.d("LeaderboardRepository", "Stats not higher than existing record. Skipping Firestore upload.")
            }
        }

        if (shouldUpdate) {
            val data = hashMapOf<String, Any?>(
                "uid" to uid,
                "playerName" to playerName,
                "displayName" to playerName, // Backward compatibility with existing UI
                "kills" to newKills,
                "totalKills" to newKills, // Backward compatibility
                "score" to newScore,
                "highestScore" to newScore, // Backward compatibility
                "level" to newLevel,
                "highestLevel" to newLevel, // Backward compatibility
                "coins" to newCoins,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(data, SetOptions.merge()).await()
            Log.d("LeaderboardRepository", "Successfully synchronized leaderboard for $playerName")
        }
    }

    /**
     * Fetch Top 100 players from the collection efficiently.
     * This query executes with O(k) complexity where k is the limit of 100 documents, scaling to millions.
     */
    suspend fun getTop100Leaderboard(): Result<List<LeaderboardUser>> = runCatching {
        val querySnapshot = db.collection("leaderboard")
            .orderBy("kills", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()

        val list = mutableListOf<LeaderboardUser>()
        querySnapshot.documents.forEachIndexed { index, doc ->
            val uid = doc.getString("uid") ?: doc.id
            val name = doc.getString("playerName") ?: doc.getString("displayName") ?: "Unknown Pilot"
            val kills = doc.getLong("kills")?.toInt() ?: doc.getLong("totalKills")?.toInt() ?: 0
            val score = doc.getLong("score")?.toInt() ?: doc.getLong("highestScore")?.toInt() ?: 0
            val level = doc.getLong("level")?.toInt() ?: doc.getLong("highestLevel")?.toInt() ?: 1
            val coins = doc.getLong("coins")?.toInt() ?: 0
            val profilePic = doc.getString("profilePictureBase64")
            val updatedAtDate = doc.getTimestamp("updatedAt")?.toDate()

            list.add(
                LeaderboardUser(
                    rank = index + 1,
                    uid = uid,
                    playerName = name,
                    kills = kills,
                    score = score,
                    level = level,
                    coins = coins,
                    profilePictureBase64 = profilePic,
                    updatedAt = updatedAtDate
                )
            )
        }
        list
    }

    /**
     * Calculate global rank using an aggregation query without fetching documents.
     * Scales efficiently to millions of players.
     */
    suspend fun getPlayerGlobalRank(userKills: Int): Result<Int> = runCatching {
        if (userKills < 0) return Result.success(0)

        // Count of documents where kills > player's kills
        val countQuery = db.collection("leaderboard")
            .whereGreaterThan("kills", userKills)
            .count()

        val countSnapshot = countQuery.get(AggregateSource.SERVER).await()
        val higherCount = countSnapshot.count

        // Rank = number of players with strictly higher kills + 1
        (higherCount + 1).toInt()
    }
}
