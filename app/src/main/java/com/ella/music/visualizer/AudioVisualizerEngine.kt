package com.ella.music.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

//region debug-point audio-engine
/**
 * Audio analysis engine for terrain visualizer
 * Ported from sonic-topography AudioEngine.ts
 */
class AudioVisualizerEngine(
    private val audioSessionId: Int
) {
    companion object {
        private const val TAG = "AudioVisualizerEngine"
        private const val FFT_SIZE = 1024 // 512 bins
        private const val LOG_COMPONENT = "AudioEngine"
    }
//endregion debug-point audio-engine

    private var visualizer: Visualizer? = null
    private val fftBuffer = ByteArray(FFT_SIZE)

    // Smoothed audio data (similar to sonic-topography)
    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedEnergy = 0f
    private var smoothedSubBass = 0f
    private var smoothedLowMid = 0f
    private var smoothedHighMid = 0f
    private var smoothedPresence = 0f
    private var smoothedBrilliance = 0f
    private var smoothedAir = 0f
    private var smoothedWarmth = 0f
    private var smoothedBrightness = 0f
    private var smoothedSharpness = 0f
    private var smoothedSmoothness = 0f
    private var smoothedDensity = 0f
    private var prevBrightness = 0f

    // Previous FFT data for flux calculation
    private val prevFftData = FloatArray(FFT_SIZE / 2) { 0f }

    // Beat detection for ripples (similar to sonic-topography)
    private var onBeatDetected: ((strength: Float, mode: String) -> Unit)? = null
    
    // Flux history for adaptive threshold (match sonic-topography: 40 elements)
    private val fluxHistory = FloatArray(40) { 0f }
    private var fluxHistoryIndex = 0
    private var prevSmoothedFlux = 0f
    private var beatHold = 0
    private val beatCooldown = 30 // frames (0.5s for more frequent triggers - was 60/1s)
    private val pulseStrength = 0.5f // Increased from 0.2 for stronger ripples
    private val sensitivity = 0.15f // Match sonic-topography
    
    fun setBeatCallback(callback: (strength: Float, mode: String) -> Unit) {
        onBeatDetected = callback
    }

    data class AudioData(
        val bass: Float = 0f,
        val mid: Float = 0f,
        val treble: Float = 0f,
        val energy: Float = 0f,
        val subBass: Float = 0f,
        val lowMid: Float = 0f,
        val highMid: Float = 0f,
        val presence: Float = 0f,
        val brilliance: Float = 0f,
        val air: Float = 0f,
        val warmth: Float = 0f,
        val brightness: Float = 0f,
        val sharpness: Float = 0f,
        val smoothness: Float = 0f,
        val density: Float = 0f
    )

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        //region debug-point audio-init
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "AudioEngine initialize started: audioSessionId=$audioSessionId")
        
        if (audioSessionId <= 0) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, "Invalid audio session ID: $audioSessionId")
            Log.w(TAG, "Invalid audio session ID: $audioSessionId")
            return@withContext false
        }

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(FFT_SIZE)
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                enabled = true
            }
            
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, 
                "Visualizer created: captureSize=${visualizer?.captureSize}, enabled=${visualizer?.enabled}")
            Log.d(TAG, "AudioVisualizerEngine initialized successfully")
            true
        } catch (e: Exception) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, 
                "Failed to initialize Visualizer: ${e.message}")
            Log.e(TAG, "Failed to initialize Visualizer", e)
            false
        }
        //endregion debug-point audio-init
    }

    private var audioDataCallCount = 0L
    suspend fun getAudioData(): AudioData = withContext(Dispatchers.IO) {
        //region debug-point audio-data
        audioDataCallCount++
        
        val viz = visualizer
        if (viz == null || !viz.enabled) {
            if (audioDataCallCount % 20 == 1L) { // Log every 20 calls (~1 second at 50ms interval)
                VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_WARN, LOG_COMPONENT, 
                    "getAudioData: visualizer null or disabled (viz=$viz, enabled=${viz?.enabled})")
            }
            return@withContext AudioData()
        }

        try {
            if (viz.getFft(fftBuffer) != Visualizer.SUCCESS) {
                if (audioDataCallCount % 20 == 1L) {
                    VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_WARN, LOG_COMPONENT, "getAudioData: FFT capture failed")
                }
                return@withContext AudioData(
                    bass = smoothedBass,
                    mid = smoothedMid,
                    treble = smoothedTreble,
                    energy = smoothedEnergy,
                    subBass = smoothedSubBass,
                    lowMid = smoothedLowMid,
                    highMid = smoothedHighMid,
                    presence = smoothedPresence,
                    brilliance = smoothedBrilliance,
                    air = smoothedAir,
                    warmth = smoothedWarmth,
                    brightness = smoothedBrightness,
                    sharpness = smoothedSharpness,
                    smoothness = smoothedSmoothness,
                    density = smoothedDensity
                )
            }

            // Process FFT data similar to sonic-topography
            val binCount = fftBuffer.size / 2
            var energySum = 0f
            var subBassSum = 0f
            var bassSum = 0f
            var lowMidSum = 0f
            var midSum = 0f
            var highMidSum = 0f
            var presenceSum = 0f
            var brillianceSum = 0f
            var airSum = 0f
            var jumpVolatilitySum = 0f
            
            // Flux calculation for beat detection (Kick band: 0-18 bins)
            var fluxKick = 0f
            val kickBandStart = 0
            val kickBandEnd = 18

            for (i in 0 until binCount) {
                val real = fftBuffer[i * 2].toFloat()
                val imag = fftBuffer[i * 2 + 1].toFloat()
                val magnitude = sqrt(real * real + imag * imag)
                val normalized = magnitude / 255f

                energySum += normalized

                val prevVal = prevFftData[i]
                jumpVolatilitySum += kotlin.math.abs(normalized - prevVal)
                
                // Flux for kick detection (positive changes in low frequencies - EXACT match sonic-topography: 0-16)
                if (i >= 0 && i <= 16) { // bandEnd = 16 (not 18!)
                    val diff = normalized - prevVal
                    if (diff > 0) fluxKick += diff
                }
                
                prevFftData[i] = normalized

                // Frequency band mapping (similar to sonic-topography)
                when {
                    i <= 1 -> subBassSum += normalized
                    i <= 3 -> bassSum += normalized
                    i <= 7 -> lowMidSum += normalized
                    i <= 18 -> midSum += normalized
                    i <= 46 -> highMidSum += normalized
                    i <= 93 -> presenceSum += normalized
                    i <= 186 -> brillianceSum += normalized
                    else -> airSum += normalized
                }
            }

            val energy = energySum / binCount
            
            // Beat detection (adaptive threshold - match sonic-topography)
            val smoothedFlux = fluxKick * 0.4f + prevSmoothedFlux * 0.6f // Match sonic-topography: 0.4
            
            // Add to flux history
            fluxHistory[fluxHistoryIndex] = smoothedFlux
            fluxHistoryIndex = (fluxHistoryIndex + 1) % fluxHistory.size
            
            // Calculate adaptive threshold (exact match with sonic-topography)
            val avgFlux = fluxHistory.average().toFloat()
            var fluxVariance = 0f
            for (f in fluxHistory) {
                fluxVariance += (f - avgFlux) * (f - avgFlux)
            }
            fluxVariance /= fluxHistory.size
            val fluxStdDev = sqrt(fluxVariance)
            
            // thresholdMultiplier = max(0.1, 5.0 - sensitivity * 4.0) with sensitivity = 0.15
            val thresholdMultiplier = max(0.1f, 5.0f - sensitivity * 4.0f)
            val adaptiveThreshold = max(0.05f, avgFlux + fluxStdDev * thresholdMultiplier)
            
            // Check for peak (exact match with sonic-topography)
            val isPeak = prevSmoothedFlux > adaptiveThreshold && prevSmoothedFlux >= smoothedFlux
            
            if (beatHold > 0) {
                beatHold--
            } else if (isPeak && prevSmoothedFlux - smoothedFlux > 0.0001f) {
                // Beat detected! Trigger ripple (exact strength formula)
                val strength = prevSmoothedFlux * 3.0f * pulseStrength
                onBeatDetected?.invoke(strength, "Kick")
                beatHold = beatCooldown
                
                // Log every beat for debugging
                VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, 
                    "Beat detected! flux=$prevSmoothedFlux, threshold=$adaptiveThreshold, strength=$strength")
            } else {
                // Log flux values periodically (every 40 frames)
                if (audioDataCallCount % 40 == 1L) {
                    VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, 
                        "Flux: smoothed=$smoothedFlux, prev=$prevSmoothedFlux, threshold=$adaptiveThreshold, avgFlux=$avgFlux, fluxStdDev=$fluxStdDev")
                }
            }
            
            prevSmoothedFlux = smoothedFlux

            // Average amplitudes per band
            val subBass = subBassSum / 2f
            val bass = bassSum / 2f
            val lowMid = lowMidSum / 4f
            val mid = midSum / 11f
            val highMid = highMidSum / 28f
            val presence = presenceSum / 47f
            val brilliance = brillianceSum / 93f
            val air = airSum / 186f

            // Legacy mapping for compatibility
            val oldBass = (subBassSum + bassSum + lowMidSum) / 8f
            val oldMid = (midSum + highMidSum) / 39f
            val oldTreble = (presenceSum + brillianceSum + airSum) / 326f

            // Timbral Metrics (similar to sonic-topography)
            val warmth = if (energySum > 0f) {
                (subBassSum + bassSum + lowMidSum + midSum) / energySum
            } else 0f

            val brightness = if (energySum > 0f) {
                (presenceSum + brillianceSum + airSum) / energySum
            } else 0f

            val sharpness = max(0f, brightness - prevBrightness) * 10f
            prevBrightness = brightness

            val smoothnessVal = max(0f, 1.0f - (jumpVolatilitySum / binCount) * 2.0f)

            val activeThreshold = energy * 1.5f
            var activeBands = 0
            if (subBass > activeThreshold) activeBands++
            if (bass > activeThreshold) activeBands++
            if (lowMid > activeThreshold) activeBands++
            if (mid > activeThreshold) activeBands++
            if (highMid > activeThreshold) activeBands++
            if (presence > activeThreshold) activeBands++
            if (brilliance > activeThreshold) activeBands++
            if (air > activeThreshold) activeBands++
            val density = activeBands / 8f

            // Exponential smoothing (similar to sonic-topography)
            val dt = if (energySum > 0f) 0.15f else 0.08f

            smoothedBass += (oldBass - smoothedBass) * dt
            smoothedMid += (oldMid - smoothedMid) * dt
            smoothedTreble += (oldTreble - smoothedTreble) * dt
            smoothedEnergy += (energy - smoothedEnergy) * dt

            smoothedSubBass += (subBass - smoothedSubBass) * dt
            smoothedLowMid += (lowMid - smoothedLowMid) * dt
            smoothedHighMid += (highMid - smoothedHighMid) * dt
            smoothedPresence += (presence - smoothedPresence) * dt
            smoothedBrilliance += (brilliance - smoothedBrilliance) * dt
            smoothedAir += (air - smoothedAir) * dt

            smoothedWarmth += (warmth - smoothedWarmth) * dt
            smoothedBrightness += (brightness - smoothedBrightness) * dt
            smoothedSharpness += (sharpness - smoothedSharpness) * dt
            smoothedSmoothness += (smoothnessVal - smoothedSmoothness) * dt
            smoothedDensity += (density - smoothedDensity) * dt

            val result = AudioData(
                bass = smoothedBass,
                mid = smoothedMid,
                treble = smoothedTreble,
                energy = smoothedEnergy,
                subBass = smoothedSubBass,
                lowMid = smoothedLowMid,
                highMid = smoothedHighMid,
                presence = smoothedPresence,
                brilliance = smoothedBrilliance,
                air = smoothedAir,
                warmth = smoothedWarmth,
                brightness = smoothedBrightness,
                sharpness = smoothedSharpness,
                smoothness = smoothedSmoothness,
                density = smoothedDensity
            )
            
            // Log audio data periodically
            if (audioDataCallCount % 20 == 1L) {
                VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, 
                    "AudioData: energy=${result.energy}, bass=${result.bass}, mid=${result.mid}")
            }
            
            //endregion debug-point audio-data
            result
        } catch (e: Exception) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, 
                "Error getting FFT data: ${e.message}")
            Log.e(TAG, "Error getting FFT data", e)
            AudioData(
                bass = smoothedBass,
                mid = smoothedMid,
                treble = smoothedTreble,
                energy = smoothedEnergy,
                subBass = smoothedSubBass,
                lowMid = smoothedLowMid,
                highMid = smoothedHighMid,
                presence = smoothedPresence,
                brilliance = smoothedBrilliance,
                air = smoothedAir,
                warmth = smoothedWarmth,
                brightness = smoothedBrightness,
                sharpness = smoothedSharpness,
                smoothness = smoothedSmoothness,
                density = smoothedDensity
            )
        }
    }

    fun release() {
        //region debug-point audio-release
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "AudioEngine releasing")
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "AudioEngine released successfully")
            Log.d(TAG, "AudioVisualizerEngine released")
        } catch (e: Exception) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, 
                "Error releasing Visualizer: ${e.message}")
            Log.e(TAG, "Error releasing Visualizer", e)
        }
        //endregion debug-point audio-release
    }
    
    /**
     * Reset smoothed values when music is paused
     * This allows the visualizer to gradually return to idle state
     */
    fun reset() {
        smoothedBass = 0f
        smoothedMid = 0f
        smoothedTreble = 0f
        smoothedEnergy = 0f
        smoothedSubBass = 0f
        smoothedLowMid = 0f
        smoothedHighMid = 0f
        smoothedPresence = 0f
        smoothedBrilliance = 0f
        smoothedAir = 0f
        smoothedWarmth = 0f
        smoothedBrightness = 0f
        smoothedSharpness = 0f
        smoothedSmoothness = 0f
        smoothedDensity = 0f
        prevBrightness = 0f
        
        for (i in prevFftData.indices) {
            prevFftData[i] = 0f
        }
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Audio data reset (music paused)")
    }
}