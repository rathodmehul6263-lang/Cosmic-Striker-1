package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.facebook.login.LoginManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

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
        callbackManager = CallbackManager.Factory.create()
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)

        // Auto-login if previously logged in
        loadSession(context)
    }

    private fun loadSession(context: Context) {
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
     * Sync user progress state from/to SharedPreferences based on account ID.
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSavedDataKey = "user_${userId}_has_saved_data"
        val hasSavedData = prefs.getBoolean(hasSavedDataKey, false)

        if (hasSavedData) {
            // Restore previous progress for this account
            val coins = prefs.getInt("user_${userId}_total_coins", 0)
            val level = prefs.getInt("user_${userId}_highest_unlocked_level", 1)
            val equippedShip = prefs.getString("user_${userId}_equipped_ship_id", "falcon") ?: "falcon"
            val ownedCsv = prefs.getString("user_${userId}_owned_ships_csv", "falcon") ?: "falcon"
            val ownedShips = ownedCsv.split(",").filter { it.isNotEmpty() }.toSet()
            
            onProgressRestored(coins, level, equippedShip, ownedShips)
        } else {
            // New account: Save the current progress as their initial account progress
            saveAccountProgress(context, userId, currentCoins, currentLevel, currentEquippedShip, currentOwnedShips)
            prefs.edit().putBoolean(hasSavedDataKey, true).apply()
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("user_${userId}_total_coins", coins)
            putInt("user_${userId}_highest_unlocked_level", level)
            putString("user_${userId}_equipped_ship_id", equippedShip)
            putString("user_${userId}_owned_ships_csv", ownedShips.joinToString(","))
            apply()
        }
    }
}
