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
}
