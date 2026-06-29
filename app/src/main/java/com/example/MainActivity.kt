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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

enum class GameScreen {
    MENU,
    PLAYING,
    GAMEOVER,
    LEVEL_COMPLETE
}

class MainActivity : ComponentActivity() {
    private lateinit var leaderboardManager: LeaderboardManager
    var webView: WebView? = null

    // Compose state variables
    private var currentScreen by mutableStateOf(GameScreen.MENU)
    private var finalScore by mutableStateOf(0)
    private var isNewHighScore by mutableStateOf(false)
    private var leaderboardList by mutableStateOf(listOf<LeaderboardEntry>())
    private var isScoreSavedForCurrentGame = false

    // Level, Coins, Selected Stage states
    private var highestLevelState by mutableStateOf(1)
    private var totalCoinsState by mutableStateOf(0)
    private var selectedLevel by mutableStateOf(1)
    private var coinsEarnedState by mutableStateOf(0)

    fun onLevelCompleted(coinsEarned: Int, totalCoins: Int) {
        coinsEarnedState = coinsEarned
        setTotalCoins(totalCoins)
        val nextLvl = selectedLevel + 1
        if (nextLvl > highestLevelState && nextLvl <= 50) {
            setHighestLevel(nextLvl)
        }
        currentScreen = GameScreen.LEVEL_COMPLETE
    }

    fun getHighestLevel(): Int = highestLevelState
    fun setHighestLevel(level: Int) {
        highestLevelState = level
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("highest_unlocked_level", level).apply()
    }

    fun getTotalCoins(): Int = totalCoinsState
    fun setTotalCoins(coins: Int) {
        totalCoinsState = coins
        val prefs = getSharedPreferences("cosmic_striker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("total_coins", coins).apply()
    }

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
        val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createAttributionContext("default")
        } else {
            this
        }
        val vibrator = targetContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
        highestLevelState = prefs.getInt("highest_unlocked_level", 1)
        totalCoinsState = prefs.getInt("total_coins", 0)
        selectedLevel = highestLevelState

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
                            } else if (currentScreen == GameScreen.GAMEOVER || currentScreen == GameScreen.LEVEL_COMPLETE) {
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
                                        if (selectedLevel < 50) {
                                            selectedLevel += 1
                                            isPaused = false
                                            currentScreen = GameScreen.PLAYING
                                            webView?.evaluateJavascript("window.startGame($selectedLevel)", null)
                                        }
                                    },
                                    onReplay = {
                                        isPaused = false
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
                val webViewContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ctx.createAttributionContext("default")
                } else {
                    ctx
                }
                WebView(webViewContext).apply {
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
    onSoundToggled: (Boolean) -> Unit
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
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .graphicsLayer { alpha = enterAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 1. TOP STATUS BAR (Coins indicators, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFFFFD700).copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))), shape = RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(text = "🪙 ", fontSize = 15.sp)
                    Text(
                        text = "TOTAL COINS: ",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format("%04d", totalCoins),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 2. HERO HEADER (Title and Subtitle)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Magenta holographic backdrop shadow
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF007F).copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        modifier = Modifier.offset(x = (-2).dp, y = 1.dp)
                    )
                    // Cyan holographic backdrop shadow
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF).copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        modifier = Modifier.offset(x = 2.dp, y = (-1).dp)
                    )
                    // Core white title text
                    Text(
                        text = "COSMIC STRIKER",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "GALACTIC DEFENSE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00F0FF),
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0x3A000000), shape = RoundedCornerShape(4.dp))
                        .border(BorderStroke(0.8.dp, Color(0x6600F0FF)), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // 3. MAIN CENTERPIECE ANIMATION (Planet & Space fighter)
            RotatingPlanetAndSpaceship(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // 4. LEVEL SECTOR SELECTION PANEL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "SELECT MISSION SECTOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF0080),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val (difficultyText, difficultyColor) = getDifficultyCategory(selectedLevel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "SECTOR $selectedLevel: ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = difficultyText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = difficultyColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0x7F04071C), shape = RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color(0x6600F0FF), Color(0x22FFFFFF)))),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in 0 until 10) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            }

            // 5. PULSING START MISSION BUTTON
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
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Button glow backing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00A0))),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .graphicsLayer {
                            scaleX = 1.03f
                            scaleY = 1.12f
                            alpha = glowAlpha * 0.4f
                        }
                )

                Button(
                    onClick = onLaunchMission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            BorderStroke(2.dp, Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00A0)))),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF024673), Color(0xFF42025C))),
                            shape = RoundedCornerShape(16.dp)
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
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LAUNCH SECTOR $selectedLevel",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // 6. CYBERNETIC QUICK COMMAND DOCK (Leaderboard, Sound Toggle, System Settings)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leaderboard Button
                Button(
                    onClick = { showLeaderboardPanel = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.8f)), shape = RoundedCornerShape(12.dp))
                        .background(Color(0x660A0F2D), shape = RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "🏆 LEADERS",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Sound Button Toggle
                Button(
                    onClick = { onSoundToggled(!soundEnabled) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(
                            BorderStroke(1.dp, if (soundEnabled) Color(0xFF00F0FF) else Color(0x44FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (soundEnabled) Color(0x3300F0FF) else Color(0x22000000),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = if (soundEnabled) "🔊 AUDIO: ON" else "🔇 AUDIO: OFF",
                        color = if (soundEnabled) Color.White else Color(0x88FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Settings Button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x660A0F2D), shape = RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.8f)), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System Settings",
                        tint = Color(0xFF00F0FF)
                    )
                }
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
