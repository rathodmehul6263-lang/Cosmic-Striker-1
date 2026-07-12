package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseVerifyTest {

    @Test
    fun verifyFirebaseIntegration() = runBlocking {
        val resultFile = File("firebase_verification_result.txt")
        val log = StringBuilder()
        fun logLine(line: String) {
            println(line)
            log.append(line).append("\n")
            resultFile.writeText(log.toString())
        }

        logLine("=== FIREBASE VERIFICATION START ===")
        try {
            // Step 1: Check google-services.json
            var jsonFile = File("google-services.json")
            if (!jsonFile.exists()) {
                jsonFile = File("app/google-services.json")
            }
            if (!jsonFile.exists()) {
                throw IllegalStateException("google-services.json is missing in both current directory and /app folder! Current dir: " + File(".").absolutePath)
            }
            logLine("1. google-services.json is present at: " + jsonFile.absolutePath)
            val jsonContent = jsonFile.readText()
            if (!jsonContent.contains("cosmic-striker-production")) {
                logLine("WARNING: google-services.json content does not contain project ID 'cosmic-striker-production'!")
            } else {
                logLine("   Project ID from google-services.json matches: cosmic-striker-production")
            }

            // Step 2: Initialize Firebase
            val context = ApplicationProvider.getApplicationContext<Context>()
            val firebaseApp = FirebaseApp.initializeApp(context)
            if (firebaseApp == null) {
                throw IllegalStateException("FirebaseApp.initializeApp returned null!")
            }
            logLine("2. Firebase.initializeApp() ran successfully.")
            
            val projectId = firebaseApp.options.projectId
            logLine("   Firebase Options Project ID: $projectId")

            // Step 3: Firebase Authentication
            val auth = FirebaseAuth.getInstance()
            logLine("3. Firebase Authentication instance obtained successfully.")

            // Step 4: Anonymous sign-in
            logLine("   Initiating anonymous sign-in...")
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user
            if (user == null) {
                throw IllegalStateException("Anonymous sign-in succeeded but returned null user!")
            }
            logLine("4. Anonymous sign-in succeeded.")
            
            val uid = user.uid
            logLine("   Authenticated UID: $uid")

            // Step 5: Upload a test document to "leaderboard"
            val db = FirebaseFirestore.getInstance()
            logLine("5. Firebase Firestore instance obtained.")

            val testDocId = "verification_test_" + System.currentTimeMillis()
            val testData = hashMapOf(
                "uid" to uid,
                "playerName" to "Firebase Verifier UI-Test",
                "displayName" to "Firebase Verifier UI-Test",
                "kills" to 42,
                "score" to 9999,
                "level" to 1,
                "highestLevel" to 1,
                "coins" to 200,
                "timestamp" to System.currentTimeMillis(),
                "isTestVerification" to true
            )

            logLine("   Uploading test document to 'leaderboard' with ID: $testDocId...")
            db.collection("leaderboard").document(testDocId).set(testData).await()
            logLine("6. Test document uploaded successfully to collection 'leaderboard'.")

            // Step 6: Read the same document back
            logLine("   Reading test document back from 'leaderboard' with ID: $testDocId...")
            val docSnapshot = db.collection("leaderboard").document(testDocId).get().await()
            if (!docSnapshot.exists()) {
                throw IllegalStateException("Test document was uploaded but could not be found when reading back!")
            }
            
            val scoreRead = docSnapshot.getLong("score")
            val nameRead = docSnapshot.getString("playerName")
            logLine("7. Read same document back successfully.")
            logLine("   Read Data - playerName: $nameRead, score: $scoreRead")

            logLine("=== ALL CHECKS PASSED SUCCESSFULLY ===")
            logLine("STATUS: SUCCESS")
            
        } catch (e: Throwable) {
            logLine("\n!!! EXCEPTION ENCOUNTERED !!!")
            logLine(e.stackTraceToString())
            logLine("STATUS: FAILURE")
        }
    }
}
