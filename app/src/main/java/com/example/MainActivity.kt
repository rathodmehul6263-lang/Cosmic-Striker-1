package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class GameScreen {
    MENU,
    PLAYING,
    GAMEOVER,
    LEVEL_COMPLETE,
    LEADERBOARD
}

fun getUpgradeCostForLevel(level: Int): Int {
    return when (level) {
        1 -> 200
        2 -> 400
        3 -> 700
        4 -> 1000
        5 -> 1400
        6 -> 1900
        7 -> 2500
        8 -> 3200
        9 -> 4000
        else -> 5000
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        private const val RC_SIGN_IN = 9001
    }

    lateinit var leaderboardManager: LeaderboardManager
    private lateinit var backgroundMusicManager: BackgroundMusicManager
    private lateinit var billingManager: BillingManager
    private var showCoinShopDialog by mutableStateOf(false)
    private var showPurchaseSuccessOverlay by mutableStateOf(false)
    private var successGrantedCoinsAmount by mutableStateOf(0)
    var webView: WebView? = null

    // Compose state variables
    private var currentScreen by mutableStateOf(GameScreen.MENU)
    private var finalScore by mutableStateOf(0)
    private var finalKills by mutableStateOf(0)
    private var isNewHighScore by mutableStateOf(false)
    private var leaderboardList by mutableStateOf(listOf<LeaderboardEntry>())
    private var playerRankState by mutableStateOf("--")
    private var isScoreSavedForCurrentGame = false

    // Level, Coins, Selected Stage, Statistics states
    private var highestLevelState by mutableStateOf(1)
    private var totalCoinsState by mutableStateOf(0)
    private var selectedLevel by mutableStateOf(1)
    private var coinsEarnedState by mutableStateOf(0)
    private var gamesPlayedState by mutableStateOf(0)
    private var totalKillsState by mutableStateOf(0)

    // Upgrades state variables
    private var damageUpgradeLevelState by mutableStateOf(1)
    private var fireRateUpgradeLevelState by mutableStateOf(1)
    private var shieldUpgradeLevelState by mutableStateOf(1)
    private var speedUpgradeLevelState by mutableStateOf(1)

    // Daily rewards state variables
    private var dailyStreakDayState by mutableStateOf(1)
    private var lastRewardClaimTimeState by mutableStateOf(0L)
    private var showDailyRewardsPopup by mutableStateOf(false)

    // Custom Skins state variables
    private var isExclusiveSkinUnlockedState by mutableStateOf(false)
    private var isLegendarySkinUnlockedState by mutableStateOf(false)
    private var isExclusiveSkinEnabledState by mutableStateOf(false)
    private var isLegendarySkinEnabledState by mutableStateOf(false)

    // Achievements Dialog state variables
    data class AchievementPopupData(val id: String, val title: String, val description: String, val coinReward: Int)
    private var activeAchievementPopup by mutableStateOf<AchievementPopupData?>(null)
    private var showAchievementsDialog by mutableStateOf(false)

    // Upgrade Dialog state variable
    private var showUpgradeDialog by mutableStateOf(false)

    // Ads simulator state variables
    private var showAdSimulator by mutableStateOf(false)
    private var adRewardAction by mutableStateOf<(() -> Unit)?>(null)
    private var adCountdownSeconds by mutableStateOf(5)
    private var continuedThisGame by mutableStateOf(false)

    fun onLevelCompleted(coinsEarned: Int, totalCoins: Int) {
        coinsEarnedState = coinsEarned
        setTotalCoins(totalCoins)
        val nextLvl = selectedLevel + 1
        if (nextLvl > highestLevelState && nextLvl <= 50) {
            setHighestLevel(nextLvl)
        }
        currentScreen = GameScreen.LEVEL_COMPLETE
    }

    fun isSectorCompletedAndRewarded(level: Int): Boolean {
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val rewarded = prefs.getStringSet("rewarded_sectors_completion", emptySet()) ?: emptySet()
        return rewarded.contains(level.toString())
    }

    fun markSectorRewarded(level: Int) {
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val rewarded = (prefs.getStringSet("rewarded_sectors_completion", emptySet()) ?: emptySet()).toMutableSet()
        rewarded.add(level.toString())
        prefs.edit().putStringSet("rewarded_sectors_completion", rewarded).apply()
    }

    fun awardSectorCompletionReward(level: Int) {
        if (!isSectorCompletedAndRewarded(level)) {
            markSectorRewarded(level)
            val newTotal = totalCoinsState + 50
            setTotalCoins(newTotal)
            Toast.makeText(this@MainActivity, "Sector Complete! +50 Coins", Toast.LENGTH_LONG).show()
        }
    }

    fun getHighestLevel(): Int = highestLevelState
    fun setHighestLevel(level: Int) {
        highestLevelState = level
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("highest_unlocked_level", level).apply()
        AuthManager.syncProfileToFirestore(this)
    }

    fun getTotalCoins(): Int = totalCoinsState
    fun setTotalCoins(coins: Int) {
        totalCoinsState = coins
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_coins", coins).apply()
        AuthManager.syncProfileToFirestore(this)
    }

    // Spaceship Garage & Selection state variables
    private var equippedShipIdState by mutableStateOf("falcon")
    private var ownedShipsState by mutableStateOf(setOf("falcon"))

    fun getEquippedShipId(): String = equippedShipIdState
    fun setEquippedShipId(shipId: String) {
        equippedShipIdState = shipId
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("equipped_ship_id", shipId).apply()
        webView?.post {
            webView?.evaluateJavascript("window.syncEquippedShip()", null)
        }
    }

    fun getOwnedShips(): Set<String> = ownedShipsState
    fun setOwnedShips(ships: Set<String>) {
        ownedShipsState = ships.toSet()
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val csv = ships.joinToString(",")
        prefs.edit()
            .putString("owned_ships_csv", csv)
            .putStringSet("owned_ships", ships)
            .apply()
    }

    // Sound, Music, Vibration, Pause states
    private var isPaused by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    
    // Authentication & Reward states
    private var showAuthWelcomeScreen by mutableStateOf(false)
    private var showBonusPopup by mutableStateOf(false)
    private var showProfileDialog by mutableStateOf(false)
    
    private var soundEffectsEnabledState by mutableStateOf(true)
    private var musicEnabledState by mutableStateOf(true)
    private var vibrationEnabledState by mutableStateOf(true)

    private val hasAudioOutput: Boolean by lazy {
        try {
            val pm = packageManager
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUDIO_OUTPUT)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking audio output capability", e)
            true
        }
    }

    fun getSoundEffectsEnabled(): Boolean {
        if (!hasAudioOutput) return false
        return soundEffectsEnabledState
    }

    fun getMusicEnabled(): Boolean {
        if (!hasAudioOutput) return false
        return musicEnabledState
    }

    fun getVibrationEnabled(): Boolean = vibrationEnabledState

    fun setSoundEffectsEnabled(enabled: Boolean) {
        soundEffectsEnabledState = enabled
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("settings_sound_effects", enabled).apply()
        webView?.post {
            webView?.evaluateJavascript("window.updateSettings()", null)
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabledState = enabled
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("settings_music", enabled).apply()
        if (::backgroundMusicManager.isInitialized) {
            backgroundMusicManager.setEnabled(enabled)
        }
        webView?.post {
            webView?.evaluateJavascript("window.updateSettings()", null)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabledState = enabled
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("settings_vibration", enabled).apply()
        webView?.post {
            webView?.evaluateJavascript("window.updateSettings()", null)
        }
    }

    fun vibratePhone(durationMs: Long) {
        if (!vibrationEnabledState) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }

    private val profileImagePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = AuthManager.cropAndSaveProfilePicture(this, uri)
            if (savedPath != null) {
                AuthManager.init(this)
                AuthManager.syncProfileToFirestore(this)
                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to update profile picture.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun registerPilot(displayName: String) {
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val generatedUid = (100000 + (Math.random() * 900000).toLong()).toString()
        
        prefs.edit()
            .putString("player_uid", generatedUid)
            .putString("player_name", displayName)
            .putInt("total_coins", 200)
            .putInt("highest_unlocked_level", 1)
            .putInt("total_kills_stat", 0)
            .putInt("games_played_stat", 0)
            .apply()

        highestLevelState = 1
        totalCoinsState = 200
        gamesPlayedState = 0
        totalKillsState = 0

        AuthManager.init(this)
        AuthManager.syncProfileToFirestore(this)

        showBonusPopup = true
        showAuthWelcomeScreen = false
    }

    fun updatePilotName(newName: String) {
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("player_name", newName).apply()
        
        AuthManager.init(this)
        AuthManager.syncProfileToFirestore(this)
        Toast.makeText(this, "Pilot name updated!", Toast.LENGTH_SHORT).show()
    }

    fun getUpgradeLevel(statName: String): Int {
        return when (statName) {
            "damage" -> damageUpgradeLevelState
            "fire_rate" -> fireRateUpgradeLevelState
            "shield" -> shieldUpgradeLevelState
            "speed" -> speedUpgradeLevelState
            else -> 1
        }
    }

    fun isExclusiveSkinEnabled(): Boolean = isExclusiveSkinEnabledState
    fun isLegendarySkinEnabled(): Boolean = isLegendarySkinEnabledState

    fun checkDailyRewards() {
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val lastClaim = prefs.getLong("last_reward_claim_time", 0L)
        val elapsed = now - lastClaim
        
        // Consecutive Login Streak Expiration logic:
        // If they claimed before, but more than 48 hours have elapsed, they missed a day.
        // Reset streak back to Day 1!
        if (lastClaim > 0L && elapsed >= 48 * 60 * 60 * 1000L) {
            dailyStreakDayState = 1
            prefs.edit().putInt("daily_streak_day", 1).apply()
        }

        // Auto show Daily Login Reward Popup if eligible on launch
        if (elapsed >= 24 * 60 * 60 * 1000L) {
            showDailyRewardsPopup = true
        }
    }

    fun claimDailyReward() {
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        
        val lastClaim = prefs.getLong("last_reward_claim_time", 0L)
        val elapsed = now - lastClaim
        if (elapsed < 24 * 60 * 60 * 1000L) {
            Toast.makeText(this, "Reward already claimed today!", Toast.LENGTH_SHORT).show()
            return
        }

        val rewardAmount = when (dailyStreakDayState) {
            1 -> 100
            2 -> 150
            3 -> 200
            4 -> 250
            5 -> 300
            6 -> 400
            7 -> 0 // Exclusive Skin Reward
            else -> 100
        }

        if (dailyStreakDayState == 7) {
            val alreadyUnlocked = isExclusiveSkinUnlockedState
            isExclusiveSkinUnlockedState = true
            prefs.edit().putBoolean("exclusive_skin_unlocked", true).apply()
            if (alreadyUnlocked) {
                setTotalCoins(totalCoinsState + 500)
                Toast.makeText(this, "Day 7 Claimed: Exclusive Neon Pink Skin (Already Owned) + 500 Coins!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Day 7 Claimed: Exclusive Neon Pink Skin!", Toast.LENGTH_LONG).show()
            }
        } else {
            setTotalCoins(totalCoinsState + rewardAmount)
            Toast.makeText(this, "Day $dailyStreakDayState Claimed: $rewardAmount Coins!", Toast.LENGTH_SHORT).show()
        }

        val nextStreakDay = if (dailyStreakDayState >= 7) 1 else dailyStreakDayState + 1
        dailyStreakDayState = nextStreakDay
        lastRewardClaimTimeState = now

        prefs.edit()
            .putInt("daily_streak_day", nextStreakDay)
            .putLong("last_reward_claim_time", now)
            .apply()

        showDailyRewardsPopup = false
        AuthManager.syncProfileToFirestore(this)
    }

    fun checkAchievements() {
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val completed = prefs.getStringSet("completed_achievements", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val totalKills = prefs.getInt("total_kills_stat", 0)
        val highestLevel = prefs.getInt("highest_unlocked_level", 1)
        val totalCoins = prefs.getInt("total_coins", 0)

        // Achievement 1: Destroy 100 Enemies
        if (totalKills >= 100 && !completed.contains("kills_100")) {
            completed.add("kills_100")
            awardAchievement("kills_100", "DEVASTATOR I", "Destroyed 100 enemy ships!", 500)
        }
        // Achievement 2: Destroy 500 Enemies
        if (totalKills >= 500 && !completed.contains("kills_500")) {
            completed.add("kills_500")
            awardAchievement("kills_500", "DEVASTATOR II", "Destroyed 500 enemy ships!", 1000)
        }
        // Achievement 3: Destroy 1000 Enemies
        if (totalKills >= 1000 && !completed.contains("kills_1000")) {
            completed.add("kills_1000")
            awardAchievement("kills_1000", "COSMIC CONQUEROR", "Destroyed 1000 enemy ships!", 2500)
        }
        // Achievement 4: Reach Level 10
        if (highestLevel >= 10 && !completed.contains("level_10")) {
            completed.add("level_10")
            awardAchievement("level_10", "SECTOR COMMANDER", "Reached Sector 10!", 300)
        }
        // Achievement 5: Reach Level 25
        if (highestLevel >= 25 && !completed.contains("level_25")) {
            completed.add("level_25")
            awardAchievement("level_25", "GALACTIC SENTINEL", "Reached Sector 25!", 800)
        }
        // Achievement 6: Reach Level 50
        if (highestLevel >= 50 && !completed.contains("level_50")) {
            completed.add("level_50")
            awardAchievement("level_50", "OMEGA COMMANDER", "Reached Sector 50!", 2000)
        }
        // Achievement 7: Collect 10,000 Coins
        if (totalCoins >= 10000 && !completed.contains("coins_10000")) {
            completed.add("coins_10000")
            awardAchievement("coins_10000", "TREASURE HUNTER", "Amassed 10,000 total stellar credits!", 1000)
        }
        // Achievement 8: Unlock Every Spaceship (8 spaceships)
        if (ownedShipsState.size >= 8 && !completed.contains("unlock_all_ships")) {
            completed.add("unlock_all_ships")
            isLegendarySkinUnlockedState = true
            prefs.edit().putBoolean("legendary_skin_unlocked", true).apply()
            awardAchievement("unlock_all_ships", "LEGENDARY PILOT", "Unlocked every single spaceship! Unlocked Legendary Ship Skin!", 0)
        }

        prefs.edit().putStringSet("completed_achievements", completed).apply()
    }

    private fun awardAchievement(id: String, title: String, description: String, coinReward: Int) {
        setTotalCoins(totalCoinsState + coinReward)
        activeAchievementPopup = AchievementPopupData(id, title, description, coinReward)
        webView?.post {
            webView?.evaluateJavascript("if (window.sound) { window.sound.playPowerUp(); }", null)
        }
    }

    fun onEnemyDestroyed() {
        totalKillsState += 1
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_kills_stat", totalKillsState).apply()
        checkAchievements()
        AuthManager.syncProfileToFirestore(this)
    }

    override fun onResume() {
        super.onResume()
        if (::backgroundMusicManager.isInitialized) {
            backgroundMusicManager.resumeMusic()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::backgroundMusicManager.isInitialized) {
            backgroundMusicManager.pauseMusic()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::backgroundMusicManager.isInitialized) {
            backgroundMusicManager.release()
        }
        if (::billingManager.isInitialized) {
            billingManager.destroy()
        }
    }

    fun playClickSound() {
        webView?.post {
            webView?.evaluateJavascript("sound.playClick()", null)
        }
    }

    fun submitScoreToOnlineLeaderboard(score: Int, kills: Int) {
        val user = AuthManager.currentUser
        if (user != null) {
            val coins = getTotalCoins()
            leaderboardManager.updateOnlineLeaderboard(
                userId = user.id,
                displayName = user.name,
                newScore = score,
                killsEarned = kills,
                currentCoins = coins
            )
        }
        AuthManager.syncProfileToFirestore(this)
    }

    fun updateWebViewRank(rank: String) {
        webView?.post {
            webView?.evaluateJavascript("if (window.updateRankDisplay) { window.updateRankDisplay('$rank'); }", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        leaderboardManager = LeaderboardManager(this)
        leaderboardList = leaderboardManager.getTopScores()

        // Load settings permanently from local storage (SharedPreferences)
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        playerRankState = leaderboardManager.getCachedRank()

        AuthManager.onSyncSuccess = {
            val pUid = prefs.getString("player_uid", null)
            if (pUid != null) {
                leaderboardManager.fetchAndCacheGlobalRank(pUid, forceRefresh = true) { rankStr ->
                    playerRankState = rankStr
                    updateWebViewRank(rankStr)
                }
            }
        }
        
        val pUid = prefs.getString("player_uid", null)
        if (pUid != null) {
            leaderboardManager.fetchAndCacheGlobalRank(pUid, forceRefresh = true) { rankStr ->
                playerRankState = rankStr
                updateWebViewRank(rankStr)
            }
        }
        soundEffectsEnabledState = prefs.getBoolean("settings_sound_effects", true)
        musicEnabledState = prefs.getBoolean("settings_music", true)
        vibrationEnabledState = prefs.getBoolean("settings_vibration", true)
        
        backgroundMusicManager = BackgroundMusicManager(this)
        backgroundMusicManager.setEnabled(musicEnabledState)
        // Load local player progress directly
        highestLevelState = prefs.getInt("highest_unlocked_level", 1)
        totalCoinsState = prefs.getInt("total_coins", 0)
        selectedLevel = highestLevelState

        equippedShipIdState = prefs.getString("equipped_ship_id", "falcon") ?: "falcon"
        val ownedShipsStr = prefs.getString("owned_ships_csv", null)
        ownedShipsState = if (ownedShipsStr != null) {
            ownedShipsStr.split(",").filter { it.isNotEmpty() }.toSet()
        } else {
            prefs.getStringSet("owned_ships", setOf("falcon")) ?: setOf("falcon")
        }

        gamesPlayedState = prefs.getInt("games_played_stat", 0)
        totalKillsState = prefs.getInt("total_kills_stat", 0)

        // Load stat upgrades
        damageUpgradeLevelState = prefs.getInt("upgrade_damage_level", 1)
        fireRateUpgradeLevelState = prefs.getInt("upgrade_fire_rate", 1)
        shieldUpgradeLevelState = prefs.getInt("upgrade_shield_level", 1)
        speedUpgradeLevelState = prefs.getInt("upgrade_speed_level", 1)

        // Load daily rewards state
        dailyStreakDayState = prefs.getInt("daily_streak_day", 1)
        lastRewardClaimTimeState = prefs.getLong("last_reward_claim_time", 0L)

        // Load custom skins state
        isExclusiveSkinUnlockedState = prefs.getBoolean("exclusive_skin_unlocked", false)
        isLegendarySkinUnlockedState = prefs.getBoolean("legendary_skin_unlocked", false)
        isExclusiveSkinEnabledState = prefs.getBoolean("exclusive_skin_enabled", false)
        isLegendarySkinEnabledState = prefs.getBoolean("legendary_skin_enabled", false)

        // Check if custom player profile is created
        val uid = prefs.getString("player_uid", null)
        val name = prefs.getString("player_name", null)
        if (uid == null || name == null) {
            showAuthWelcomeScreen = true
        } else {
            showAuthWelcomeScreen = false
            AuthManager.init(this)
            
            // Execute Cloud Restore & Merge
            AuthManager.restoreProfileFromFirestore(this, uid) { success ->
                damageUpgradeLevelState = prefs.getInt("upgrade_damage_level", damageUpgradeLevelState)
                fireRateUpgradeLevelState = prefs.getInt("upgrade_fire_rate", fireRateUpgradeLevelState)
                shieldUpgradeLevelState = prefs.getInt("upgrade_shield_level", shieldUpgradeLevelState)
                speedUpgradeLevelState = prefs.getInt("upgrade_speed_level", speedUpgradeLevelState)

                dailyStreakDayState = prefs.getInt("daily_streak_day", dailyStreakDayState)
                lastRewardClaimTimeState = prefs.getLong("last_reward_claim_time", lastRewardClaimTimeState)

                isExclusiveSkinUnlockedState = prefs.getBoolean("exclusive_skin_unlocked", isExclusiveSkinUnlockedState)
                isLegendarySkinUnlockedState = prefs.getBoolean("legendary_skin_unlocked", isLegendarySkinUnlockedState)
                isExclusiveSkinEnabledState = prefs.getBoolean("exclusive_skin_enabled", isExclusiveSkinEnabledState)
                isLegendarySkinEnabledState = prefs.getBoolean("legendary_skin_enabled", isLegendarySkinEnabledState)

                highestLevelState = prefs.getInt("highest_unlocked_level", highestLevelState)
                totalCoinsState = prefs.getInt("total_coins", totalCoinsState)
                totalKillsState = prefs.getInt("total_kills_stat", totalKillsState)
                gamesPlayedState = prefs.getInt("games_played_stat", gamesPlayedState)
                
                val eqShip = prefs.getString("equipped_ship_id", equippedShipIdState) ?: "falcon"
                equippedShipIdState = eqShip

                // Perform checks
                checkAchievements()
                checkDailyRewards()

                AuthManager.syncProfileToFirestore(this)
            }
        }

        // Hide Android System Bars (Status and Navigation) to provide true immersive arcade view
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        // Pre-create WebView cache directories to prevent inaccessible/missing directory errors on start up
        try {
            val cacheDir = cacheDir
            val jsCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!jsCache.exists()) {
                jsCache.mkdirs()
            }
            val wasmCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!wasmCache.exists()) {
                wasmCache.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        billingManager = BillingManager(
            context = this,
            onCoinsPurchased = { productId, coinAmount ->
                val newCoins = totalCoinsState + coinAmount
                setTotalCoins(newCoins)
                successGrantedCoinsAmount = coinAmount
                showPurchaseSuccessOverlay = true
            },
            onPurchaseFailed = { productId, errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            },
            onPurchaseCancelled = { productId ->
                Toast.makeText(this, "Purchase Cancelled", Toast.LENGTH_SHORT).show()
            }
        )

        setContent {
            val isBillingReadyState by billingManager.isBillingReady.collectAsState()
            val productDetailsMapState by billingManager.productDetailsMap.collectAsState()
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF03030C)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Level 0: The WebView running the background loop and game canvas
                        GameWebViewContainer(
                            onWebViewCreated = { wv ->
                                webView = wv
                            }
                        )

                        LaunchedEffect(currentScreen, musicEnabledState) {
                            if (musicEnabledState) {
                                when (currentScreen) {
                                    GameScreen.MENU -> {
                                        backgroundMusicManager.startMenuMusic()
                                    }
                                    GameScreen.PLAYING -> {
                                        backgroundMusicManager.startGameplayMusic()
                                    }
                                    GameScreen.GAMEOVER, GameScreen.LEVEL_COMPLETE, GameScreen.LEADERBOARD -> {
                                        backgroundMusicManager.startMenuMusic()
                                    }
                                }
                            } else {
                                backgroundMusicManager.stopMusic()
                            }
                        }

                        // Handle Android Back presses smoothly
                        BackHandler(enabled = true) {
                            if (currentScreen == GameScreen.PLAYING) {
                                if (isPaused) {
                                    isPaused = false
                                    webView?.evaluateJavascript("window.resumeGame()", null)
                                } else {
                                    isPaused = true
                                    webView?.evaluateJavascript("window.pauseGame()", null)
                                }
                            } else if (currentScreen == GameScreen.GAMEOVER || currentScreen == GameScreen.LEVEL_COMPLETE || currentScreen == GameScreen.LEADERBOARD) {
                                if (currentScreen == GameScreen.LEVEL_COMPLETE) {
                                    awardSectorCompletionReward(selectedLevel)
                                }
                                currentScreen = GameScreen.MENU
                                isPaused = false
                                webView?.evaluateJavascript("window.showStartScreen()", null)
                            } else if (currentScreen == GameScreen.MENU) {
                                finish()
                            }
                        }

                        // Level 1 & 2 overlays
                        when (currentScreen) {
                            GameScreen.MENU -> {
                                MainMenuOverlay(
                                    topScores = leaderboardList,
                                    highestUnlockedLevel = highestLevelState,
                                    totalCoins = totalCoinsState,
                                    selectedLevel = selectedLevel,
                                    onLevelSelected = { lvl ->
                                        playClickSound()
                                        selectedLevel = lvl
                                    },
                                    onLaunchMission = {
                                        isPaused = false
                                        continuedThisGame = false
                                        currentScreen = GameScreen.PLAYING
                                        webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                    },
                                    onSettingsClick = {
                                        playClickSound()
                                        showSettingsDialog = true
                                    },
                                    soundEnabled = soundEffectsEnabledState && musicEnabledState,
                                    onSoundToggled = { enabled ->
                                        setSoundEffectsEnabled(enabled)
                                        setMusicEnabled(enabled)
                                        setVibrationEnabled(enabled)
                                        playClickSound()
                                    },
                                    equippedShipId = equippedShipIdState,
                                    ownedShips = ownedShipsState,
                                    onEquipShip = { shipId ->
                                        setEquippedShipId(shipId)
                                        playClickSound()
                                        val shipName = SPACESHIPS_LIST.find { it.id == shipId }?.name ?: "Spaceship"
                                        Toast.makeText(this@MainActivity, "$shipName equipped!", Toast.LENGTH_SHORT).show()
                                    },
                                    onBuyShip = { shipId, cost ->
                                        if (totalCoinsState >= cost) {
                                            setTotalCoins(totalCoinsState - cost)
                                            setOwnedShips(ownedShipsState + shipId)
                                            playClickSound()
                                            val shipName = SPACESHIPS_LIST.find { it.id == shipId }?.name ?: "Spaceship"
                                            Toast.makeText(this@MainActivity, "$shipName purchased successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Not enough coins! Play levels to earn more.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onCoinShopClick = {
                                        playClickSound()
                                        showCoinShopDialog = true
                                    },
                                    onLeaderboardClick = {
                                        playClickSound()
                                        currentScreen = GameScreen.LEADERBOARD
                                    },
                                    onProfileClick = {
                                        playClickSound()
                                        val pUid = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE).getString("player_uid", null)
                                        if (pUid != null) {
                                            leaderboardManager.fetchAndCacheGlobalRank(pUid, forceRefresh = true) { rankStr ->
                                                playerRankState = rankStr
                                                updateWebViewRank(rankStr)
                                            }
                                        }
                                        showProfileDialog = true
                                    },
                                    playerRank = playerRankState,
                                    onUpgradeClick = {
                                        playClickSound()
                                        showUpgradeDialog = true
                                    },
                                    onDailyRewardsClick = {
                                        playClickSound()
                                        showDailyRewardsPopup = true
                                    },
                                    onAchievementsClick = {
                                        playClickSound()
                                        showAchievementsDialog = true
                                    },
                                    onEarnCoinsClick = {
                                        playClickSound()
                                        adRewardAction = {
                                            setTotalCoins(totalCoinsState + 200)
                                            Toast.makeText(this@MainActivity, "+200 Coins Added!", Toast.LENGTH_LONG).show()
                                        }
                                        adCountdownSeconds = 5
                                        showAdSimulator = true
                                    }
                                )
                            }
                            GameScreen.LEADERBOARD -> {
                                LeaderboardScreen(
                                    currentUserId = AuthManager.currentUser?.id,
                                    onClose = {
                                        playClickSound()
                                        currentScreen = GameScreen.MENU
                                    },
                                    onRankCalculated = { rankStr ->
                                        playerRankState = rankStr
                                        updateWebViewRank(rankStr)
                                    }
                                )
                            }
                            GameScreen.GAMEOVER -> {
                                GameOverOverlay(
                                    score = finalScore,
                                    kills = finalKills,
                                    isNewHighScore = isNewHighScore,
                                    topScores = leaderboardList,
                                    continuedThisGame = continuedThisGame,
                                    onContinueClick = {
                                        playClickSound()
                                        adRewardAction = {
                                            continuedThisGame = true
                                            currentScreen = GameScreen.PLAYING
                                            webView?.evaluateJavascript("window.continueGameAfterAd()", null)
                                        }
                                        adCountdownSeconds = 5
                                        showAdSimulator = true
                                    },
                                    onDeployAgain = {
                                        isPaused = false
                                        continuedThisGame = false
                                        currentScreen = GameScreen.PLAYING
                                        webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                    },
                                    onReturnToHangar = {
                                        isPaused = false
                                        currentScreen = GameScreen.MENU
                                        webView?.evaluateJavascript("window.showStartScreen()", null)
                                    }
                                )
                            }
                            GameScreen.LEVEL_COMPLETE -> {
                                LevelCompleteOverlay(
                                    level = selectedLevel,
                                    coinsEarned = coinsEarnedState,
                                    totalCoins = totalCoinsState,
                                    onNextLevel = {
                                        awardSectorCompletionReward(selectedLevel)
                                        if (selectedLevel < 50) {
                                            selectedLevel += 1
                                            isPaused = false
                                            continuedThisGame = false
                                            currentScreen = GameScreen.PLAYING
                                            webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                        }
                                    },
                                    onReplay = {
                                        awardSectorCompletionReward(selectedLevel)
                                        isPaused = false
                                        continuedThisGame = false
                                        currentScreen = GameScreen.PLAYING
                                        webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                    },
                                    onReturnToHangar = {
                                        awardSectorCompletionReward(selectedLevel)
                                        isPaused = false
                                        currentScreen = GameScreen.MENU
                                        webView?.evaluateJavascript("window.showStartScreen()", null)
                                    }
                                )
                            }
                            GameScreen.PLAYING -> {
                                if (!isPaused) {
                                    // Professional Floating Pause Button at top-right corner
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 75.dp, end = 16.dp),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        IconButton(
                                            onClick = {
                                                playClickSound()
                                                isPaused = true
                                                webView?.evaluateJavascript("window.pauseGame()", null)
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0x88000000), shape = RoundedCornerShape(12.dp))
                                                .border(BorderStroke(1.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(12.dp))
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 4.dp, height = 16.dp)
                                                        .background(Color(0xFF00F0FF), shape = RoundedCornerShape(1.dp))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 4.dp, height = 16.dp)
                                                        .background(Color(0xFF00F0FF), shape = RoundedCornerShape(1.dp))
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Professional Cybernetic Pause Menu Overlay
                                    PauseMenuOverlay(
                                        onResume = {
                                            isPaused = false
                                            webView?.evaluateJavascript("window.resumeGame()", null)
                                        },
                                        onRestart = {
                                            isPaused = false
                                            webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                        },
                                        onMainMenu = {
                                            isPaused = false
                                            currentScreen = GameScreen.MENU
                                            webView?.evaluateJavascript("window.showStartScreen()", null)
                                        },
                                        onSettings = {
                                            showSettingsDialog = true
                                        },
                                        playClick = { playClickSound() }
                                    )
                                }
                            }
                        }

                        // Overlay Settings Dialog on top of any active screen when open
                        if (showSettingsDialog) {
                            SettingsDialog(
                                soundEffectsEnabled = soundEffectsEnabledState,
                                onSoundEffectsChanged = { setSoundEffectsEnabled(it) },
                                musicEnabled = musicEnabledState,
                                onMusicChanged = { setMusicEnabled(it) },
                                vibrationEnabled = vibrationEnabledState,
                                onVibrationChanged = { setVibrationEnabled(it) },
                                onClose = { showSettingsDialog = false },
                                playClick = { playClickSound() }
                            )
                        }

                        // Overlay Coin Shop Dialog on top of any active screen when open
                        if (showCoinShopDialog) {
                            CoinShopDialog(
                                isBillingReady = isBillingReadyState,
                                productDetailsMap = productDetailsMapState,
                                onBuyPack = { productId ->
                                    billingManager.launchPurchaseFlow(this@MainActivity, productId)
                                },
                                onClose = { showCoinShopDialog = false },
                                playClick = { playClickSound() },
                                showSuccessOverlay = showPurchaseSuccessOverlay,
                                successGrantedAmount = successGrantedCoinsAmount,
                                onSuccessOverlayDismiss = {
                                    showPurchaseSuccessOverlay = false
                                    showCoinShopDialog = false
                                }
                            )
                        }

                        // Auth Welcome Screen for first launches
                        if (showAuthWelcomeScreen) {
                            AuthWelcomeOverlay(
                                onRegister = { displayName ->
                                    playClickSound()
                                    registerPilot(displayName)
                                }
                            )
                        }

                        // Profile details view (Logout and Sync stats)
                        if (showProfileDialog) {
                            ProfileDialog(
                                totalKills = totalKillsState,
                                gamesPlayed = gamesPlayedState,
                                highestLevel = highestLevelState,
                                totalCoins = totalCoinsState,
                                onUpdateName = { newName ->
                                    playClickSound()
                                    updatePilotName(newName)
                                },
                                onChangeProfilePicture = {
                                    playClickSound()
                                    profileImagePickerLauncher.launch("image/*")
                                },
                                onClose = {
                                    playClickSound()
                                    showProfileDialog = false
                                },
                                playerRank = playerRankState
                            )
                        }

                        // Daily Rewards Dialog
                        if (showDailyRewardsPopup) {
                            DailyRewardsDialog(
                                currentStreakDay = dailyStreakDayState,
                                lastClaimTime = lastRewardClaimTimeState,
                                onClaim = {
                                    playClickSound()
                                    claimDailyReward()
                                },
                                onClose = {
                                    playClickSound()
                                    showDailyRewardsPopup = false
                                }
                            )
                        }

                        // Upgrade Dialog
                        if (showUpgradeDialog) {
                            UpgradeDialog(
                                currentCoins = totalCoinsState,
                                damageLvl = damageUpgradeLevelState,
                                fireRateLvl = fireRateUpgradeLevelState,
                                shieldLvl = shieldUpgradeLevelState,
                                speedLvl = speedUpgradeLevelState,
                                onUpgrade = { statName ->
                                    val multiplier = when (statName) {
                                        "damage" -> 100
                                        "fire_rate" -> 120
                                        "shield" -> 150
                                        "speed" -> 100
                                        else -> 100
                                    }
                                    val currentLvl = when (statName) {
                                        "damage" -> damageUpgradeLevelState
                                        "fire_rate" -> fireRateUpgradeLevelState
                                        "shield" -> shieldUpgradeLevelState
                                        "speed" -> speedUpgradeLevelState
                                        else -> 1
                                    }
                                    val cost = getUpgradeCostForLevel(currentLvl)
                                    if (currentLvl >= 10) {
                                        Toast.makeText(this@MainActivity, "Stat already at MAX level!", Toast.LENGTH_SHORT).show()
                                    } else if (totalCoinsState < cost) {
                                        Toast.makeText(this@MainActivity, "Insufficient coins!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        playClickSound()
                                        setTotalCoins(totalCoinsState - cost)
                                        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                                        when (statName) {
                                            "damage" -> {
                                                damageUpgradeLevelState += 1
                                                prefs.edit().putInt("upgrade_damage_level", damageUpgradeLevelState).apply()
                                            }
                                            "fire_rate" -> {
                                                fireRateUpgradeLevelState += 1
                                                prefs.edit().putInt("upgrade_fire_rate", fireRateUpgradeLevelState).apply()
                                            }
                                            "shield" -> {
                                                shieldUpgradeLevelState += 1
                                                prefs.edit().putInt("upgrade_shield_level", shieldUpgradeLevelState).apply()
                                            }
                                            "speed" -> {
                                                speedUpgradeLevelState += 1
                                                prefs.edit().putInt("upgrade_speed_level", speedUpgradeLevelState).apply()
                                            }
                                        }
                                        // Immediately notify HTML5 game loop
                                        webView?.evaluateJavascript("window.syncEquippedShip()", null)
                                        AuthManager.syncProfileToFirestore(this@MainActivity)
                                        Toast.makeText(this@MainActivity, "Ship systems upgraded!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isExclusiveUnlocked = isExclusiveSkinUnlockedState,
                                isLegendaryUnlocked = isLegendarySkinUnlockedState,
                                isExclusiveEnabled = isExclusiveSkinEnabledState,
                                isLegendaryEnabled = isLegendarySkinEnabledState,
                                onToggleExclusive = { enabled ->
                                    playClickSound()
                                    isExclusiveSkinEnabledState = enabled
                                    if (enabled) {
                                        isLegendarySkinEnabledState = false
                                    }
                                    val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putBoolean("exclusive_skin_enabled", isExclusiveSkinEnabledState)
                                        .putBoolean("legendary_skin_enabled", isLegendarySkinEnabledState)
                                        .apply()
                                    webView?.evaluateJavascript("window.syncEquippedShip()", null)
                                    AuthManager.syncProfileToFirestore(this@MainActivity)
                                },
                                onToggleLegendary = { enabled ->
                                    playClickSound()
                                    isLegendarySkinEnabledState = enabled
                                    if (enabled) {
                                        isExclusiveSkinEnabledState = false
                                    }
                                    val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putBoolean("exclusive_skin_enabled", isExclusiveSkinEnabledState)
                                        .putBoolean("legendary_skin_enabled", isLegendarySkinEnabledState)
                                        .apply()
                                    webView?.evaluateJavascript("window.syncEquippedShip()", null)
                                    AuthManager.syncProfileToFirestore(this@MainActivity)
                                },
                                onWatchAdForCoins = {
                                    playClickSound()
                                    adRewardAction = {
                                        setTotalCoins(totalCoinsState + 200)
                                        Toast.makeText(this@MainActivity, "Rewarded 200 Stellar Coins!", Toast.LENGTH_SHORT).show()
                                    }
                                    adCountdownSeconds = 5
                                    showAdSimulator = true
                                },
                                onClose = {
                                    playClickSound()
                                    showUpgradeDialog = false
                                }
                            )
                        }

                        // Achievements Dialog
                        if (showAchievementsDialog) {
                            val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                            val completedSet = prefs.getStringSet("completed_achievements", emptySet()) ?: emptySet()
                            AchievementsDialog(
                                completedIds = completedSet,
                                totalKills = totalKillsState,
                                highestLevel = highestLevelState,
                                totalCoins = totalCoinsState,
                                ownedShipsCount = ownedShipsState.size,
                                onClose = {
                                    playClickSound()
                                    showAchievementsDialog = false
                                }
                            )
                        }

                        // Ads Simulator Overlay
                        if (showAdSimulator) {
                            AdSimulator(
                                secondsRemaining = adCountdownSeconds,
                                onSecondsTick = {
                                    if (adCountdownSeconds > 0) {
                                        adCountdownSeconds -= 1
                                    }
                                },
                                onFinished = {
                                    playClickSound()
                                    showAdSimulator = false
                                    adRewardAction?.invoke()
                                },
                                onSkip = {
                                    playClickSound()
                                    showAdSimulator = false
                                    adRewardAction = null
                                    Toast.makeText(this@MainActivity, "Watch the full ad to earn coins.", Toast.LENGTH_LONG).show()
                                }
                            )
                        }

                        // Animated Achievement Toast/Popup
                        activeAchievementPopup?.let { popup ->
                            AchievementAnimatedPopup(
                                title = popup.title,
                                description = popup.description,
                                coinReward = popup.coinReward,
                                onDismiss = {
                                    playClickSound()
                                    activeAchievementPopup = null
                                }
                            )
                        }

                        // 200 Coin bonus celebration popup
                        if (showBonusPopup) {
                            BonusCoinsPopup(
                                onCollect = {
                                    playClickSound()
                                    showBonusPopup = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun GameWebViewContainer(onWebViewCreated: (WebView) -> Unit) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            try {
                                val cacheDir = context.cacheDir
                                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
                                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                            } catch (e: Exception) {
                                // ignore
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            try {
                                val cacheDir = context.cacheDir
                                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
                                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        mediaPlaybackRequiresUserGesture = false
                    }

                    setBackgroundColor(0xFF03030C.toInt())
                    
                    // Hook Javascript Interface
                    addJavascriptInterface(GameInterface(
                        activity = this@MainActivity,
                        onGameOver = { score, kills ->
                            finalScore = score
                            finalKills = kills
                            if (!isScoreSavedForCurrentGame) {
                                isNewHighScore = leaderboardManager.addScore(score)
                                leaderboardList = leaderboardManager.getTopScores()
                                isScoreSavedForCurrentGame = true
                                submitScoreToOnlineLeaderboard(score, kills)
                            }
                            currentScreen = GameScreen.GAMEOVER
                        },
                        onGameStarted = {
                            isScoreSavedForCurrentGame = false
                            currentScreen = GameScreen.PLAYING
                            gamesPlayedState += 1
                            val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putInt("games_played_stat", gamesPlayedState).apply()
                            AuthManager.syncProfileToFirestore(this@MainActivity)
                        },
                        getHighScore = {
                            leaderboardManager.getHighScore()
                        },
                        saveScore = { score, kills ->
                            finalKills = kills
                            if (!isScoreSavedForCurrentGame) {
                                val isNewHigh = leaderboardManager.addScore(score)
                                leaderboardList = leaderboardManager.getTopScores()
                                if (isNewHigh) {
                                    isNewHighScore = true
                                }
                                isScoreSavedForCurrentGame = true
                                submitScoreToOnlineLeaderboard(score, kills)
                            }
                        }
                    ), "AndroidGame")

                    loadUrl("file:///android_asset/index.html")
                    onWebViewCreated(this)
                }
            }
        )
    }
}

// Thread-safe WebView Native Javascript bridge class
class GameInterface(
    private val activity: MainActivity,
    private val onGameOver: (Int, Int) -> Unit,
    private val onGameStarted: () -> Unit,
    private val getHighScore: () -> Int,
    private val saveScore: (Int, Int) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun gameOver(score: Int) {
        gameOver(score, 0)
    }

    @android.webkit.JavascriptInterface
    fun gameOver(score: Int, kills: Int) {
        activity.runOnUiThread {
            onGameOver(score, kills)
        }
    }

    @android.webkit.JavascriptInterface
    fun gameStarted() {
        activity.runOnUiThread {
            onGameStarted()
        }
    }

    @android.webkit.JavascriptInterface
    fun getNativeHighScore(): Int {
        return getHighScore()
    }

    @android.webkit.JavascriptInterface
    fun saveNativeScore(score: Int) {
        saveNativeScore(score, 0)
    }

    @android.webkit.JavascriptInterface
    fun saveNativeScore(score: Int, kills: Int) {
        activity.runOnUiThread {
            saveScore(score, kills)
        }
    }

    @android.webkit.JavascriptInterface
    fun onEnemyDestroyed() {
        activity.runOnUiThread {
            activity.onEnemyDestroyed()
        }
    }

    @android.webkit.JavascriptInterface
    fun isSoundEffectsEnabled(): Boolean {
        return activity.getSoundEffectsEnabled()
    }

    @android.webkit.JavascriptInterface
    fun isMusicEnabled(): Boolean {
        return activity.getMusicEnabled()
    }

    @android.webkit.JavascriptInterface
    fun isVibrationEnabled(): Boolean {
        return activity.getVibrationEnabled()
    }

    @android.webkit.JavascriptInterface
    fun vibrate(durationMs: Int) {
        activity.vibratePhone(durationMs.toLong())
    }

    @android.webkit.JavascriptInterface
    fun getHighestLevel(): Int {
        return activity.getHighestLevel()
    }

    @android.webkit.JavascriptInterface
    fun saveHighestLevel(level: Int) {
        activity.runOnUiThread {
            activity.setHighestLevel(level)
        }
    }

    @android.webkit.JavascriptInterface
    fun getTotalCoins(): Int {
        return activity.getTotalCoins()
    }

    @android.webkit.JavascriptInterface
    fun saveTotalCoins(coins: Int) {
        activity.runOnUiThread {
            activity.setTotalCoins(coins)
        }
    }

    @android.webkit.JavascriptInterface
    fun levelComplete(coinsEarned: Int, totalCoins: Int) {
        activity.runOnUiThread {
            activity.onLevelCompleted(coinsEarned, totalCoins)
        }
    }

    @android.webkit.JavascriptInterface
    fun returnToMenu() {
        activity.runOnUiThread {
            activity.webView?.evaluateJavascript("window.showStartScreen()", null)
        }
    }

    @android.webkit.JavascriptInterface
    fun getEquippedShipId(): String {
        return activity.getEquippedShipId()
    }

    @android.webkit.JavascriptInterface
    fun getUpgradeLevel(statName: String): Int {
        return activity.getUpgradeLevel(statName)
    }

    @android.webkit.JavascriptInterface
    fun isExclusiveSkinEnabled(): Boolean {
        return activity.isExclusiveSkinEnabled()
    }

    @android.webkit.JavascriptInterface
    fun isLegendarySkinEnabled(): Boolean {
        return activity.isLegendarySkinEnabled()
    }

    @android.webkit.JavascriptInterface
    fun getPlayerRank(): String {
        return activity.leaderboardManager.getCachedRank()
    }
}

// --- Support Classes and Composable Assets for Premium Menu ---

data class StarData(
    val xPercent: Float,
    val yPercent: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float
)

data class NebulaData(
    val xPercent: Float,
    val yPercent: Float,
    val radius: Float,
    val color: Color
)

@Composable
fun StarfieldBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Starfield")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StarProgress"
    )

    val starsList = remember {
        List(80) {
            StarData(
                xPercent = Math.random().toFloat(),
                yPercent = Math.random().toFloat(),
                size = (Math.random() * 2.5 + 1).toFloat(),
                speed = (Math.random() * 0.4 + 0.1).toFloat(),
                alpha = (Math.random() * 0.5 + 0.5).toFloat()
            )
        }
    }

    val nebulaeList = remember {
        List(4) {
            NebulaData(
                xPercent = Math.random().toFloat(),
                yPercent = Math.random().toFloat(),
                radius = (Math.random() * 150 + 100).toFloat(),
                color = if (Math.random() < 0.5) Color(0x1200F0FF) else Color(0x12D000FF)
            )
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Draw ambient nebulae
        nebulaeList.forEach { neb ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(neb.color, Color.Transparent),
                    center = Offset(neb.xPercent * w, neb.yPercent * h),
                    radius = neb.radius
                ),
                radius = neb.radius,
                center = Offset(neb.xPercent * w, neb.yPercent * h)
            )
        }

        // Draw stars
        starsList.forEach { star ->
            val starX = star.xPercent * w
            val starY = ((star.yPercent + progress * star.speed) % 1.0f) * h
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size,
                center = Offset(starX, starY)
            )
        }
    }
}

@Composable
fun ParticleDustBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Dust")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DustProgress"
    )

    val dustList = remember {
        List(25) {
            StarData(
                xPercent = Math.random().toFloat(),
                yPercent = Math.random().toFloat(),
                size = (Math.random() * 5 + 3).toFloat(),
                speed = (Math.random() * 0.2 + 0.05).toFloat(),
                alpha = (Math.random() * 0.3 + 0.1).toFloat()
            )
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        dustList.forEach { dust ->
            val x = ((dust.xPercent + progress * dust.speed * 0.4f) % 1.0f) * w
            val y = ((dust.yPercent - progress * dust.speed) % 1.0f) * h
            val adjustedY = if (y < 0f) y + h else y
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (dust.size > 5f) Color(0x3300F0FF) else Color(0x33D000FF),
                        Color.Transparent
                    ),
                    center = Offset(x, adjustedY),
                    radius = dust.size * 2
                ),
                radius = dust.size * 2,
                center = Offset(x, adjustedY)
            )
        }
    }
}

@Composable
fun RotatingPlanetAndSpaceship(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "PlanetAnimation")
    
    val rotationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orbit"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Float"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .graphicsLayer {
                translationY = floatOffset
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width * 0.28f

            // Atmospheric Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00F0FF).copy(alpha = 0.45f), Color(0x00000000)),
                    center = center,
                    radius = radius * 1.6f
                ),
                radius = radius * 1.6f,
                center = center
            )

            // Planet Body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2C0B54),
                        Color(0xFF060111)
                    ),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )

            // Surface details (clipper)
            val path = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center, radius))
            }
            clipPath(path) {
                val lineSpacing = radius * 0.35f
                for (i in -2..8) {
                    val y = center.y - radius + (i * lineSpacing) + (rotationProgress * lineSpacing)
                    
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00F0FF).copy(alpha = 0.3f),
                                Color(0xFFD000FF).copy(alpha = 0.3f),
                                Color(0xFF00F0FF).copy(alpha = 0.3f)
                            )
                        ),
                        topLeft = Offset(center.x - radius, y - 4f),
                        size = Size(radius * 2f, 8f)
                    )

                    drawLine(
                        color = Color(0xFF00F0FF).copy(alpha = 0.5f),
                        start = Offset(center.x - radius, y + 10f),
                        end = Offset(center.x + radius, y + 10f),
                        strokeWidth = 1.5f
                    )
                }
            }

            // Shadow Overlay
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x99010105),
                        Color(0xFF010105)
                    ),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 1.3f
                ),
                radius = radius,
                center = center
            )

            // Orbit line
            val orbitRx = radius * 1.65f
            val orbitRy = radius * 0.42f
            drawOval(
                color = Color(0x2200F0FF),
                topLeft = Offset(center.x - orbitRx, center.y - orbitRy),
                size = Size(orbitRx * 2, orbitRy * 2),
                style = Stroke(width = 1f)
            )

            // Spaceship calculations
            val rad = Math.toRadians(orbitAngle.toDouble())
            val tiltAngle = Math.toRadians(12.0)
            val orbitXUn = orbitRx * Math.cos(rad)
            val orbitYUn = orbitRy * Math.sin(rad)
            val orbitX = (orbitXUn * Math.cos(tiltAngle) - orbitYUn * Math.sin(tiltAngle)).toFloat()
            val orbitY = (orbitXUn * Math.sin(tiltAngle) + orbitYUn * Math.cos(tiltAngle)).toFloat()
            val shipPos = Offset(center.x + orbitX, center.y + orbitY)

            val depthScale = (0.75f + (Math.sin(rad).toFloat() + 1f) * 0.25f)

            // Engine Trail Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF00A0).copy(alpha = 0.8f), Color.Transparent),
                    center = shipPos,
                    radius = 14f * depthScale
                ),
                radius = 14f * depthScale,
                center = shipPos
            )

            // Fighter Silhouette
            val shipSize = 8f * depthScale
            val shipPath = Path().apply {
                moveTo(shipPos.x, shipPos.y - shipSize)
                lineTo(shipPos.x - shipSize * 0.8f, shipPos.y + shipSize)
                lineTo(shipPos.x, shipPos.y + shipSize * 0.4f)
                lineTo(shipPos.x + shipSize * 0.8f, shipPos.y + shipSize)
                close()
            }

            val nextRad = Math.toRadians(orbitAngle.toDouble() + 2)
            val nextXUn = orbitRx * Math.cos(nextRad)
            val nextYUn = orbitRy * Math.sin(nextRad)
            val nextX = (nextXUn * Math.cos(tiltAngle) - nextYUn * Math.sin(tiltAngle)).toFloat()
            val nextY = (nextXUn * Math.sin(tiltAngle) + nextYUn * Math.cos(tiltAngle)).toFloat()
            val angleDeg = Math.toDegrees(Math.atan2((nextY - orbitY).toDouble(), (nextX - orbitX).toDouble())).toFloat()

            withTransform({
                rotate(degrees = angleDeg + 90f, pivot = shipPos)
            }) {
                drawPath(
                    path = shipPath,
                    brush = Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00A0)))
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f * depthScale,
                    center = shipPos
                )
            }
        }
    }
}

@Composable
fun LeaderboardOverlayDialog(
    topScores: List<LeaderboardEntry>,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB03030C))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.78f)
                .background(Color(0xF2090D26), shape = RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                    shape = RoundedCornerShape(24.dp)
                )
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏆 COLD FLIGHT LOGS",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00F0FF),
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x33000000), shape = RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), shape = RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (topScores.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO HIGH SCORE DATA DETECTED.\nCOMPLETE MISSIONS TO ESTABLISH FLIGHT LOGS.",
                            color = Color(0xFF8FA0DD),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(topScores) { index, entry ->
                            LeaderboardRow(rank = index + 1, entry = entry)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
fun MainMenuOverlay(
    topScores: List<LeaderboardEntry>,
    highestUnlockedLevel: Int,
    totalCoins: Int,
    selectedLevel: Int,
    onLevelSelected: (Int) -> Unit,
    onLaunchMission: () -> Unit,
    onSettingsClick: () -> Unit = {},
    soundEnabled: Boolean,
    onSoundToggled: (Boolean) -> Unit,
    equippedShipId: String,
    ownedShips: Set<String>,
    onEquipShip: (String) -> Unit,
    onBuyShip: (String, Int) -> Unit,
    onCoinShopClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    playerRank: String = "--",
    onUpgradeClick: () -> Unit = {},
    onDailyRewardsClick: () -> Unit = {},
    onAchievementsClick: () -> Unit = {},
    onEarnCoinsClick: () -> Unit = {}
) {
    var showLeaderboardPanel by remember { mutableStateOf(false) }

    val animVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animVisible.value = true
    }

    val enterAlpha by animateFloatAsState(
        targetValue = if (animVisible.value) 1f else 0f,
        animationSpec = tween(1200, easing = LinearOutSlowInEasing),
        label = "FadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03030F)),
        contentAlignment = Alignment.Center
    ) {
        // AAA-Quality Dynamic Background Layer
        StarfieldBackground()
        ParticleDustBackground()

        // Content layout with entering fade-in effect
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer { alpha = enterAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 1. TOP STATUS BAR (Profile, Coins, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Interactive Profile Avatar Button
                val activeUser = AuthManager.currentUser
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onProfileClick() }
                        .background(Color(0xFF0C1033).copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(
                                1.2.dp,
                                if (activeUser != null) Color(0xFF00F0FF) else Color(0xFFFF0080)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (activeUser != null) {
                        AsyncImage(
                            model = activeUser.photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF00F0FF), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${activeUser.name.split(" ").firstOrNull() ?: "Pilot"} ($playerRank)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text("👤 ", fontSize = 11.sp)
                        Text(
                            text = "PILOT: LOCAL",
                            color = Color(0xFFFF0080),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }



                // Right: Coins Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onCoinShopClick() }
                        .background(Color(0xFFFFD700).copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))), shape = RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "🪙 ", fontSize = 14.sp)
                    Text(
                        text = "COINS: ",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format("%04d", totalCoins),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFFFD700), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. HERO HEADER (Title and Subtitle)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Magenta holographic backdrop shadow
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF007F).copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        modifier = Modifier.offset(x = (-2).dp, y = 1.dp)
                    )
                    // Cyan holographic backdrop shadow
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF).copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        modifier = Modifier.offset(x = 2.dp, y = (-1).dp)
                    )
                    // Core white title text
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "GALACTIC DEFENSE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00F0FF),
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0x3A000000), shape = RoundedCornerShape(4.dp))
                        .border(BorderStroke(0.8.dp, Color(0x6600F0FF)), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }

            // 3. MAIN CENTERPIECE ANIMATION - Spaceship Selection Garage Carousel
            SpaceshipGarageCarousel(
                totalCoins = totalCoins,
                equippedShipId = equippedShipId,
                ownedShips = ownedShips,
                onEquipShip = onEquipShip,
                onBuyShip = onBuyShip,
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
            )

            // 4. LEVEL SECTOR SELECTION PANEL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 📺 EARN COINS Button (Neon Gold style with subtle glow)
                val earnCoinsPulse = rememberInfiniteTransition(label = "EarnCoinsPulse")
                val earnCoinsScale by earnCoinsPulse.animateFloat(
                    initialValue = 0.96f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "EarnCoinsScale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = earnCoinsScale
                            scaleY = earnCoinsScale
                        }
                        .fillMaxWidth(0.85f)
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gold subtle glow backer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .graphicsLayer {
                                scaleX = 1.02f
                                scaleY = 1.08f
                                alpha = 0.25f
                            }
                    )
                    Button(
                        onClick = onEarnCoinsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.15f),
                                        Color(0xFFFFA500).copy(alpha = 0.15f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("earn_coins_button")
                    ) {
                        Text(
                            text = "📺 EARN COINS",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Sector Difficulty (Example: SECTOR 1 : EASY)
                val (difficultyText, difficultyColor) = getDifficultyCategory(selectedLevel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "SECTOR $selectedLevel: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = difficultyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = difficultyColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Sector Selection Grid Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(Color(0x7F04071C), shape = RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0x6600F0FF), Color(0x22FFFFFF)))),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in 0 until 10) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (col in 1..5) {
                                    val lvl = row * 5 + col
                                    LevelSelectorItem(
                                        level = lvl,
                                        isUnlocked = lvl <= highestUnlockedLevel,
                                        isCompleted = lvl < highestUnlockedLevel,
                                        isSelected = lvl == selectedLevel,
                                        onClick = { onLevelSelected(lvl) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // PULSING START MISSION BUTTON (Blue -> Pink neon gradient)
                val pulseTransition = rememberInfiniteTransition(label = "StartPulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 0.98f,
                    targetValue = 1.02f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ButtonScale"
                )
                val glowAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.85f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "GlowAlpha"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .fillMaxWidth(0.85f),
                    contentAlignment = Alignment.Center
                ) {
                    // Blue -> Pink neon glow backer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00A0))),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .graphicsLayer {
                                scaleX = 1.02f
                                scaleY = 1.08f
                                alpha = glowAlpha * 0.4f
                            }
                    )

                    Button(
                        onClick = onLaunchMission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(
                                BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00A0)))),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF024673), Color(0xFF42025C))),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LAUNCH SECTOR $selectedLevel",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Bottom Navigation in One Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonWeight = 1f

                // 1. Upgrades
                CompactNavButton(
                    icon = "🔧",
                    label = "UPGRADES",
                    borderColor = Color(0xFF00FF88),
                    bgColor = Color(0xFF00FF88).copy(alpha = 0.12f),
                    onClick = onUpgradeClick,
                    modifier = Modifier.weight(buttonWeight).testTag("upgrades_button")
                )

                // 2. Daily Rewards
                CompactNavButton(
                    icon = "🎁",
                    label = "DAILY CLAIM",
                    borderColor = Color(0xFFFFD700),
                    bgColor = Color(0xFFFFD700).copy(alpha = 0.12f),
                    onClick = onDailyRewardsClick,
                    modifier = Modifier.weight(buttonWeight).testTag("daily_rewards_button")
                )

                // 3. Achievements
                CompactNavButton(
                    icon = "🏆",
                    label = "ACHIEVES",
                    borderColor = Color(0xFFFF00A0),
                    bgColor = Color(0xFFFF00A0).copy(alpha = 0.12f),
                    onClick = onAchievementsClick,
                    modifier = Modifier.weight(buttonWeight).testTag("achievements_button")
                )

                // 4. Leaders
                CompactNavButton(
                    icon = "📊",
                    label = "LEADERS",
                    borderColor = Color(0xFF00F0FF),
                    bgColor = Color(0xFF00F0FF).copy(alpha = 0.12f),
                    onClick = onLeaderboardClick,
                    modifier = Modifier.weight(buttonWeight)
                )

                // 5. Audio
                CompactNavButton(
                    icon = if (soundEnabled) "🔊" else "🔇",
                    label = if (soundEnabled) "AUDIO" else "MUTED",
                    borderColor = if (soundEnabled) Color(0xFF00F0FF) else Color(0x66FFFFFF),
                    bgColor = if (soundEnabled) Color(0xFF00F0FF).copy(alpha = 0.12f) else Color(0x11FFFFFF),
                    onClick = { onSoundToggled(!soundEnabled) },
                    modifier = Modifier.weight(buttonWeight)
                )

                // 6. Settings
                CompactNavButton(
                    icon = "⚙️",
                    label = "SETTINGS",
                    borderColor = Color(0xFF00F0FF),
                    bgColor = Color(0xFF00F0FF).copy(alpha = 0.12f),
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(buttonWeight)
                )
            }
        }

        // Leaderboard Console Overlay Modal
        if (showLeaderboardPanel) {
            LeaderboardOverlayDialog(
                topScores = topScores,
                onClose = { showLeaderboardPanel = false }
            )
        }
    }
}

@Composable
fun LevelSelectorItem(
    level: Int,
    isUnlocked: Boolean,
    isCompleted: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00F0FF) else if (isUnlocked) Color(0x4400F0FF) else Color(0x11FFFFFF)
    val backgroundBrush = if (isSelected) {
        Brush.linearGradient(listOf(Color(0xFF005577), Color(0xFF0088AA)))
    } else if (isCompleted) {
        Brush.linearGradient(listOf(Color(0x2200FF55), Color(0x4400FF55)))
    } else if (isUnlocked) {
        Brush.linearGradient(listOf(Color(0x220A0F2D), Color(0x440A0F2D)))
    } else {
        Brush.linearGradient(listOf(Color(0x11000000), Color(0x11000000)))
    }
    val textColor = if (isSelected) Color.White else if (isUnlocked) Color(0xFF8FA0DD) else Color(0x338FA0DD)

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 40.dp)
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor), shape = RoundedCornerShape(8.dp))
            .background(backgroundBrush, shape = RoundedCornerShape(8.dp))
            .then(if (isUnlocked) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = level.toString(),
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = "✓",
                            color = Color(0xFF00FF55),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        } else {
            Text(
                text = "🔒",
                fontSize = 11.sp,
                color = Color(0x44FFFFFF)
            )
        }
    }
}

@Composable
fun CompactNavButton(
    icon: String,
    label: String,
    borderColor: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(48.dp)
            .border(BorderStroke(1.dp, borderColor.copy(alpha = 0.8f)), shape = RoundedCornerShape(10.dp))
            .background(bgColor, shape = RoundedCornerShape(10.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = label,
                color = if (borderColor == Color(0x66FFFFFF)) Color(0xAAFFFFFF) else Color.White,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                letterSpacing = 0.2.sp
            )
        }
    }
}



@Composable
fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color(0xFF8FA0DD) // Normal
    }

    val rankText = when (rank) {
        1 -> "🏆 #1"
        2 -> "🥈 #2"
        3 -> "🥉 #3"
        else -> "   #$rank"
    }

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val formattedDate = sdf.format(Date(entry.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (rank <= 3) Color(0x22FFFFFF) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rankText,
                color = rankColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(60.dp)
            )
            
            Text(
                text = formattedDate,
                color = Color(0xFF8FA0DD),
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        Text(
            text = String.format("%,d PTS", entry.score),
            color = if (rank == 1) Color(0xFFFF0080) else Color(0xFF00F0FF),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    kills: Int,
    isNewHighScore: Boolean,
    topScores: List<LeaderboardEntry>,
    continuedThisGame: Boolean = false,
    onContinueClick: () -> Unit = {},
    onDeployAgain: () -> Unit,
    onReturnToHangar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20F0202)), // Dark red hue immersive overlay
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .background(Color(0xEE1D0A0A), shape = RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFFFF0055), Color(0xFFFF5500)))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MISSION FAILED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFFFF0055),
                textAlign = TextAlign.Center
            )

            Text(
                text = "SPACESHIP DESTROYED",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF9F9F),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Final score display card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x33FF0055)
                ),
                border = BorderStroke(1.dp, Color(0x66FF0055)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "FINAL SCORE",
                                fontSize = 11.sp,
                                color = Color(0xFFFF9F9F),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%,d", score),
                                fontSize = 28.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TOTAL KILLS",
                                fontSize = 11.sp,
                                color = Color(0xFFFF9F9F),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%,d", kills),
                                fontSize = 28.sp,
                                color = Color(0xFFFF0055),
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    if (isNewHighScore) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "🔥 NEW BEST HIGH SCORE! 🔥",
                            fontSize = 14.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val placeIdx = topScores.indexOfFirst { it.score == score && System.currentTimeMillis() - it.timestamp < 10000 }
                        if (placeIdx != -1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✨ PILOT RANK #${placeIdx + 1} ESTABLISHED! ✨",
                                fontSize = 13.sp,
                                color = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // High scores preview box
            Text(
                text = "TOP RANKS PREVIEW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9F9F),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x33000000), shape = RoundedCornerShape(10.dp))
                    .padding(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(topScores.take(5)) { index, entry ->
                        LeaderboardRow(rank = index + 1, entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!continuedThisGame) {
                Button(
                    onClick = onContinueClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            BorderStroke(2.dp, Color(0xFF00FF88)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF025c2e), Color(0xFF00FF88))),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("continue_mission_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📺 CONTINUE MISSION",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Deploy Again Button
            Button(
                onClick = onDeployAgain,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(
                        BorderStroke(2.dp, Color(0xFFFF0055)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF880022), Color(0xFFFF0055))),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEPLOY AGAIN",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Return to Hangar Button
            OutlinedButton(
                onClick = onReturnToHangar,
                border = BorderStroke(1.5.dp, Color(0x88FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETURN TO HANGAR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PauseMenuOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    onSettings: () -> Unit,
    playClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99020208)), // Semi-transparent dark space overlay
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xEE0A0F2D), shape = RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "MISSION PAUSED",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "PILOT TACTICAL INTERFACE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8FA0DD),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Resume Button
            Button(
                onClick = {
                    playClick()
                    onResume()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001F30))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00F0FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RESUME MISSION", color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Restart Button
            Button(
                onClick = {
                    playClick()
                    onRestart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(BorderStroke(1.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001122))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RESTART FIGHT", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Settings Button
            Button(
                onClick = {
                    playClick()
                    onSettings()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(BorderStroke(1.dp, Color(0x8800F0FF)), shape = RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001122))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SYSTEM SETTINGS", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Main Menu Button
            Button(
                onClick = {
                    playClick()
                    onMainMenu()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape = RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ABANDON TO HANGAR", color = Color.LightGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    soundEffectsEnabled: Boolean,
    onSoundEffectsChanged: (Boolean) -> Unit,
    musicEnabled: Boolean,
    onMusicChanged: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    playClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .border(
                BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                shape = RoundedCornerShape(16.dp)
            ),
        containerColor = Color(0xFB0A0F2D),
        title = {
            Text(
                text = "SYSTEM SETTINGS",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sound Effects Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "SOUND EFFECTS",
                            color = Color(0xFF00F0FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Blasters & explosions",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Switch(
                        checked = soundEffectsEnabled,
                        onCheckedChange = {
                            playClick()
                            onSoundEffectsChanged(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00F0FF),
                            checkedTrackColor = Color(0x6600F0FF),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                // Music Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "SPACE MUSIC",
                            color = Color(0xFF00F0FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Looping cosmic drone",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Switch(
                        checked = musicEnabled,
                        onCheckedChange = {
                            playClick()
                            onMusicChanged(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00F0FF),
                            checkedTrackColor = Color(0x6600F0FF),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                // Vibration Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "HAPTICS & VIBRATION",
                            color = Color(0xFF00F0FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Tactile ship damage",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            playClick()
                            onVibrationChanged(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00F0FF),
                            checkedTrackColor = Color(0x6600F0FF),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    playClick()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(BorderStroke(1.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF002233)
                )
            ) {
                Text(
                    text = "CLOSE SYSTEMS",
                    color = Color(0xFF00F0FF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

fun getDifficultyCategory(level: Int): Pair<String, Color> {
    return when (level) {
        in 1..10 -> "EASY" to Color(0xFF00FF88)
        in 11..20 -> "MEDIUM" to Color(0xFF00F0FF)
        in 21..30 -> "HARD" to Color(0xFFFFD700)
        in 31..40 -> "EXPERT" to Color(0xFFFF5500)
        else -> "EXTREME" to Color(0xFFFF0055)
    }
}

@Composable
fun LevelCompleteOverlay(
    level: Int,
    coinsEarned: Int,
    totalCoins: Int,
    onNextLevel: () -> Unit,
    onReplay: () -> Unit,
    onReturnToHangar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2020F0C)), // Dark teal/green hue immersive overlay
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .background(Color(0xEE0A1D16), shape = RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00FF88), Color(0xFF00F0FF)))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SECTOR SECURED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFF00FF88),
                textAlign = TextAlign.Center
            )

            Text(
                text = "SECTOR $level SECURED SUCCESSFULLY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFA0FFDD),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Reward Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x3300FF88)
                ),
                border = BorderStroke(1.dp, Color(0x6600FF88)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MISSION COIN REWARD",
                        fontSize = 11.sp,
                        color = Color(0xFFA0FFDD),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+$coinsEarned",
                        fontSize = 36.sp,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "TOTAL COINS: $totalCoins",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons: Next Level, Replay, Home
            if (level < 50) {
                Button(
                    onClick = onNextLevel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            BorderStroke(2.dp, Color(0xFF00FF88)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF006633), Color(0xFF00FF88))),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEXT SECTOR",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Replay Button
            Button(
                onClick = onReplay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(
                        BorderStroke(1.5.dp, Color(0xFF00F0FF)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF004466), Color(0xFF0088AA))),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REPLAY MISSION",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Return to Hangar (Home Button)
            OutlinedButton(
                onClick = onReturnToHangar,
                border = BorderStroke(1.5.dp, Color(0x88FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETURN TO HANGAR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

// --- SPACESHIP GARAGE & SELECTION MODELS AND COMPOSABLES ---

data class Spaceship(
    val id: String,
    val name: String,
    val cost: Int,
    val powerDesc: String,
    val speed: Float,       // 0.0f to 1.0f representation for HUD
    val damage: Float,      // 0.0f to 1.0f representation for HUD
    val shield: Float,      // 0.0f to 1.0f representation for HUD
    val fireRate: Float,    // 0.0f to 1.0f representation for HUD
    val engineColor: Color,
    val bulletColor: Color,
    val description: String
)

val SPACESHIPS_LIST = listOf(
    Spaceship("falcon", "Falcon Mk-I", 0, "Balanced Federation Specs", 0.5f, 0.4f, 0.5f, 0.5f, Color(0xFFFF6A00), Color(0xFF00F0FF), "Standard Federation starfighter. Highly reliable and versatile."),
    Spaceship("lightning", "Lightning X", 1000, "⚡ +20% Fire Rate & Speed boost", 0.7f, 0.4f, 0.5f, 0.8f, Color(0xFF00FFFF), Color(0xFFFFD700), "Sleek interceptor with hyper-charged capacitor cores for lightning speed."),
    Spaceship("titan", "Titan Defender", 3000, "🛡️ +40% Reinforced Shield Shell", 0.3f, 0.5f, 0.9f, 0.3f, Color(0xFF00FF00), Color(0xFF39FF14), "Heavy armored dreadnought designed to withstand devastating cosmic attacks."),
    Spaceship("phoenix", "Phoenix Blaster", 6000, "🔥 Double Bullet Kinetic Damage", 0.5f, 0.8f, 0.4f, 0.5f, Color(0xFFFF0000), Color(0xFFFF4500), "Equipped with hyper-dense plasma chargers that incinerate enemy fleets."),
    Spaceship("frost", "Frost Wing", 10000, "❄️ Emits Sub-Zero 20% Time Dilation", 0.6f, 0.5f, 0.6f, 0.5f, Color(0xFF87CEFA), Color(0xFF00BFFF), "Fires chronal freeze-pulses that delay and slow down all incoming threats."),
    Spaceship("nova", "Nova Destroyer", 16000, "💥 Explosive Kinetic Shockwave Splash", 0.4f, 0.7f, 0.7f, 0.4f, Color(0xFF8A2BE2), Color(0xFFFF00FF), "Generates collateral micro-nova blasts upon securing enemy destructions."),
    Spaceship("phantom", "Phantom Stealth", 24000, "👤 5-Second Phase-Cloak Invincibility", 0.8f, 0.5f, 0.5f, 0.6f, Color(0xFF4B0082), Color(0xFF9370DB), "Phase-shifts on launch, granting five seconds of complete invulnerability."),
    Spaceship("cosmic", "Cosmic Emperor", 40000, "👑 Legendary Golden 5-Way Bullet Spread", 0.9f, 1.0f, 0.9f, 0.9f, Color(0xFFFF1493), Color(0xFFFFEA00), "The supreme imperial flagship. Fires devastating 5-way cosmic blasters.")
)

@Composable
fun DrawSpaceshipCanvas(
    shipId: String,
    flameScale: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2

        // Flame colors
        val flameColor = when (shipId) {
            "falcon" -> Color(0xFFFF6A00)
            "lightning" -> Color(0xFF00FFFF)
            "titan" -> Color(0xFF00FF00)
            "phoenix" -> Color(0xFFFF0000)
            "frost" -> Color(0xFF87CEFA)
            "nova" -> Color(0xFF8A2BE2)
            "phantom" -> Color(0xFF4B0082)
            "cosmic" -> Color(0xFFFF1493)
            else -> Color(0xFFFF6A00)
        }
        val flameCoreColor = when (shipId) {
            "falcon" -> Color(0xFFFFEA00)
            "lightning" -> Color(0xFFFFFFFF)
            "titan" -> Color(0xFFE0FFE0)
            "phoenix" -> Color(0xFFFFD700)
            "frost" -> Color(0xFFFFFFFF)
            "nova" -> Color(0xFFFF00FF)
            "phantom" -> Color(0xFFDDA0DD)
            "cosmic" -> Color(0xFFFFD700)
            else -> Color(0xFFFFEA00)
        }

        // Draw thruster fire
        val baseFlameLen = 30f * flameScale
        val pathFlame = Path().apply {
            moveTo(cx - 12f, cy + 20f)
            lineTo(cx + 12f, cy + 20f)
            lineTo(cx, cy + 20f + baseFlameLen)
            close()
        }
        drawPath(
            path = pathFlame,
            brush = Brush.verticalGradient(listOf(flameColor, Color.Transparent))
        )
        val pathFlameCore = Path().apply {
            moveTo(cx - 6f, cy + 20f)
            lineTo(cx + 6f, cy + 20f)
            lineTo(cx, cy + 20f + baseFlameLen * 0.6f)
            close()
        }
        drawPath(
            path = pathFlameCore,
            brush = Brush.verticalGradient(listOf(flameCoreColor, Color.Transparent))
        )

        // Ship Chassis color
        val shipColor = when (shipId) {
            "falcon" -> Color(0xFF00F0FF)
            "lightning" -> Color(0xFFFFFF00)
            "titan" -> Color(0xFF00FFaa)
            "phoenix" -> Color(0xFFFF4500)
            "frost" -> Color(0xFF00BFFF)
            "nova" -> Color(0xFFFF00FF)
            "phantom" -> Color(0xFF9370DB)
            "cosmic" -> Color(0xFFFFD700)
            else -> Color(0xFF00F0FF)
        }

        when (shipId) {
            "falcon" -> {
                val body = Path().apply {
                    moveTo(cx, cy - 30f)
                    lineTo(cx - 24f, cy + 15f)
                    lineTo(cx - 12f, cy + 20f)
                    lineTo(cx + 12f, cy + 20f)
                    lineTo(cx + 24f, cy + 15f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF060A26), Color(0xFF0C1647))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 18f)
                    lineTo(cx - 5f, cy)
                    lineTo(cx + 5f, cy)
                    close()
                }
                drawPath(cockpit, color = Color.White)
            }
            "lightning" -> {
                val body = Path().apply {
                    moveTo(cx - 6f, cy - 35f)
                    lineTo(cx - 10f, cy - 10f)
                    lineTo(cx - 32f, cy - 20f)
                    lineTo(cx - 20f, cy + 15f)
                    lineTo(cx - 8f, cy + 10f)
                    lineTo(cx, cy + 22f)
                    lineTo(cx + 8f, cy + 10f)
                    lineTo(cx + 20f, cy + 15f)
                    lineTo(cx + 32f, cy - 20f)
                    lineTo(cx + 10f, cy - 10f)
                    lineTo(cx + 6f, cy - 35f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF030D1E), Color(0xFF082245))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 15f)
                    lineTo(cx - 4f, cy + 5f)
                    lineTo(cx + 4f, cy + 5f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFF00FFFF))
            }
            "titan" -> {
                val body = Path().apply {
                    moveTo(cx - 12f, cy - 25f)
                    lineTo(cx - 12f, cy - 10f)
                    lineTo(cx - 30f, cy - 5f)
                    lineTo(cx - 30f, cy + 15f)
                    lineTo(cx - 15f, cy + 22f)
                    lineTo(cx + 15f, cy + 22f)
                    lineTo(cx + 30f, cy + 15f)
                    lineTo(cx + 30f, cy - 5f)
                    lineTo(cx + 12f, cy - 10f)
                    lineTo(cx + 12f, cy - 25f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF041812), Color(0xFF0B2E24))))
                drawPath(body, color = shipColor, style = Stroke(width = 3.5f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 10f)
                    lineTo(cx - 8f, cy + 10f)
                    lineTo(cx + 8f, cy + 10f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFF39FF14))
            }
            "phoenix" -> {
                val body = Path().apply {
                    moveTo(cx, cy - 38f)
                    lineTo(cx - 12f, cy - 5f)
                    lineTo(cx - 35f, cy + 12f)
                    lineTo(cx - 15f, cy + 12f)
                    lineTo(cx - 8f, cy + 24f)
                    lineTo(cx + 8f, cy + 24f)
                    lineTo(cx + 15f, cy + 12f)
                    lineTo(cx + 35f, cy + 12f)
                    lineTo(cx + 12f, cy - 5f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF280303), Color(0xFF4C0808))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 15f)
                    lineTo(cx - 5f, cy + 5f)
                    lineTo(cx + 5f, cy + 5f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFFFFD700))
            }
            "frost" -> {
                val body = Path().apply {
                    moveTo(cx, cy - 32f)
                    lineTo(cx - 10f, cy - 12f)
                    lineTo(cx - 32f, cy + 8f)
                    lineTo(cx - 28f, cy - 8f)
                    lineTo(cx - 10f, cy + 18f)
                    lineTo(cx + 10f, cy + 18f)
                    lineTo(cx + 28f, cy - 8f)
                    lineTo(cx + 32f, cy + 8f)
                    lineTo(cx + 10f, cy - 12f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF011520), Color(0xFF03263B))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 14f)
                    lineTo(cx - 4f, cy + 3f)
                    lineTo(cx + 4f, cy + 3f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFF00BFFF))
            }
            "nova" -> {
                val body = Path().apply {
                    moveTo(cx - 5f, cy - 36f)
                    lineTo(cx - 12f, cy - 10f)
                    lineTo(cx - 34f, cy + 16f)
                    lineTo(cx - 14f, cy + 16f)
                    lineTo(cx, cy + 25f)
                    lineTo(cx + 14f, cy + 16f)
                    lineTo(cx + 34f, cy + 16f)
                    lineTo(cx + 12f, cy - 10f)
                    lineTo(cx + 5f, cy - 36f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF160120), Color(0xFF2C033E))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 16f)
                    lineTo(cx - 5f, cy + 6f)
                    lineTo(cx + 5f, cy + 6f)
                    close()
                }
                drawPath(cockpit, color = Color.White)
            }
            "phantom" -> {
                val body = Path().apply {
                    moveTo(cx, cy - 28f)
                    lineTo(cx - 36f, cy + 15f)
                    lineTo(cx - 16f, cy + 15f)
                    lineTo(cx - 8f, cy + 22f)
                    lineTo(cx + 8f, cy + 22f)
                    lineTo(cx + 16f, cy + 15f)
                    lineTo(cx + 36f, cy + 15f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF0D021A), Color(0xFF1B0533))))
                drawPath(body, color = shipColor, style = Stroke(width = 3f))

                val cockpit = Path().apply {
                    moveTo(cx, cy - 12f)
                    lineTo(cx - 4f, cy + 2f)
                    lineTo(cx + 4f, cy + 2f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFFFF00FF))
            }
            "cosmic" -> {
                val body = Path().apply {
                    moveTo(cx, cy - 42f)
                    lineTo(cx - 8f, cy - 10f)
                    lineTo(cx - 36f, cy - 5f)
                    lineTo(cx - 24f, cy + 20f)
                    lineTo(cx - 10f, cy + 12f)
                    lineTo(cx, cy + 28f)
                    lineTo(cx + 10f, cy + 12f)
                    lineTo(cx + 24f, cy + 20f)
                    lineTo(cx + 36f, cy - 5f)
                    lineTo(cx + 8f, cy - 10f)
                    close()
                }
                drawPath(body, brush = Brush.linearGradient(listOf(Color(0xFF261D02), Color(0xFF4C3D06))))
                drawPath(body, color = shipColor, style = Stroke(width = 3.5f))

                // Left & Right auxiliary hulls
                drawLine(color = shipColor, start = Offset(cx - 18f, cy - 5f), end = Offset(cx - 18f, cy - 22f), strokeWidth = 3f)
                drawLine(color = shipColor, start = Offset(cx + 18f, cy - 5f), end = Offset(cx + 18f, cy - 22f), strokeWidth = 3f)

                val cockpit = Path().apply {
                    moveTo(cx, cy - 20f)
                    lineTo(cx - 6f, cy - 2f)
                    lineTo(cx + 6f, cy - 2f)
                    close()
                }
                drawPath(cockpit, color = Color(0xFFFF4500))
            }
        }
    }
}

@Composable
fun SpaceshipGarageCarousel(
    totalCoins: Int,
    equippedShipId: String,
    ownedShips: Set<String>,
    onEquipShip: (String) -> Unit,
    onBuyShip: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember { mutableStateOf(SPACESHIPS_LIST.indexOfFirst { it.id == equippedShipId }.coerceAtLeast(0)) }
    val ship = SPACESHIPS_LIST[currentIndex]
    
    val isOwned = ownedShips.contains(ship.id)
    val isEquipped = ship.id == equippedShipId

    val scope = rememberCoroutineScope()
    var insufficientCoinsErrorShipId by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableStateOf(0) }
    
    val shakeOffset by animateDpAsState(
        targetValue = if (insufficientCoinsErrorShipId == ship.id) {
            when (shakeTrigger) {
                1 -> (-10).dp
                2 -> 10.dp
                3 -> (-8).dp
                4 -> 8.dp
                5 -> (-4).dp
                6 -> 4.dp
                else -> 0.dp
            }
        } else {
            0.dp
        },
        animationSpec = spring(dampingRatio = 0.25f, stiffness = 1200f),
        label = "Shake"
    )

    var swipeOffset by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "SpaceshipFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Float"
    )
    val flameScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlameScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper Carousel Area (Ship Display & Navigation)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Button
            IconButton(
                onClick = {
                    if (currentIndex > 0) currentIndex-- else currentIndex = SPACESHIPS_LIST.size - 1
                },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag("garage_prev_button")
            ) {
                Text(
                    text = "◀",
                    color = Color(0xFF00F0FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Ship Body Canvas Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset > 40f) {
                                    if (currentIndex > 0) currentIndex-- else currentIndex = SPACESHIPS_LIST.size - 1
                                } else if (swipeOffset < -40f) {
                                    if (currentIndex < SPACESHIPS_LIST.size - 1) currentIndex++ else currentIndex = 0
                                }
                                swipeOffset = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                swipeOffset += dragAmount
                            }
                        )
                    }
                    .graphicsLayer {
                        translationY = floatOffset
                        rotationZ = (swipeOffset / 10f).coerceIn(-15f, 15f)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background Soft Circular Glow
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(ship.engineColor.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                DrawSpaceshipCanvas(
                    shipId = ship.id,
                    flameScale = flameScale,
                    modifier = Modifier.size(90.dp)
                )

                // Lock Overlay Indicator
                if (!isOwned) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(50))
                            .align(Alignment.TopEnd)
                            .border(BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)), shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔒", fontSize = 14.sp)
                    }
                }
            }

            // Right Button
            IconButton(
                onClick = {
                    if (currentIndex < SPACESHIPS_LIST.size - 1) currentIndex++ else currentIndex = 0
                },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("garage_next_button")
            ) {
                Text(
                    text = "▶",
                    color = Color(0xFF00F0FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Space indicators for carousel index
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            SPACESHIPS_LIST.forEachIndexed { idx, item ->
                Box(
                    modifier = Modifier
                        .size(if (idx == currentIndex) 10.dp else 6.dp)
                        .background(
                            color = if (idx == currentIndex) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }

        // Bottom details (Name, Power, Stats & Action)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ship Name
            Text(
                text = ship.name.uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            // Ownership / Price Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            ) {
                if (isEquipped) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF39FF14).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFF39FF14)), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EQUIPPED",
                            color = Color(0xFF39FF14),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else if (isOwned) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00F0FF).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "OWNED",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFFFFD700)), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${ship.cost} COINS",
                            color = Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Power description
            Text(
                text = ship.powerDesc,
                color = if (isOwned) Color(0xFF00FFCC) else Color(0xFFFF4D4D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Stat bars layout
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                StatProgressBar(label = "SPD", value = ship.speed, color = Color(0xFF00E5FF))
                StatProgressBar(label = "DMG", value = ship.damage, color = Color(0xFFFF6A00))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Animated "Not enough coins" message above the button
            AnimatedVisibility(
                visible = insufficientCoinsErrorShipId == ship.id,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Text(
                    text = "Not enough coins!",
                    color = Color(0xFFFF4D4D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Action Button
            if (isEquipped) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF39FF14).copy(alpha = 0.12f),
                                    Color(0xFF00FF88).copy(alpha = 0.12f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.horizontalGradient(listOf(Color(0xFF39FF14), Color(0xFF00FF88)))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚀 EQUIPPED",
                        color = Color(0xFF39FF14),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            } else if (isOwned) {
                Button(
                    onClick = { onEquipShip(ship.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .border(BorderStroke(1.5.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF003366), Color(0xFF0077AA))), shape = RoundedCornerShape(12.dp))
                        .testTag("equip_ship_button")
                ) {
                    Text(
                        text = "EQUIP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                val canAfford = totalCoins >= ship.cost
                val isErrorActive = insufficientCoinsErrorShipId == ship.id
                
                Button(
                    onClick = {
                        if (canAfford) {
                            onBuyShip(ship.id, ship.cost)
                        } else {
                            scope.launch {
                                insufficientCoinsErrorShipId = ship.id
                                for (i in 1..6) {
                                    shakeTrigger = i
                                    delay(60)
                                }
                                shakeTrigger = 0
                                delay(1500)
                                insufficientCoinsErrorShipId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .offset(x = shakeOffset)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                if (isErrorActive) Color(0xFFFF3333)
                                else if (canAfford) Color(0xFFFFD700)
                                else Color(0xFFFF4D4D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isErrorActive) Brush.linearGradient(listOf(Color(0xFF881111), Color(0xFFBB1111)))
                            else if (canAfford) Brush.linearGradient(listOf(Color(0xFF665500), Color(0xFFCCAA00)))
                            else Brush.linearGradient(listOf(Color(0xFF441111), Color(0xFF661111))),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("buy_ship_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isErrorActive) {
                            Text(
                                text = "❌ NOT ENOUGH COINS",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(text = "🪙 ", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BUY FOR ${ship.cost} COINS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatProgressBar(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value)
                    .background(color, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWelcomeOverlay(
    onRegister: (String) -> Unit
) {
    var displayNameInput by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val isNameValid = displayNameInput.trim().length in 3..20
    val showError = hasAttemptedSubmit && !isNameValid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE603030F)),
        contentAlignment = Alignment.Center
    ) {
        StarfieldBackground()
        ParticleDustBackground()

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFA0B0F2F), shape = RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "INITIAL PILOT SETUP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Welcome to Cold Flight! Setup your neural signature record.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFFFFD700).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, Color(0xFFFFD700)), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎁 REGISTRATION BONUS: 🪙 200 COINS!",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "PILOT DISPLAY NAME",
                    color = Color(0xFF00F0FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    placeholder = { Text("Enter pilot name...", color = Color.Gray) },
                    singleLine = true,
                    isError = showError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        errorBorderColor = Color(0xFFFF0080),
                        focusedContainerColor = Color(0xFF0D1236),
                        unfocusedContainerColor = Color(0xFF0D1236)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pilot_name_input"),
                    supportingText = {
                        Text(
                            text = if (showError) "Pilot name must be between 3 and 20 characters." else "Length: 3-20 characters",
                            color = if (showError) Color(0xFFFF0080) else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                )
            }

            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (isNameValid) {
                        onRegister(displayNameInput.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(BorderStroke(1.5.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF005F73), Color(0xFF0A9396))), shape = RoundedCornerShape(10.dp))
                    .testTag("register_continue_button")
            ) {
                Text(
                    text = "ESTABLISH PILOT PROFILE",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    totalKills: Int,
    gamesPlayed: Int,
    highestLevel: Int,
    totalCoins: Int,
    onUpdateName: (String) -> Unit,
    onChangeProfilePicture: () -> Unit,
    onClose: () -> Unit,
    playerRank: String = "--"
) {
    val activeUser = AuthManager.currentUser ?: return
    
    var isEditingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(activeUser.name) }
    val isNameValid = nameInput.trim().length in 3..20
    var hasError by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(
                BorderStroke(2.dp, Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                shape = RoundedCornerShape(16.dp)
            ),
        containerColor = Color(0xFB0A0F2D),
        title = {
            Text(
                text = "PILOT PROFILE PANEL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.size(100.dp)
                ) {
                    val localPicFile = remember(activeUser.photoUrl) {
                        if (activeUser.photoUrl.isNotEmpty()) {
                            java.io.File(activeUser.photoUrl)
                        } else null
                    }

                    if (localPicFile != null && localPicFile.exists()) {
                        AsyncImage(
                            model = localPicFile,
                            contentDescription = "Pilot Avatar",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF00F0FF), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color(0xFF13173A), shape = CircleShape)
                                .border(2.dp, Color(0xFFFF0080), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🚀", fontSize = 44.sp)
                        }
                    }

                    IconButton(
                        onClick = onChangeProfilePicture,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF00F0FF), shape = CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                    ) {
                        Text("📷", fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = onChangeProfilePicture,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300F0FF)),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("change_photo_button")
                ) {
                    Text("CHANGE PHOTO", color = Color(0xFF00F0FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            isError = hasError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00F0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                errorBorderColor = Color(0xFFFF0080)
                            ),
                            modifier = Modifier.fillMaxWidth(0.9f).testTag("edit_name_input"),
                            supportingText = {
                                Text(
                                    text = if (hasError) "Must be 3-20 characters" else "Name (3-20 chars)",
                                    color = if (hasError) Color(0xFFFF0080) else Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isNameValid) {
                                        onUpdateName(nameInput.trim())
                                        isEditingName = false
                                        hasError = false
                                    } else {
                                        hasError = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("save_name_button")
                            ) {
                                Text("SAVE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    nameInput = activeUser.name
                                    isEditingName = false
                                    hasError = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("CANCEL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = activeUser.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { isEditingName = true },
                                modifier = Modifier.size(24.dp).testTag("edit_name_button")
                            ) {
                                Text("✏️", fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Player UID", activeUser.id)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "UID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("Profile", "Clipboard error", e)
                                    }
                                }
                                .background(Color(0xFF0C1033).copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.2f)), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "UID: ${activeUser.id}",
                                color = Color(0xFF00F0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📋", fontSize = 10.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x3F000000), shape = RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), shape = RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PILOT STATS RECORDED",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    
                    val rankColor = when {
                        playerRank.contains("#1") || playerRank == "1" -> Color(0xFFFFD700)
                        playerRank.contains("#2") || playerRank == "2" -> Color(0xFFC0C0C0)
                        playerRank.contains("#3") || playerRank == "3" -> Color(0xFFCD7F32)
                        else -> Color(0xFF00F0FF)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "GLOBAL RANK:", color = Color.White, fontSize = 12.sp)
                        Text(text = playerRank, color = rankColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "TOTAL KILLS:", color = Color.White, fontSize = 12.sp)
                        Text(text = "$totalKills", color = Color(0xFFFF0080), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "HIGHEST LEVEL:", color = Color.White, fontSize = 12.sp)
                        Text(text = "LEVEL $highestLevel", color = Color(0xFF00F0FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "TOTAL COINS:", color = Color.White, fontSize = 12.sp)
                        Text(text = "🪙 $totalCoins", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "GAMES PLAYED:", color = Color.White, fontSize = 12.sp)
                        Text(text = "$gamesPlayed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = "DISMISS", color = Color.White, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun BonusCoinsPopup(onCollect: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCollect,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .border(
                BorderStroke(2.dp, Color(0xFFFFD700)),
                shape = RoundedCornerShape(16.dp)
            ),
        containerColor = Color(0xFB0F140A),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🪙 REWARD RECEIVED! 🪙",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PILOT SYNC BONUS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    text = "+200 COINS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD700),
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Your first-time authentication bonus has been credited to your account! Spend these coins in the garage to purchase advanced spaceships.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCollect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("collect_bonus_button")
            ) {
                Text(
                    text = "COLLECT COINS",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    )
}

@Composable
fun DailyRewardsDialog(
    currentStreakDay: Int,
    lastClaimTime: Long,
    onClaim: () -> Unit,
    onClose: () -> Unit
) {
    val days = listOf(
        Pair(1, "100 🪙"),
        Pair(2, "150 🪙"),
        Pair(3, "200 🪙"),
        Pair(4, "250 🪙"),
        Pair(5, "300 🪙"),
        Pair(6, "400 🪙"),
        Pair(7, "🌸 NEON SKIN")
    )

    var remainingTimeStr by remember { mutableStateOf("") }
    var isEligible by remember { mutableStateOf(false) }

    LaunchedEffect(lastClaimTime) {
        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastClaimTime
            val target = 24 * 60 * 60 * 1000L
            if (elapsed >= target) {
                remainingTimeStr = "Eligible to claim now!"
                isEligible = true
            } else {
                val diff = target - elapsed
                val hours = (diff / (1000 * 60 * 60)) % 24
                val minutes = (diff / (1000 * 60)) % 60
                val seconds = (diff / 1000) % 60
                remainingTimeStr = String.format("%02d:%02d:%02d until next cargo", hours, minutes, seconds)
                isEligible = false
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF121216),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                BorderStroke(2.dp, Color(0xFFFFD700)),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("daily_rewards_dialog"),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎁 DAILY STELLAR CARGO 🎁",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Claim daily supplies to upgrade your ship!",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Streak Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Let's create rows of Day cards
                    // Row 1: Days 1-4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 1..4) {
                            val dayData = days[i - 1]
                            val isClaimed = i < currentStreakDay
                            val isCurrent = i == currentStreakDay

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isClaimed -> Color(0x3300FF88)
                                            isCurrent -> Color(0x44FFFFD7)
                                            else -> Color(0xFF1E1E24)
                                        }
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = when {
                                            isClaimed -> Color(0xFF00FF88)
                                            isCurrent -> Color(0xFFFFD700)
                                            else -> Color(0x33FFFFFF)
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "DAY $i",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color(0xFFFFD700) else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isClaimed) "✅" else dayData.second.split(" ")[0],
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color(0xFFFFD700) else Color.LightGray
                                    )
                                    if (!isClaimed && dayData.second.contains("🪙")) {
                                        Text(
                                            text = "COINS",
                                            fontSize = 7.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Days 5-7
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 5..7) {
                            val dayData = days[i - 1]
                            val isClaimed = i < currentStreakDay
                            val isCurrent = i == currentStreakDay

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(if (i == 7) 2.1f else 1f) // Day 7 is special/wider!
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isClaimed -> Color(0x3300FF88)
                                            isCurrent -> Color(0x44FFFFD7)
                                            else -> Color(0xFF1E1E24)
                                        }
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = when {
                                            isClaimed -> Color(0xFF00FF88)
                                            isCurrent -> Color(0xFFFFD700)
                                            i == 7 -> Color(0xFFFF00A0)
                                            else -> Color(0x33FFFFFF)
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "DAY $i",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color(0xFFFFD700) else if (i == 7) Color(0xFFFF00A0) else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isClaimed) "✅" else dayData.second,
                                        fontSize = if (i == 7) 10.sp else 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCurrent) Color(0xFFFFD700) else if (i == 7) Color(0xFFFF00A0) else Color.LightGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Claim consecutive daily rewards. Missing a day resets the supply chain back to Day 1!",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onClaim,
                enabled = isEligible,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    disabledContainerColor = Color(0xFF1E1E24)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("daily_reward_claim_button")
            ) {
                Text(
                    text = if (isEligible) "CLAIM DAY $currentStreakDay CARGO" else "NEXT CARGO: $remainingTimeStr",
                    color = if (isEligible) Color.Black else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_reward_close_button")
            ) {
                Text(
                    text = "CLOSE SYSTEM",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
fun UpgradeDialog(
    currentCoins: Int,
    damageLvl: Int,
    fireRateLvl: Int,
    shieldLvl: Int,
    speedLvl: Int,
    onUpgrade: (String) -> Unit,
    isExclusiveUnlocked: Boolean,
    isLegendaryUnlocked: Boolean,
    isExclusiveEnabled: Boolean,
    isLegendaryEnabled: Boolean,
    onToggleExclusive: (Boolean) -> Unit,
    onToggleLegendary: (Boolean) -> Unit,
    onWatchAdForCoins: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF101014),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(
                BorderStroke(2.dp, Color(0xFF00FF88)),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("upgrades_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛠️ SHIP SYSTEMS GARAGE",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00FF88),
                    fontFamily = FontFamily.Monospace
                )
                
                Box(
                    modifier = Modifier
                        .background(Color(0x33FFFF00), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFFD700), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🪙 $currentCoins",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: System Upgrades
                Text(
                    text = "=== ENGINE & WEAPONS UPGRADES ===",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                val stats = listOf(
                    Triple("damage", "💥 DAMAGE BOOST", damageLvl),
                    Triple("fire_rate", "⚡ PLASMA RECHARGE", fireRateLvl),
                    Triple("shield", "🛡️ DEFLECTOR SHIELD", shieldLvl),
                    Triple("speed", "🚀 THRUSTER KINETICS", speedLvl)
                )

                stats.forEach { (statId, label, level) ->
                    val multiplier = when (statId) {
                        "damage" -> 100
                        "fire_rate" -> 120
                        "shield" -> 150
                        "speed" -> 100
                        else -> 100
                    }
                    val cost = getUpgradeCostForLevel(level)
                    val isMax = level >= 10

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF16161F), shape = RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Lv. $level/10",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMax) Color(0xFFFF00A0) else Color(0xFF00FF88),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom Progress blocks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (b in 1..10) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .background(
                                            when {
                                                b <= level -> Color(0xFF00FF88)
                                                else -> Color(0xFF252530)
                                            },
                                            shape = RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onUpgrade(statId) },
                            enabled = !isMax,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMax) Color(0xFF252530) else Color(0xFF00FF88),
                                disabledContainerColor = Color(0xFF1E1E24)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .testTag("upgrade_stat_$statId")
                        ) {
                            Text(
                                text = when {
                                    isMax -> "FULLY MAXED OUT"
                                    currentCoins < cost -> "UPGRADE: $cost COINS (NEED COINS)"
                                    else -> "UPGRADE: $cost COINS"
                                },
                                color = if (isMax) Color.Gray else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Section 2: Hull Skins
                Text(
                    text = "=== HULL COSMETICS / SKINS ===",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Neon Skin Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16161F), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🌸 NEON PINK HULL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF00A0),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Unlocked via 7-day daily streak.",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }

                    if (isExclusiveUnlocked) {
                        Switch(
                            checked = isExclusiveEnabled,
                            onCheckedChange = { onToggleExclusive(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF00A0),
                                checkedTrackColor = Color(0xFF500030)
                            ),
                            modifier = Modifier.testTag("toggle_exclusive_skin")
                        )
                    } else {
                        Text(
                            text = "🔒 LOCKED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Legendary Skin Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16161F), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "👑 GOLD LEGENDARY HULL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Unlocked: Destroy 1000 total enemies.",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }

                    if (isLegendaryUnlocked) {
                        Switch(
                            checked = isLegendaryEnabled,
                            onCheckedChange = { onToggleLegendary(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFD700),
                                checkedTrackColor = Color(0xFF504000)
                            ),
                            modifier = Modifier.testTag("toggle_legendary_skin")
                        )
                    } else {
                        Text(
                            text = "🔒 LOCKED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Section 3: Extra Coins
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onWatchAdForCoins,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(BorderStroke(1.5.dp, Color(0xFFFFD700)), shape = RoundedCornerShape(10.dp))
                        .background(Color(0x22FFFFD7), shape = RoundedCornerShape(10.dp))
                        .testTag("upgrade_watch_ad_button")
                ) {
                    Text(
                        text = "📺 WATCH ARCADE AD (+200 COINS)",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("upgrade_close_button")
            ) {
                Text(
                    text = "DISMISS GARAGE",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
fun AchievementsDialog(
    completedIds: Set<String>,
    totalKills: Int,
    highestLevel: Int,
    totalCoins: Int,
    ownedShipsCount: Int,
    onClose: () -> Unit
) {
    val achievements = listOf(
        Pair("kills_100", Triple("🏆 DEVASTATOR I", "Destroyed 100 enemy ships!\nReward: 500 Coins", 100)),
        Pair("kills_500", Triple("🏆 DEVASTATOR II", "Destroyed 500 enemy ships!\nReward: 1000 Coins", 500)),
        Pair("kills_1000", Triple("👑 COSMIC CONQUEROR", "Destroyed 1000 enemy ships!\nReward: 2500 Coins", 1000)),
        Pair("level_10", Triple("🛸 SECTOR COMMANDER", "Reached Sector 10!\nReward: 300 Coins", 10)),
        Pair("level_25", Triple("🌌 GALACTIC SENTINEL", "Reached Sector 25!\nReward: 800 Coins", 25)),
        Pair("level_50", Triple("☄️ OMEGA COMMANDER", "Reached Sector 50!\nReward: 2000 Coins", 50)),
        Pair("coins_10000", Triple("💎 TREASURE HUNTER", "Amassed 10,000 total stellar credits!\nReward: 1000 Coins", 10000)),
        Pair("unlock_all_ships", Triple("🚀 LEGENDARY PILOT", "Unlocked every single spaceship!\nReward: Legendary Ship Skin", 8))
    )

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0F0F12),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(
                BorderStroke(2.dp, Color(0xFFFF00A0)),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("achievements_dialog"),
        title = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🏆 COSMIC ACHIEVEMENTS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF00A0),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Unlock milestones for extra gold & epic skins!",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(achievements) { _, item ->
                        val id = item.first
                        val title = item.second.first
                        val desc = item.second.second
                        val target = item.second.third
                        val isCompleted = completedIds.contains(id)

                        val currentProgress = when {
                            id.startsWith("kills_") -> totalKills
                            id.startsWith("level_") -> highestLevel
                            id.startsWith("coins_") -> totalCoins
                            id == "unlock_all_ships" -> ownedShipsCount
                            else -> 0
                        }

                        val progressRatio = (currentProgress.toFloat() / target.toFloat()).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16161F), shape = RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isCompleted) Color(0xFFFF00A0) else Color(0x11FFFFFF),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) Color(0xFFFF00A0) else Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = desc,
                                    fontSize = 9.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Small progress bar
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .background(Color(0xFF252530), shape = RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progressRatio)
                                                .height(4.dp)
                                                .background(
                                                    if (isCompleted) Color(0xFFFF00A0) else Color(0xFF00FF88),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                    Text(
                                        text = "$currentProgress/$target",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isCompleted) Color(0x33FF00A0) else Color(0xFF252530),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isCompleted) "🏆" else "🔒",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF00A0)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("achievements_close_button")
            ) {
                Text(
                    text = "BACK TO COMMAND",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
fun AdSimulator(
    secondsRemaining: Int,
    onSecondsTick: () -> Unit,
    onFinished: () -> Unit,
    onSkip: () -> Unit
) {
    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            onSecondsTick()
        } else {
            onFinished()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {}, // Force watching ad fully unless explicitly skipped
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60A0A0F))
                .testTag("ad_simulator_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(24.dp)
                    .border(2.dp, Color(0xFFFFD700), shape = RoundedCornerShape(16.dp))
                    .background(Color(0xFF101014), shape = RoundedCornerShape(16.dp))
                    .padding(32.dp)
            ) {
                Text(
                    text = "📺 ARCADE TRANSMISSION",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "STATION SPONSOR MESSAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Fun Retro Loading Indicator/Wheel
                CircularProgressIndicator(
                    color = Color(0xFFFFD700),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Securing stellar sponsors...",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "REWARD IN: $secondsRemaining SECONDS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00FF88),
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0xFFFF2200)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("skip_ad_button")
                ) {
                    Text(
                        text = "⏩ SKIP AD",
                        color = Color(0xFFFF2200),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementAnimatedPopup(
    title: String,
    description: String,
    coinReward: Int,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2C1035), Color(0xFF10081C))
                    )
                )
                .border(2.dp, Color(0xFFFF00A0), shape = RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🏆 ACHIEVEMENT UNLOCKED! 🏆",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF00A0),
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .background(Color(0x33FFFF00), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFFD700), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+$coinReward COINS CREDITED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF00A0)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("achievement_dismiss_button")
                ) {
                    Text(
                        text = "AFFIRMATIVE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}




