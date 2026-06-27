package com.example

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

enum class GameScreen {
    MENU,
    PLAYING,
    GAMEOVER
}

class MainActivity : ComponentActivity() {
    private lateinit var leaderboardManager: LeaderboardManager
    private var webView: WebView? = null

    // Compose state variables
    private var currentScreen by mutableStateOf(GameScreen.MENU)
    private var finalScore by mutableStateOf(0)
    private var isNewHighScore by mutableStateOf(false)
    private var leaderboardList by mutableStateOf(listOf<LeaderboardEntry>())
    private var isScoreSavedForCurrentGame = false

    // Sound, Music, Vibration, Pause states
    private var isPaused by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    
    private var soundEffectsEnabledState by mutableStateOf(true)
    private var musicEnabledState by mutableStateOf(true)
    private var vibrationEnabledState by mutableStateOf(true)

    fun getSoundEffectsEnabled(): Boolean = soundEffectsEnabledState
    fun getMusicEnabled(): Boolean = musicEnabledState
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

    fun playClickSound() {
        webView?.post {
            webView?.evaluateJavascript("sound.playClick()", null)
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
        soundEffectsEnabledState = prefs.getBoolean("settings_sound_effects", true)
        musicEnabledState = prefs.getBoolean("settings_music", true)
        vibrationEnabledState = prefs.getBoolean("settings_vibration", true)

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

        setContent {
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
                            } else if (currentScreen == GameScreen.GAMEOVER) {
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
                                    onLaunchMission = {
                                        isPaused = false
                                        currentScreen = GameScreen.PLAYING
                                        webView?.evaluateJavascript("window.startGame()", null)
                                    },
                                    onSettingsClick = {
                                        playClickSound()
                                        showSettingsDialog = true
                                    }
                                )
                            }
                            GameScreen.GAMEOVER -> {
                                GameOverOverlay(
                                    score = finalScore,
                                    isNewHighScore = isNewHighScore,
                                    topScores = leaderboardList,
                                    onDeployAgain = {
                                        isPaused = false
                                        currentScreen = GameScreen.PLAYING
                                        webView?.evaluateJavascript("window.startGame()", null)
                                    },
                                    onReturnToHangar = {
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
                                            .padding(top = 40.dp, end = 16.dp),
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
                                            webView?.evaluateJavascript("window.startGame()", null)
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
                    webViewClient = WebViewClient()
                    
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
                    }

                    setBackgroundColor(0xFF03030C.toInt())
                    
                    // Hook Javascript Interface
                    addJavascriptInterface(GameInterface(
                        activity = this@MainActivity,
                        onGameOver = { score ->
                            finalScore = score
                            if (!isScoreSavedForCurrentGame) {
                                isNewHighScore = leaderboardManager.addScore(score)
                                leaderboardList = leaderboardManager.getTopScores()
                                isScoreSavedForCurrentGame = true
                            }
                            currentScreen = GameScreen.GAMEOVER
                        },
                        onGameStarted = {
                            isScoreSavedForCurrentGame = false
                            currentScreen = GameScreen.PLAYING
                        },
                        getHighScore = {
                            leaderboardManager.getHighScore()
                        },
                        saveScore = { score ->
                            if (!isScoreSavedForCurrentGame) {
                                val isNewHigh = leaderboardManager.addScore(score)
                                leaderboardList = leaderboardManager.getTopScores()
                                if (isNewHigh) {
                                    isNewHighScore = true
                                }
                                isScoreSavedForCurrentGame = true
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
    private val onGameOver: (Int) -> Unit,
    private val onGameStarted: () -> Unit,
    private val getHighScore: () -> Int,
    private val saveScore: (Int) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun gameOver(score: Int) {
        activity.runOnUiThread {
            onGameOver(score)
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
        activity.runOnUiThread {
            saveScore(score)
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
}

@Composable
fun MainMenuOverlay(
    topScores: List<LeaderboardEntry>,
    onLaunchMission: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE020208)), // Deep immersive dark space blur backing
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .background(Color(0xAA0A0F2D), shape = RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF0080)))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "COSMIC STRIKER",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            
            Text(
                text = "GALACTIC DEFENSE FORCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8FA0DD),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Leaderboard Box
            Text(
                text = "TOP PILOTS LEADERBOARD",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F0FF),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x55000000), shape = RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape = RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                if (topScores.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO FLIGHT LOGS FOUND\nBE THE FIRST HERO!",
                            color = Color(0xFF8FA0DD),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(topScores) { index, entry ->
                            LeaderboardRow(rank = index + 1, entry = entry)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Play Button
            Button(
                onClick = onLaunchMission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .border(
                        BorderStroke(2.dp, Color(0xFF00F0FF)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF005577), Color(0xFF00AAAA))),
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAUNCH MISSION",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // Floating Settings Gear at the top right of the screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, end = 24.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xAA0A0F2D), shape = RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0xFF00F0FF)), shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "System Settings",
                    tint = Color(0xFF00F0FF)
                )
            }
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
    isNewHighScore: Boolean,
    topScores: List<LeaderboardEntry>,
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
                        fontSize = 36.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    if (isNewHighScore) {
                        Spacer(modifier = Modifier.height(8.dp))
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
