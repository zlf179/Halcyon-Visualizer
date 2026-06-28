package com.ella.music.visualizer

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ella.music.ui.player.PlayerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Full-screen terrain visualizer component
 * Ported from sonic-topography 3D terrain visualization
 */
@Composable
fun TerrainVisualizerScreen(
    audioSessionId: Int,
    isPlaying: Boolean,
    palette: PlayerPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    if (!hasPermission || audioSessionId <= 0) {
        // Permission not granted or invalid audio session
        Box(modifier = modifier.fillMaxSize())
        return
    }

    // Create renderer instance first
    val renderer = remember {
        TerrainVisualizerRenderer(context)
    }

    // Create audio engine
    val engine = remember {
        AudioVisualizerEngine(audioSessionId)
    }

    // Initialize audio engine
    LaunchedEffect(audioSessionId) {
        withContext(Dispatchers.IO) {
            engine.initialize()
        }
    }
    
    // Set up beat callback for ripples (exact match with sonic-topography)
    LaunchedEffect(engine) {
        engine.setBeatCallback { strength, mode ->
            // Random position similar to sonic-topography
            val angle = Math.random() * Math.PI * 2
            val dist = if (mode == "Kick") {
                Math.random() * 25 // Near center for kick
            } else {
                10 + Math.random() * 25 // Further out for other beats
            }
            val rx = (Math.cos(angle) * dist).toFloat()
            val rz = (Math.sin(angle) * dist).toFloat()
            
            // Pass strength directly (strength = prevSmoothedFlux * 3.0 * pulseStrength)
            // sonic-topography applies min(strength * 3.0, 4.0) in shader, we pass raw strength
            renderer.addRipple(rx, rz, strength)
            
            // Log ripple creation
            com.ella.music.visualizer.VisualizerDebugLogger.log(
                com.ella.music.visualizer.VisualizerDebugLogger.LEVEL_DEBUG,
                "BeatCallback",
                "Ripple created at ($rx, $rz) with strength=$strength"
            )
        }
    }

    // Update audio data and palette (60 FPS for responsive visualization - match sonic-topography)
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            // Reset visualizer when music is paused
            engine.reset()
            renderer.setAudioData(AudioVisualizerEngine.AudioData())
        } else {
            while (isActive) {
                val audioData = withContext(Dispatchers.IO) {
                    engine.getAudioData()
                }
                renderer.setAudioData(audioData)
                renderer.setPalette(palette)
                delay(16L) // ~60 FPS update rate (16ms interval) - match sonic-topography useFrame
            }
        }
    }

    AndroidView(
        factory = {
            GLSurfaceView(context).apply {
                // Configure for transparent background
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0) // RGBA8888 with alpha channel
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT) // Transparent surface
                setZOrderOnTop(true) // Ensure it's on top for transparency
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier.fillMaxSize()
    )

    // Cleanup on dispose
    DisposableEffect(engine) {
        onDispose {
            engine.release()
        }
    }
}