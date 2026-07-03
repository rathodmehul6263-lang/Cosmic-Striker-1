package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.facebook.CallbackManager
import com.facebook.login.LoginManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String,
    val provider: String // "Google" or "Facebook"
)

object AuthManager {
    var currentUser by mutableStateOf<UserProfile?>(null)
    
    // Official SDK fields
    lateinit var callbackManager: CallbackManager
    lateinit var googleSignInClient: GoogleSignInClient

    private const val PREFS_NAME = "cosmic_striker_prefs"

    fun init(context: Context) {
        val appContext = context.applicationContext
        callbackManager = CallbackManager.Factory.create()
        
        try {
            FirebaseApp.initializeApp(appContext)
        } catch (e: Exception) {
            Log.e("AuthManager", "FirebaseApp initialization failed", e)
        }

        val webClientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to resolve R.string.default_web_client_id", e)
            ""
        }

        Log.i("AuthManager", "Runtime config - default_web_client_id loaded: '$webClientId'")

        if (webClientId.isEmpty()) {
            Log.e("AuthManager", "CRITICAL ERROR: default_web_client_id is missing! Ensure google-services.json is correctly configured.")
            Toast.makeText(appContext, "Authentication Config Error: default_web_client_id is missing", Toast.LENGTH_LONG).show()
        }

        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (webClientId.isNotEmpty()) {
            gsoBuilder.requestIdToken(webClientId)
        }
        
        googleSignInClient = GoogleSignIn.getClient(appContext, gsoBuilder.build())

        // Auto-login if previously logged in (via Firebase Auth as single source of truth)
        loadSession(appContext)
    }

    private fun loadSession(context: Context) {
        val firebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to retrieve Firebase currentUser", e)
            null
        }

        if (firebaseUser != null) {
            var provider = "Firebase"
            for (profile in firebaseUser.providerData) {
                if (profile.providerId == "google.com") {
                    provider = "Google"
                    break
                } else if (profile.providerId == "facebook.com") {
                    provider = "Facebook"
                    break
                }
            }
            currentUser = UserProfile(
                id = firebaseUser.uid,
                name = firebaseUser.displayName ?: "Firebase Pilot",
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                provider = provider
            )
        } else {
            // Check if there's a cached profile from SharedPreferences as secondary fallback
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString("auth_user_id", null)
            val name = prefs.getString("auth_user_name", null)
            val email = prefs.getString("auth_user_email", null)
            val photoUrl = prefs.getString("auth_user_photo", null)
            val provider = prefs.getString("auth_user_provider", null)

            if (id != null && name != null && email != null && photoUrl != null && provider != null) {
                currentUser = UserProfile(id, name, email, photoUrl, provider)
            }
        }
    }

    fun saveSession(context: Context, user: UserProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("auth_user_id", user.id)
            putString("auth_user_name", user.name)
            putString("auth_user_email", user.email)
            putString("auth_user_photo", user.photoUrl)
            putString("auth_user_provider", user.provider)
            apply()
        }
        currentUser = user
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("auth_user_id")
            remove("auth_user_name")
            remove("auth_user_email")
            remove("auth_user_photo")
            remove("auth_user_provider")
            apply()
        }
        currentUser = null
    }

    /**
     * Checks if this account qualifies for the 200 bonus coins.
     * Rewards the bonus only ONCE per account, linked using SharedPreferences.
     * Returns true if first-time bonus was rewarded, false otherwise.
     */
    fun checkAndRewardBonus(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bonusKey = "user_${userId}_bonus_rewarded"
        val alreadyRewarded = prefs.getBoolean(bonusKey, false)
        
        if (!alreadyRewarded) {
            prefs.edit().putBoolean(bonusKey, true).apply()
            return true
        }
        return false
    }

    /**
     * Overloaded sync method for startup check compat.
     */
    fun syncAccountProgress(
        context: Context,
        userId: String,
        currentCoins: Int,
        currentLevel: Int,
        currentEquippedShip: String,
        currentOwnedShips: Set<String>,
        onProgressRestored: (coins: Int, level: Int, equippedShip: String, ownedShips: Set<String>) -> Unit
    ) {
        syncAccountProgress(
            context = context,
            userId = userId,
            currentCoins = currentCoins,
            currentLevel = currentLevel,
            currentEquippedShip = currentEquippedShip,
            currentOwnedShips = currentOwnedShips,
            onNewUserReward = {},
            onProgressRestored = onProgressRestored
        )
    }

    /**
     * Sync user progress state from/to Firestore & SharedPreferences.
     */
    fun syncAccountProgress(
        context: Context,
        userId: String,
        currentCoins: Int,
        currentLevel: Int,
        currentEquippedShip: String,
        currentOwnedShips: Set<String>,
        onNewUserReward: () -> Unit,
        onProgressRestored: (coins: Int, level: Int, equippedShip: String, ownedShips: Set<String>) -> Unit
    ) {
        val db = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to get Firestore instance", e)
            null
        }

        if (db == null) {
            // Fallback to local SharedPreferences if Firestore fails
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasSavedDataKey = "user_${userId}_has_saved_data"
            val hasSavedData = prefs.getBoolean(hasSavedDataKey, false)

            if (hasSavedData) {
                val coins = prefs.getInt("user_${userId}_total_coins", currentCoins)
                val level = prefs.getInt("user_${userId}_highest_unlocked_level", currentLevel)
                val equippedShip = prefs.getString("user_${userId}_equipped_ship_id", currentEquippedShip) ?: currentEquippedShip
                val ownedCsv = prefs.getString("user_${userId}_owned_ships_csv", currentOwnedShips.joinToString(",")) ?: currentOwnedShips.joinToString(",")
                val ownedShips = ownedCsv.split(",").filter { it.isNotEmpty() }.toSet()
                onProgressRestored(coins, level, equippedShip, ownedShips)
            } else {
                // First time local fallback
                val alreadyRewarded = prefs.getBoolean("user_${userId}_bonus_rewarded", false)
                if (!alreadyRewarded) {
                    prefs.edit().putBoolean("user_${userId}_bonus_rewarded", true).apply()
                    onNewUserReward()
                }
                saveAccountProgress(context, userId, currentCoins + (if (!alreadyRewarded) 200 else 0), currentLevel, currentEquippedShip, currentOwnedShips)
                prefs.edit().putBoolean(hasSavedDataKey, true).apply()
            }
            return
        }

        val docRef = db.collection("users").document(userId)
        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Existing User! Restore progress from Firestore
                    val coins = document.getLong("coins")?.toInt() ?: currentCoins
                    val level = document.getLong("level")?.toInt() ?: currentLevel
                    val equippedShip = document.getString("equippedShip") ?: currentEquippedShip
                    @Suppress("UNCHECKED_CAST")
                    val ownedList = document.get("ownedShips") as? List<String>
                    val ownedShips = ownedList?.toSet() ?: currentOwnedShips

                    // Sync to local SharedPreferences so they are consistent offline
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("user_${userId}_has_saved_data", true).apply()
                    saveAccountProgress(context, userId, coins, level, equippedShip, ownedShips)

                    // Call restore callback to update UI states
                    onProgressRestored(coins, level, equippedShip, ownedShips)
                } else {
                    // Brand New User in Firestore!
                    // Check local SharedPreferences to see if we already marked them as rewarded locally
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val localRewarded = prefs.getBoolean("user_${userId}_bonus_rewarded", false)
                    
                    val giveReward = !localRewarded
                    val finalCoins = if (giveReward) currentCoins + 200 else currentCoins

                    // Save new profile info to Firestore
                    val data = hashMapOf(
                        "coins" to finalCoins,
                        "level" to currentLevel,
                        "equippedShip" to currentEquippedShip,
                        "ownedShips" to currentOwnedShips.toList(),
                        "rewarded" to true
                    )

                    docRef.set(data)
                        .addOnSuccessListener {
                            Log.d("AuthManager", "Successfully saved initial progress to Firestore")
                        }
                        .addOnFailureListener { e ->
                            Log.e("AuthManager", "Failed to save initial progress to Firestore", e)
                        }

                    // Save local SharedPreferences
                    prefs.edit()
                        .putBoolean("user_${userId}_has_saved_data", true)
                        .putBoolean("user_${userId}_bonus_rewarded", true)
                        .apply()
                    saveAccountProgress(context, userId, finalCoins, currentLevel, currentEquippedShip, currentOwnedShips)

                    if (giveReward) {
                        onNewUserReward()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AuthManager", "Error reading progress from Firestore, falling back to local SharedPreferences", e)
                // If network is offline, use local SharedPreferences as fallback
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val hasSavedDataKey = "user_${userId}_has_saved_data"
                val hasSavedData = prefs.getBoolean(hasSavedDataKey, false)

                if (hasSavedData) {
                    val coins = prefs.getInt("user_${userId}_total_coins", currentCoins)
                    val level = prefs.getInt("user_${userId}_highest_unlocked_level", currentLevel)
                    val equippedShip = prefs.getString("user_${userId}_equipped_ship_id", currentEquippedShip) ?: currentEquippedShip
                    val ownedCsv = prefs.getString("user_${userId}_owned_ships_csv", currentOwnedShips.joinToString(",")) ?: currentOwnedShips.joinToString(",")
                    val ownedShips = ownedCsv.split(",").filter { it.isNotEmpty() }.toSet()
                    onProgressRestored(coins, level, equippedShip, ownedShips)
                }
            }
    }

    fun saveAccountProgress(
        context: Context,
        userId: String,
        coins: Int,
        level: Int,
        equippedShip: String,
        ownedShips: Set<String>
    ) {
        // 1. Save locally in SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("user_${userId}_total_coins", coins)
            putInt("user_${userId}_highest_unlocked_level", level)
            putString("user_${userId}_equipped_ship_id", equippedShip)
            putString("user_${userId}_owned_ships_csv", ownedShips.joinToString(","))
            apply()
        }

        // 2. Save to Firestore asynchronously in background (fire and forget)
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("users").document(userId)
            val data = hashMapOf(
                "coins" to coins,
                "level" to level,
                "equippedShip" to equippedShip,
                "ownedShips" to ownedShips.toList()
            )
            docRef.set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("AuthManager", "Successfully updated progress in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("AuthManager", "Failed to update progress in Firestore", e)
                }
        } catch (e: Exception) {
            Log.e("AuthManager", "Firestore getInstance or set failed", e)
        }
    }
}
