package com.example

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class UserProfile(
    val id: String,
    val name: String,
    val photoUrl: String,
    val email: String = "",
    val provider: String = "Custom"
)

object AuthManager {
    var currentUser by mutableStateOf<UserProfile?>(null)
    var onSyncSuccess: (() -> Unit)? = null

    // Helper to initialize and load the custom profile session
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("player_uid", null)
        val name = prefs.getString("player_name", null)
        if (uid != null && name != null) {
            val photoUrl = prefs.getString("player_profile_pic_path", "") ?: ""
            currentUser = UserProfile(
                id = uid,
                name = name,
                photoUrl = photoUrl,
                email = "",
                provider = "LocalCustom"
            )
        } else {
            currentUser = null
        }
    }

    // Process gallery picture selection, crop it to square, and save it locally
    fun cropAndSaveProfilePicture(context: Context, sourceUri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Crop to square
            val width = originalBitmap.width
            val height = originalBitmap.height
            val size = minOf(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            
            val croppedBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, x, y, size, size)
            
            // Resize to 512x512 for local profile display
            val localBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 512, 512, true)

            // Save to app local files directory
            val file = java.io.File(context.filesDir, "custom_profile_pic.jpg")
            val outputStream = java.io.FileOutputStream(file)
            localBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()

            // Compress to a highly efficient 96x96 Base64 representation to store in Firestore without heavy overhead
            val smallBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 96, 96, true)
            val base64Stream = java.io.ByteArrayOutputStream()
            smallBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, base64Stream)
            val base64Bytes = base64Stream.toByteArray()
            val base64String = android.util.Base64.encodeToString(base64Bytes, android.util.Base64.NO_WRAP)
            
            val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("player_profile_pic_path", file.absolutePath)
                .putString("player_profile_pic_base64", base64String)
                .apply()

            file.absolutePath
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to crop or save profile picture", e)
            null
        }
    }

    // Synchronize current local player profile to Firestore leaderboard collection
    fun syncProfileToFirestore(context: Context) {
        val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        val uid = prefs.getString("player_uid", null) ?: return
        val name = prefs.getString("player_name", "Pilot") ?: "Pilot"
        val base64Pic = prefs.getString("player_profile_pic_base64", null)
        
        val highestLevel = prefs.getInt("highest_unlocked_level", 1)
        val totalCoins = prefs.getInt("total_coins", 0)
        val totalKills = prefs.getInt("total_kills_stat", 0)
        val gamesPlayed = prefs.getInt("games_played_stat", 0)

        val ownedShipsStr = prefs.getString("owned_ships_csv", "falcon") ?: "falcon"
        val unlockedShipsList = ownedShipsStr.split(",").filter { it.isNotEmpty() }
        
        val damageLvl = prefs.getInt("upgrade_damage_level", 1)
        val fireRateLvl = prefs.getInt("upgrade_fire_rate", 1)
        val shieldLvl = prefs.getInt("upgrade_shield_level", 1)
        val speedLvl = prefs.getInt("upgrade_speed_level", 1)
        val shipUpgradesMap = hashMapOf(
            "damage" to damageLvl.toLong(),
            "fire_rate" to fireRateLvl.toLong(),
            "shield" to shieldLvl.toLong(),
            "speed" to speedLvl.toLong()
        )
        
        val achievementsSet = prefs.getStringSet("completed_achievements", emptySet()) ?: emptySet()
        val achievementsList = achievementsSet.toList()
        
        val dailyStreak = prefs.getInt("daily_streak_day", 1)
        val lastDailyClaimTime = prefs.getLong("last_reward_claim_time", 0L)
        
        val isExclusiveSkinUnlocked = prefs.getBoolean("exclusive_skin_unlocked", false)
        val isLegendarySkinUnlocked = prefs.getBoolean("legendary_skin_unlocked", false)
        
        val settingsSoundEffects = prefs.getBoolean("settings_sound_effects", true)
        val settingsMusic = prefs.getBoolean("settings_music", true)
        val settingsVibration = prefs.getBoolean("settings_vibration", true)

        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val docRef = db.collection("leaderboard").document(uid)
            
            val data = hashMapOf<String, Any?>(
                "uid" to uid,
                "displayName" to name,
                "highestLevel" to highestLevel.toLong(),
                "totalKills" to totalKills.toLong(),
                "coins" to totalCoins.toLong(),
                "gamesPlayed" to gamesPlayed.toLong(),
                "profilePictureBase64" to base64Pic,
                "unlockedShips" to unlockedShipsList,
                "shipUpgrades" to shipUpgradesMap,
                "achievements" to achievementsList,
                "dailyStreak" to dailyStreak.toLong(),
                "lastDailyClaimTime" to lastDailyClaimTime,
                "isExclusiveSkinUnlocked" to isExclusiveSkinUnlocked,
                "isLegendarySkinUnlocked" to isLegendarySkinUnlocked,
                "settingsSoundEffects" to settingsSoundEffects,
                "settingsMusic" to settingsMusic,
                "settingsVibration" to settingsVibration,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("AuthManager", "Firestore online profile synced successfully")
                    onSyncSuccess?.invoke()
                }
                .addOnFailureListener { e ->
                    Log.e("AuthManager", "Failed to sync Firestore online profile", e)
                }
        } catch (e: Exception) {
            Log.e("AuthManager", "Firestore error in syncProfileToFirestore", e)
        }
    }

    // Restore online profile from Firestore and merge values locally
    fun restoreProfileFromFirestore(context: Context, uid: String, onComplete: (Boolean) -> Unit = {}) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("leaderboard").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val prefs = context.getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
                        val editor = prefs.edit()

                        // Restoring data - merge remote values prioritizing larger values
                        val remoteLevel = doc.getLong("highestLevel")?.toInt() ?: 1
                        val remoteKills = doc.getLong("totalKills")?.toInt() ?: 0
                        val remoteCoins = doc.getLong("coins")?.toInt() ?: 0
                        val remoteGames = doc.getLong("gamesPlayed")?.toInt() ?: 0
                        val remoteName = doc.getString("displayName") ?: "Pilot"

                        val localCoins = prefs.getInt("total_coins", 0)
                        if (remoteCoins > localCoins) {
                            editor.putInt("total_coins", remoteCoins)
                        }
                        
                        val localLevel = prefs.getInt("highest_unlocked_level", 1)
                        if (remoteLevel > localLevel) {
                            editor.putInt("highest_unlocked_level", remoteLevel)
                        }

                        val localKills = prefs.getInt("total_kills_stat", 0)
                        if (remoteKills > localKills) {
                            editor.putInt("total_kills_stat", remoteKills)
                        }

                        val localGames = prefs.getInt("games_played_stat", 0)
                        if (remoteGames > localGames) {
                            editor.putInt("games_played_stat", remoteGames)
                        }

                        editor.putString("player_name", remoteName)

                        // Upgrades Map
                        val remoteUpgrades = doc.get("shipUpgrades") as? Map<String, Any>
                        if (remoteUpgrades != null) {
                            remoteUpgrades["damage"]?.let { editor.putInt("upgrade_damage_level", (it as? Number)?.toInt() ?: 1) }
                            remoteUpgrades["fire_rate"]?.let { editor.putInt("upgrade_fire_rate", (it as? Number)?.toInt() ?: 1) }
                            remoteUpgrades["shield"]?.let { editor.putInt("upgrade_shield_level", (it as? Number)?.toInt() ?: 1) }
                            remoteUpgrades["speed"]?.let { editor.putInt("upgrade_speed_level", (it as? Number)?.toInt() ?: 1) }
                        }

                        // Ships List
                        val remoteShips = doc.get("unlockedShips") as? List<String>
                        if (remoteShips != null && remoteShips.isNotEmpty()) {
                            editor.putString("owned_ships_csv", remoteShips.joinToString(","))
                            editor.putStringSet("owned_ships", remoteShips.toSet())
                        }

                        // Achievements
                        val remoteAchieves = doc.get("achievements") as? List<String>
                        if (remoteAchieves != null) {
                            editor.putStringSet("completed_achievements", remoteAchieves.toSet())
                        }

                        // Daily rewards
                        doc.getLong("dailyStreak")?.let { editor.putInt("daily_streak_day", it.toInt()) }
                        doc.getLong("lastDailyClaimTime")?.let { editor.putLong("last_reward_claim_time", it) }

                        // Custom skins
                        doc.getBoolean("isExclusiveSkinUnlocked")?.let { editor.putBoolean("exclusive_skin_unlocked", it) }
                        doc.getBoolean("isLegendarySkinUnlocked")?.let { editor.putBoolean("legendary_skin_unlocked", it) }

                        editor.apply()
                        init(context)
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AuthManager", "Failed to restore profile", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e("AuthManager", "Error in restoreProfileFromFirestore", e)
            onComplete(false)
        }
    }
}
