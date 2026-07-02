package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log

class BackgroundMusicManager(private val context: Context) {
    private val attributionContext: Context by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.createAttributionContext("audioPlayback")
        } else {
            context
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var proceduralSynth: ProceduralSynth? = null
    private var isMusicPlaying = false
    private var currentMusicType: MusicType? = null
    private var isEnabled = true

    private val audioManager: AudioManager by lazy {
        attributionContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var focusRequest: Any? = null // AudioFocusRequest for API 26+

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pauseOrStop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pauseOrStop()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                resumeOrStart()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return true
    }

    private fun abandonAudioFocus() {
        // No-op to avoid AppOps CONTROL_AUDIO check
    }

    private val hasAudioOutput: Boolean by lazy {
        try {
            val pm = attributionContext.packageManager
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUDIO_OUTPUT)
        } catch (e: Exception) {
            Log.e("BackgroundMusicManager", "Error checking audio output capability", e)
            true
        }
    }

    enum class MusicType {
        MENU, GAMEPLAY
    }

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (!isEnabled) {
            pauseOrStop()
        } else {
            resumeOrStart()
        }
    }

    private fun pauseOrStop() {
        try {
            mediaPlayer?.pause()
            proceduralSynth?.stop()
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e("BackgroundMusicManager", "Error pausing music", e)
        }
    }

    private fun resumeOrStart() {
        if (!isEnabled) return
        val type = currentMusicType ?: return
        if (!hasAudioOutput) {
            Log.d("BackgroundMusicManager", "No audio output feature detected, skipping resume")
            return
        }
        requestAudioFocus()
        if (mediaPlayer != null) {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.e("BackgroundMusicManager", "Error resuming MediaPlayer", e)
                startProceduralSynth(type)
            }
        } else if (proceduralSynth == null) {
            startProceduralSynth(type)
        }
    }

    fun startMenuMusic() {
        playMusic(MusicType.MENU)
    }

    fun startGameplayMusic() {
        playMusic(MusicType.GAMEPLAY)
    }

    fun stopMusic() {
        isMusicPlaying = false
        currentMusicType = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("BackgroundMusicManager", "Error stopping MediaPlayer", e)
        }
        proceduralSynth?.stop()
        proceduralSynth = null
        abandonAudioFocus()
    }

    fun pauseMusic() {
        pauseOrStop()
    }

    fun resumeMusic() {
        resumeOrStart()
    }

    fun release() {
        stopMusic()
    }

    private fun playMusic(type: MusicType) {
        if (currentMusicType == type) return
        stopMusic() // Stop any running music first
        currentMusicType = type
        isMusicPlaying = true

        if (!isEnabled) return
        if (!hasAudioOutput) {
            Log.d("BackgroundMusicManager", "No audio output feature detected, skipping playMusic")
            return
        }

        requestAudioFocus()

        val filename = when (type) {
            MusicType.MENU -> "menu_music.mp3"
            MusicType.GAMEPLAY -> "gameplay_music.mp3"
        }

        if (assetExists(filename)) {
            try {
                val afd = attributionContext.assets.openFd(filename)
                mediaPlayer = MediaPlayer().apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    }
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    isLooping = true
                    setVolume(0.40f, 0.40f)
                    prepare()
                    start()
                }
                Log.d("BackgroundMusicManager", "Playing $filename from assets")
            } catch (e: Exception) {
                Log.e("BackgroundMusicManager", "Failed to play $filename from assets, falling back to synth", e)
                startProceduralSynth(type)
            }
        } else {
            Log.d("BackgroundMusicManager", "Asset $filename not found, playing procedural space synth")
            startProceduralSynth(type)
        }
    }

    private fun startProceduralSynth(type: MusicType) {
        proceduralSynth?.stop()
        proceduralSynth = ProceduralSynth(isGameplay = (type == MusicType.GAMEPLAY)).apply {
            setVolume(0.40f)
            start()
        }
    }

    private fun assetExists(filename: String): Boolean {
        return try {
            attributionContext.assets.open(filename).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private class ProceduralSynth(private val isGameplay: Boolean) {
        private var isPlaying = false
        private var thread: Thread? = null
        private val sampleRate = 22050
        private var volume = 0.40f

        fun start() {
            if (isPlaying) return
            isPlaying = true
            thread = Thread {
                try {
                    val minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val audioTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(minBufferSize.coerceAtLeast(4096))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            minBufferSize.coerceAtLeast(4096),
                            AudioTrack.MODE_STREAM
                        )
                    }
                    audioTrack.play()

                    val bufferSize = 1024
                    val buffer = ShortArray(bufferSize)
                    var phaseBass = 0.0
                    var phaseLead = 0.0
                    
                    // G Minor chords: Gm, Eb, F, Dm
                    val chordFrequenciesMenu = arrayOf(
                        doubleArrayOf(196.00, 233.08, 293.66), // Gm
                        doubleArrayOf(155.56, 196.00, 233.08), // Eb
                        doubleArrayOf(174.61, 220.00, 261.63), // F
                        doubleArrayOf(146.83, 174.61, 220.00)  // Dm
                    )

                    // C Minor chords: Cm, Ab, Bb, G
                    val chordFrequenciesGameplay = arrayOf(
                        doubleArrayOf(130.81, 155.56, 196.00), // Cm
                        doubleArrayOf(103.83, 130.81, 155.56), // Ab
                        doubleArrayOf(116.54, 146.83, 174.61), // Bb
                        doubleArrayOf(196.00, 246.94, 293.66)  // G
                    )

                    val chords = if (isGameplay) chordFrequenciesGameplay else chordFrequenciesMenu
                    val noteDurationSamples = if (isGameplay) (sampleRate * 0.25).toInt() else (sampleRate * 0.50).toInt()
                    
                    var sampleCount = 0
                    var chordIndex = 0
                    var arpeggioIndex = 0

                    while (isPlaying) {
                        val currentChord = chords[chordIndex]
                        val currentLeadFreq = currentChord[arpeggioIndex]
                        val currentBassFreq = currentChord[0] / 2.0 // Bass line is 1 octave lower
                        
                        for (i in 0 until bufferSize) {
                            // Triangle-like wave for retro bass warmth
                            val angleBass = 2.0 * Math.PI * currentBassFreq / sampleRate
                            phaseBass += angleBass
                            if (phaseBass > 2.0 * Math.PI) phaseBass -= 2.0 * Math.PI
                            val bassVal = if (phaseBass < Math.PI) {
                                -1.0 + 2.0 * (phaseBass / Math.PI)
                            } else {
                                1.0 - 2.0 * ((phaseBass - Math.PI) / Math.PI)
                            }

                            // Smooth sine wave for melodic lead arpeggiation
                            val angleLead = 2.0 * Math.PI * currentLeadFreq / sampleRate
                            phaseLead += angleLead
                            if (phaseLead > 2.0 * Math.PI) phaseLead -= 2.0 * Math.PI
                            
                            val noteProgress = (sampleCount % noteDurationSamples).toDouble() / noteDurationSamples
                            val envelope = (1.0 - noteProgress).coerceAtLeast(0.0)
                            
                            val leadVal = Math.sin(phaseLead) * envelope

                            // Mix both channels with custom balancing (bass 40%, lead 60%)
                            val mixed = (bassVal * 0.4 + leadVal * 0.6) * volume * 32767.0
                            buffer[i] = mixed.toInt().coerceIn(-32768, 32767).toShort()

                            sampleCount++
                            if (sampleCount % noteDurationSamples == 0) {
                                arpeggioIndex = (arpeggioIndex + 1) % currentChord.size
                                if (arpeggioIndex == 0) {
                                    chordIndex = (chordIndex + 1) % chords.size
                                }
                            }
                        }
                        audioTrack.write(buffer, 0, bufferSize)
                    }

                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    Log.e("ProceduralSynth", "Error in synth thread", e)
                }
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        fun stop() {
            isPlaying = false
            thread?.interrupt()
            thread = null
        }

        fun setVolume(vol: Float) {
            this.volume = vol.coerceIn(0f, 1f)
        }
    }
}
