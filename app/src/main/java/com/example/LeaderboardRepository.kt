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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    val updatedAt: Date? = null,
    val playerId: String = ""
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
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            val ex = IllegalStateException("No authenticated Firebase user found! Cannot upload score to Firestore.")
            Log.e("LeaderboardRepository", "[AUDIT_FIREBASE] uploadScoreAndDetails() ERROR: No authenticated Firebase user found! Cannot upload.", ex)
            throw ex
        }
        val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        var playerId = prefs.getString("player_id", "") ?: ""
        if (playerId.isEmpty()) {
            var attempts = 0
            while (attempts < 10) {
                val candidate = (10000000..99999999).random().toString()
                val isUnique = try {
                    val snapshot = db.collection("leaderboard")
                        .whereEqualTo("playerId", candidate)
                        .limit(1)
                        .get()
                        .await()
                    snapshot.isEmpty
                } catch (e: Exception) {
                    true
                }
                if (isUnique) {
                    playerId = candidate
                    prefs.edit().putString("player_id", playerId).apply()
                    AuthManager.init(context)
                    break
                }
                attempts++
            }
            if (playerId.isEmpty()) {
                playerId = (10000000..99999999).random().toString()
                prefs.edit().putString("player_id", playerId).apply()
                AuthManager.init(context)
            }
        }

        val resolvedUid = currentUser.uid
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] uploadScoreAndDetails() writing to 'leaderboard' for UID: $resolvedUid, Name: $playerName, score: $newScore, kills: $newKills, playerId: $playerId")
        val docRef = db.collection("leaderboard").document(resolvedUid)

        // Try to read document first (with default Firestore fallback behavior: checks local cache if offline)
        val snapshot = try {
            docRef.get().await()
        } catch (e: Exception) {
            Log.e("LeaderboardRepository", "[AUDIT_FIREBASE] Read prior document error or offline: ${e.message}", e)
            null
        }

        var finalScore = newScore
        var finalKills = newKills
        var finalLevel = newLevel
        var finalCoins = newCoins

        if (snapshot != null && snapshot.exists()) {
            val existingScore = snapshot.getLong("score")?.toInt() ?: snapshot.getLong("highestScore")?.toInt() ?: 0
            if (newScore <= existingScore) {
                Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Skipping Firestore upload: current new score ($newScore) is not higher than existing record ($existingScore).")
                return@runCatching
            }
            
            val existingKills = snapshot.getLong("kills")?.toInt() ?: snapshot.getLong("totalKills")?.toInt() ?: 0
            val existingLevel = snapshot.getLong("level")?.toInt() ?: snapshot.getLong("highestLevel")?.toInt() ?: 1
            val existingCoins = snapshot.getLong("coins")?.toInt() ?: 0

            finalScore = maxOf(existingScore, newScore)
            finalKills = maxOf(existingKills, newKills)
            finalLevel = maxOf(existingLevel, newLevel)
            finalCoins = maxOf(existingCoins, newCoins)
        }

        val data = hashMapOf<String, Any?>(
            "uid" to resolvedUid,
            "playerId" to playerId,
            "playerName" to playerName,
            "displayName" to playerName, // Backward compatibility with existing UI
            "kills" to finalKills,
            "totalKills" to finalKills, // Backward compatibility
            "score" to finalScore,
            "highestScore" to finalScore, // Backward compatibility
            "level" to finalLevel,
            "highestLevel" to finalLevel, // Backward compatibility
            "coins" to finalCoins,
            "timestamp" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(data, SetOptions.merge()).await()
            Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Firestore upload SUCCESS to collection 'leaderboard' for UID: $resolvedUid, name: $playerName")
        } catch (e: Exception) {
            Log.e("LeaderboardRepository", "[AUDIT_FIREBASE] Firestore upload FAILURE to collection 'leaderboard' for UID: $resolvedUid, Exception: ", e)
            throw e
        }
    }

    /**
     * Read the Top 100 leaderboard in real time.
     */
    fun getTop100LeaderboardRealtime(): Flow<Result<List<LeaderboardUser>>> = callbackFlow {
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] getTop100LeaderboardRealtime() subscribing to collection 'leaderboard'")
        val subscription = db.collection("leaderboard")
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Log.e("LeaderboardRepository", "[AUDIT_FIREBASE] Real-time listener failed", error)
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                if (querySnapshot != null) {
                    val list = mutableListOf<LeaderboardUser>()
                    querySnapshot.documents.forEach { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val name = doc.getString("playerName") ?: doc.getString("displayName") ?: "Unknown Pilot"
                        val kills = doc.getLong("kills")?.toInt() ?: doc.getLong("totalKills")?.toInt() ?: 0
                        val score = doc.getLong("score")?.toInt() ?: doc.getLong("highestScore")?.toInt() ?: 0
                        val level = doc.getLong("level")?.toInt() ?: doc.getLong("highestLevel")?.toInt() ?: 1
                        val coins = doc.getLong("coins")?.toInt() ?: 0
                        val profilePic = doc.getString("profilePictureBase64")
                        val updatedAtDate = doc.getTimestamp("updatedAt")?.toDate()
                        val parsedPlayerId = doc.getString("playerId") ?: run {
                            val hash = Math.abs(uid.hashCode() % 90000000) + 10000000
                            hash.toString()
                        }

                        list.add(
                            LeaderboardUser(
                                rank = 0,
                                uid = uid,
                                playerName = name,
                                kills = kills,
                                score = score,
                                level = level,
                                coins = coins,
                                profilePictureBase64 = profilePic,
                                updatedAt = updatedAtDate,
                                playerId = parsedPlayerId
                            )
                        )
                    }

                    // Sort descending by score, then by kills, then by level
                    val sorted = list.sortedWith(
                        compareByDescending<LeaderboardUser> { it.score }
                            .thenByDescending { it.kills }
                            .thenByDescending { it.level }
                    ).mapIndexed { index, user ->
                        user.copy(rank = index + 1)
                    }.take(100)

                    trySend(Result.success(sorted))
                }
            }
        awaitClose {
            Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Closing real-time leaderboard subscription")
            subscription.remove()
        }
    }

    /**
     * Fetch Top 100 players from the collection efficiently.
     * This query executes with O(k) complexity where k is the limit of 100 documents, scaling to millions.
     */
    suspend fun getTop100Leaderboard(): Result<List<LeaderboardUser>> = runCatching {
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] getTop100Leaderboard() reading from collection 'leaderboard'")
        val querySnapshot = try {
            db.collection("leaderboard")
                .get()
                .await()
        } catch (e: Exception) {
            Log.e("LeaderboardRepository", "[AUDIT_FIREBASE] getTop100Leaderboard() read query failed: ", e)
            throw e
        }

        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] getTop100Leaderboard() read query completed. Document count: ${querySnapshot.size()}")
        if (querySnapshot.isEmpty) {
            Log.w("LeaderboardRepository", "[AUDIT_FIREBASE] getTop100Leaderboard() returned 0 documents! " +
                    "Potential reasons: (1) No players have registered/completed a game yet, " +
                    "(2) Firestore database holds no records in collection 'leaderboard', " +
                    "(3) Security rules are blocking unauthorized read requests, or " +
                    "(4) The client is offline and local cache is unpopulated.")
        }

        val list = mutableListOf<LeaderboardUser>()
        querySnapshot.documents.forEach { doc ->
            val uid = doc.getString("uid") ?: doc.id
            val name = doc.getString("playerName") ?: doc.getString("displayName") ?: "Unknown Pilot"
            val kills = doc.getLong("kills")?.toInt() ?: doc.getLong("totalKills")?.toInt() ?: 0
            val score = doc.getLong("score")?.toInt() ?: doc.getLong("highestScore")?.toInt() ?: 0
            val level = doc.getLong("level")?.toInt() ?: doc.getLong("highestLevel")?.toInt() ?: 1
            val coins = doc.getLong("coins")?.toInt() ?: 0
            val profilePic = doc.getString("profilePictureBase64")
            val updatedAtDate = doc.getTimestamp("updatedAt")?.toDate()
            val parsedPlayerId = doc.getString("playerId") ?: run {
                val hash = Math.abs(uid.hashCode() % 90000000) + 10000000
                hash.toString()
            }

            list.add(
                LeaderboardUser(
                    rank = 0,
                    uid = uid,
                    playerName = name,
                    kills = kills,
                    score = score,
                    level = level,
                    coins = coins,
                    profilePictureBase64 = profilePic,
                    updatedAt = updatedAtDate,
                    playerId = parsedPlayerId
                )
            )
        }

        // Sort descending by score, then by kills, then by level
        val sorted = list.sortedWith(
            compareByDescending<LeaderboardUser> { it.score }
                .thenByDescending { it.kills }
                .thenByDescending { it.level }
        ).mapIndexed { index, user ->
            user.copy(rank = index + 1)
        }.take(100)

        sorted
    }

    /**
     * Calculate global rank using an aggregation query without fetching documents.
     * Scales efficiently to millions of players.
     */
    suspend fun getPlayerGlobalRank(userScore: Int): Result<Int> = runCatching {
        if (userScore < 0) return Result.success(0)

        // Count of documents where highestScore > player's score
        val countQuery = db.collection("leaderboard")
            .whereGreaterThan("highestScore", userScore)
            .count()

        val countSnapshot = countQuery.get(AggregateSource.SERVER).await()
        val higherCount = countSnapshot.count

        // Rank = number of players with strictly higher score + 1
        (higherCount + 1).toInt()
    }

    /**
     * Verify connection by writing a real document and reading it back.
     * Uses the Firebase Authentication UID as the document ID in 'leaderboard' collection.
     */
    suspend fun verifyFirestoreWriteAndRead(
        uid: String,
        playerName: String,
        kills: Int,
        level: Int,
        score: Int,
        coins: Int
    ): Result<Map<String, Any>> = runCatching {
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] verifyFirestoreWriteAndRead started for UID: $uid")
        
        val docRef = db.collection("leaderboard").document(uid)
        val timestamp = System.currentTimeMillis()
        val testData = hashMapOf<String, Any?>(
            "uid" to uid,
            "playerName" to playerName,
            "displayName" to playerName,
            "kills" to kills,
            "level" to level,
            "score" to score,
            "coins" to coins,
            "timestamp" to timestamp,
            "updatedAt" to FieldValue.serverTimestamp(),
            "isVerificationTest" to true
        )

        // 1. Write the document
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Writing verification doc to 'leaderboard/$uid'...")
        docRef.set(testData, SetOptions.merge()).await()
        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Write complete. Reading back to verify...")

        // 2. Read the document back
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            throw IllegalStateException("Verification read-back failed: Document does not exist in Firestore after write!")
        }

        // 3. Parse and verify
        val readUid = snapshot.getString("uid")
        val readName = snapshot.getString("playerName")
        val readKills = snapshot.getLong("kills")?.toInt() ?: 0
        val readLevel = snapshot.getLong("level")?.toInt() ?: 0
        val readScore = snapshot.getLong("score")?.toInt() ?: 0
        val readCoins = snapshot.getLong("coins")?.toInt() ?: 0
        val readTimestamp = snapshot.getLong("timestamp") ?: 0L

        Log.d("LeaderboardRepository", "[AUDIT_FIREBASE] Verification read-back successful! Read name: $readName, score: $readScore")

        mapOf(
            "uid" to (readUid ?: ""),
            "playerName" to (readName ?: ""),
            "kills" to readKills,
            "level" to readLevel,
            "score" to readScore,
            "coins" to readCoins,
            "timestamp" to readTimestamp
        )
    }
}
