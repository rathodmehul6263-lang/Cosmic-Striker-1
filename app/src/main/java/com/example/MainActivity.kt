package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge layout for immersive full screen gameplay
        enableEdgeToEdge()
        
        // Hide Android System Bars (Status and Navigation) to provide true immersive arcade view
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        setContent {
            MyApplicationTheme {
                SpaceShooterGameScreen()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpaceShooterGameScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Use default WebViewClient to open links inside the app rather than external browser
                webViewClient = WebViewClient()
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    
                    // Visual optimization settings for canvas performance and proper scaling
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    
                    // Hardware rendering acceleration configurations
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                
                // Clear background so Canvas drawing elements show beautifully without flickering
                setBackgroundColor(0xFF03030C.toInt())
                
                // Load local assets single-file HTML5 game
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}
