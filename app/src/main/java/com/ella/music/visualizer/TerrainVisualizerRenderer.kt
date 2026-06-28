package com.ella.music.visualizer

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.ella.music.ui.player.PlayerPalette
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

//region debug-point renderer-init
/**
 * OpenGL ES renderer for 3D terrain visualizer
 * Ported from sonic-topography MapScene.tsx and CustomShaderMaterial.ts
 */
class TerrainVisualizerRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "TerrainVisualizerRenderer"
        private const val GRID_SIZE_X = 160 // Width
        private const val GRID_SIZE_Y = 80 // Height (total = 12800)
        private const val GRID_SPACING = 1.05f
        private const val MAX_RIPPLES = 10
        private const val LOG_COMPONENT = "Renderer"
    }
//endregion debug-point renderer-init

    private var programHandle = 0
    private var positionHandle = 0
    private var instancePosHandle = 0
    private var timeHandle = 0

    // Matrix handles
    private var projectionMatrixHandle = 0
    private var viewMatrixHandle = 0
    private var modelMatrixHandle = 0

    // Frequency uniforms
    private var subBassHandle = 0
    private var bassHandle = 0
    private var lowMidHandle = 0
    private var midHandle = 0
    private var highMidHandle = 0
    private var presenceHandle = 0
    private var brillianceHandle = 0
    private var airHandle = 0
    private var warmthHandle = 0
    private var brightnessHandle = 0
    private var sharpnessHandle = 0
    private var smoothnessHandle = 0
    private var densityHandle = 0
    private var energyHandle = 0

    // Color uniforms
    private var baseColor1Handle = 0
    private var baseColor2Handle = 0
    private var coolCoreHandle = 0
    private var coolEdgeHandle = 0
    private var warmCoreHandle = 0
    private var warmEdgeHandle = 0
    private var glowIntensityHandle = 0

    // Ripple uniforms (manual expansion for OpenGL ES 2.0)
    private val ripplePosHandles = IntArray(MAX_RIPPLES)
    private val rippleTimeHandles = IntArray(MAX_RIPPLES)
    private val rippleStrengthHandles = IntArray(MAX_RIPPLES)
    private val rippleActiveHandles = IntArray(MAX_RIPPLES)

    private val gridPositionsBuffer: FloatBuffer
    private val gridCount: Int

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var startTime = System.currentTimeMillis()
    private var rotationAngle = 0f
    private var lastAudioData: AudioVisualizerEngine.AudioData? = null
    private var palette: PlayerPalette? = null

    // Ripples (similar to sonic-topography)
    private val ripples = Array(MAX_RIPPLES) {
        RippleData()
    }
    private var rippleIndex = 0

    data class RippleData(
        var x: Float = 0f,
        var y: Float = 0f,
        var time: Float = -100f,
        var strength: Float = 0f,
        var active: Int = 0
    )

    init {
        //region debug-point grid-init
        VisualizerDebugLogger.initialize(context)
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Renderer init started")
        
        gridCount = GRID_SIZE_X * GRID_SIZE_Y
        val gridPositions = FloatArray(gridCount * 2)

        val offsetX = (GRID_SIZE_X * GRID_SPACING) / 2f
        val offsetZ = (GRID_SIZE_Y * GRID_SPACING) / 2f
        var index = 0
        for (x in 0 until GRID_SIZE_X) {
            for (z in 0 until GRID_SIZE_Y) {
                val px = x * GRID_SPACING - offsetX
                val pz = z * GRID_SPACING - offsetZ
                gridPositions[index++] = px
                gridPositions[index++] = pz
            }
        }
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Grid created: $gridCount points (X=$GRID_SIZE_X, Y=$GRID_SIZE_Y)")

        // Convert FloatArray to FloatBuffer for OpenGL ES
        gridPositionsBuffer = ByteBuffer.allocateDirect(gridPositions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        gridPositionsBuffer.put(gridPositions)
        gridPositionsBuffer.position(0)
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "FloatBuffer created, size=${gridPositions.size * 4} bytes")

        // Initialize matrices
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Renderer init completed successfully")
        //endregion debug-point grid-init
    }

    fun setAudioData(data: AudioVisualizerEngine.AudioData) {
        lastAudioData = data
    }

    fun setPalette(newPalette: PlayerPalette) {
        palette = newPalette
    }

    fun addRipple(x: Float, y: Float, strength: Float) {
        val idx = rippleIndex
        ripples[idx].x = x
        ripples[idx].y = y
        ripples[idx].time = (System.currentTimeMillis() - startTime) / 1000f
        ripples[idx].strength = strength
        ripples[idx].active = 1
        rippleIndex = (idx + 1) % MAX_RIPPLES
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        //region debug-point surface-created
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "onSurfaceCreated started")
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "GL context configured (transparent background)")

        // Load and compile shaders (use OpenGL ES 2.0 compatible version)
        val vertexShaderSource = loadShaderFromAssets("visualizer_vertex_shader_es2.glsl")
        val fragmentShaderSource = loadShaderFromAssets("visualizer_fragment_shader.glsl")
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Shader sources loaded: vertex=${vertexShaderSource.length} chars, fragment=${fragmentShaderSource.length} chars")
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        if (vertexShader == 0 || fragmentShader == 0) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, "Shader compilation FAILED: vertex=$vertexShader, fragment=$fragmentShader")
            Log.e(TAG, "Failed to load shaders")
            return
        }
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Shaders compiled successfully: vertex=$vertexShader, fragment=$fragmentShader")

        // Create program
        programHandle = GLES20.glCreateProgram()
        GLES20.glAttachShader(programHandle, vertexShader)
        GLES20.glAttachShader(programHandle, fragmentShader)
        GLES20.glLinkProgram(programHandle)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val errorLog = GLES20.glGetProgramInfoLog(programHandle)
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, "Program link FAILED: $errorLog")
            Log.e(TAG, "Failed to link program: $errorLog")
            GLES20.glDeleteProgram(programHandle)
            return
        }
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Program linked successfully: programHandle=$programHandle")

        // Get attribute handles
        positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        instancePosHandle = GLES20.glGetAttribLocation(programHandle, "aInstancePos")
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Attribute handles: positionHandle=$positionHandle, instancePosHandle=$instancePosHandle")

        // Get uniform handles
        timeHandle = GLES20.glGetUniformLocation(programHandle, "uTime")

        // Matrix handles
        projectionMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uProjectionMatrix")
        viewMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uViewMatrix")
        modelMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uModelMatrix")
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Matrix handles: projection=$projectionMatrixHandle, view=$viewMatrixHandle, model=$modelMatrixHandle")
        //endregion debug-point surface-created

        subBassHandle = GLES20.glGetUniformLocation(programHandle, "uSubBass")
        bassHandle = GLES20.glGetUniformLocation(programHandle, "uBass")
        lowMidHandle = GLES20.glGetUniformLocation(programHandle, "uLowMid")
        midHandle = GLES20.glGetUniformLocation(programHandle, "uMid")
        highMidHandle = GLES20.glGetUniformLocation(programHandle, "uHighMid")
        presenceHandle = GLES20.glGetUniformLocation(programHandle, "uPresence")
        brillianceHandle = GLES20.glGetUniformLocation(programHandle, "uBrilliance")
        airHandle = GLES20.glGetUniformLocation(programHandle, "uAir")
        warmthHandle = GLES20.glGetUniformLocation(programHandle, "uWarmth")
        brightnessHandle = GLES20.glGetUniformLocation(programHandle, "uBrightness")
        sharpnessHandle = GLES20.glGetUniformLocation(programHandle, "uSharpness")
        smoothnessHandle = GLES20.glGetUniformLocation(programHandle, "uSmoothness")
        densityHandle = GLES20.glGetUniformLocation(programHandle, "uDensity")
        energyHandle = GLES20.glGetUniformLocation(programHandle, "uEnergy")

        baseColor1Handle = GLES20.glGetUniformLocation(programHandle, "uBaseColor1")
        baseColor2Handle = GLES20.glGetUniformLocation(programHandle, "uBaseColor2")
        coolCoreHandle = GLES20.glGetUniformLocation(programHandle, "uCoolCore")
        coolEdgeHandle = GLES20.glGetUniformLocation(programHandle, "uCoolEdge")
        warmCoreHandle = GLES20.glGetUniformLocation(programHandle, "uWarmCore")
        warmEdgeHandle = GLES20.glGetUniformLocation(programHandle, "uWarmEdge")
        glowIntensityHandle = GLES20.glGetUniformLocation(programHandle, "uGlowIntensity")

        // Ripple uniform handles (manual expansion for OpenGL ES 2.0)
        for (i in 0 until MAX_RIPPLES) {
            ripplePosHandles[i] = GLES20.glGetUniformLocation(programHandle, "uRipplePos$i")
            rippleTimeHandles[i] = GLES20.glGetUniformLocation(programHandle, "uRippleTime$i")
            rippleStrengthHandles[i] = GLES20.glGetUniformLocation(programHandle, "uRippleStrength$i")
            rippleActiveHandles[i] = GLES20.glGetUniformLocation(programHandle, "uRippleActive$i")
        }

        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "Terrain visualizer initialized successfully")
        Log.d(TAG, "Terrain visualizer initialized successfully")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        //region debug-point surface-changed
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_INFO, LOG_COMPONENT, "onSurfaceChanged: width=$width, height=$height")
        
        GLES20.glViewport(0, 0, width, height)

        // Create projection matrix (perspective)
        val aspectRatio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspectRatio, 1f, 150f)
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Projection matrix created: aspectRatio=$aspectRatio")

        // Create view matrix (camera from above looking down - terrain stays horizontal)
        // Camera positioned at height=35, distance=50 for flatter viewing angle
        // View angle: atan(35/50) ≈ 35° (much flatter than 67°)
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 35f, 50f,  // Camera position: height=35, distance=50 (flatter angle)
            0f, 0f, 0f,    // Look at center of terrain (horizontal plane)
            0f, 1f, 0f     // Up vector (Y-up)
        )
        
        VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "View matrix created: camera at (0, 35, 50) - view angle ~35° (flat)")

        // Model matrix (identity, terrain centered and horizontal - no rotation)
        Matrix.setIdentityM(modelMatrix, 0)
        //endregion debug-point surface-changed
    }

    private var frameCount = 0L
    override fun onDrawFrame(gl: GL10?) {
        //region debug-point draw-frame
        frameCount++
        
        // Clear with transparent background (alpha = 0)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (programHandle == 0) {
            if (frameCount % 60 == 1L) { // Log every 60 frames (~1 second)
                VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, "onDrawFrame: programHandle=0, skipping render")
            }
            return
        }

        GLES20.glUseProgram(programHandle)

        // Time uniform
        val currentTime = (System.currentTimeMillis() - startTime) / 1000f
        GLES20.glUniform1f(timeHandle, currentTime)

        // Matrix uniforms
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)
        GLES20.glUniformMatrix4fv(viewMatrixHandle, 1, false, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)

        // Audio data uniforms
        val audioData = lastAudioData ?: AudioVisualizerEngine.AudioData()
        GLES20.glUniform1f(subBassHandle, audioData.subBass)
        GLES20.glUniform1f(bassHandle, audioData.bass)
        GLES20.glUniform1f(lowMidHandle, audioData.lowMid)
        GLES20.glUniform1f(midHandle, audioData.mid)
        GLES20.glUniform1f(highMidHandle, audioData.highMid)
        GLES20.glUniform1f(presenceHandle, audioData.presence)
        GLES20.glUniform1f(brillianceHandle, audioData.brilliance)
        GLES20.glUniform1f(airHandle, audioData.air)
        GLES20.glUniform1f(warmthHandle, audioData.warmth)
        GLES20.glUniform1f(brightnessHandle, audioData.brightness)
        GLES20.glUniform1f(sharpnessHandle, audioData.sharpness)
        GLES20.glUniform1f(smoothnessHandle, audioData.smoothness)
        GLES20.glUniform1f(densityHandle, audioData.density)
        GLES20.glUniform1f(energyHandle, audioData.energy)

        // Color uniforms from palette - automatic emphasis color
        val defaultPalette = PlayerPalette(
            top = androidx.compose.ui.graphics.Color(Color.parseColor("#010204")),
            middle = androidx.compose.ui.graphics.Color(Color.parseColor("#010204")),
            bottom = androidx.compose.ui.graphics.Color(Color.parseColor("#020508")),
            accent = androidx.compose.ui.graphics.Color(Color.parseColor("#007AFF"))
        )
        val pal = palette ?: defaultPalette

        // Calculate accent-based colors
        val warmColors = calculateWarmColors(pal.accent)
        val coolColors = calculateCoolColors(pal.accent)
        
        // Extract colors from palette
        val baseColor1 = floatArrayOf(0.01f, 0.02f, 0.04f)
        val baseColor2 = floatArrayOf(0.03f, 0.05f, 0.09f)
        val coolCore = coolColors[0]
        val coolEdge = coolColors[1]
        val warmCore = warmColors[0]
        val warmEdge = warmColors[1]

        GLES20.glUniform3fv(baseColor1Handle, 1, baseColor1, 0)
        GLES20.glUniform3fv(baseColor2Handle, 1, baseColor2, 0)
        GLES20.glUniform3fv(coolCoreHandle, 1, coolCore, 0)
        GLES20.glUniform3fv(coolEdgeHandle, 1, coolEdge, 0)
        GLES20.glUniform3fv(warmCoreHandle, 1, warmCore, 0)
        GLES20.glUniform3fv(warmEdgeHandle, 1, warmEdge, 0)
        GLES20.glUniform1f(glowIntensityHandle, 1.0f)

        // Ripple uniforms (manual expansion for OpenGL ES 2.0)
        for (i in 0 until MAX_RIPPLES) {
            GLES20.glUniform2f(ripplePosHandles[i], ripples[i].x, ripples[i].y)
            GLES20.glUniform1f(rippleTimeHandles[i], ripples[i].time)
            GLES20.glUniform1f(rippleStrengthHandles[i], ripples[i].strength)
            GLES20.glUniform1i(rippleActiveHandles[i], ripples[i].active)
        }

        // Draw grid points using FloatBuffer
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, gridPositionsBuffer)

        // Use instancePos as aInstancePos attribute (same data)
        GLES20.glEnableVertexAttribArray(instancePosHandle)
        GLES20.glVertexAttribPointer(instancePosHandle, 2, GLES20.GL_FLOAT, false, 0, gridPositionsBuffer)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, gridCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(instancePosHandle)
        
        // Log render status periodically
        if (frameCount % 60 == 1L) { // Log every 60 frames (~1 second)
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, 
                "Frame $frameCount: gridCount=$gridCount, audioData: bass=${audioData.bass}, mid=${audioData.mid}, energy=${audioData.energy}")
        }
        //endregion debug-point draw-frame
    }

    private fun loadShader(type: Int, shaderSource: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderSource)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            val shaderTypeStr = if (type == GLES20.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, 
                "$shaderTypeStr shader compilation FAILED: $errorLog")
            Log.e(TAG, "Failed to compile shader: $errorLog")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun loadShaderFromAssets(filename: String): String {
        return try {
            val content = context.assets.open(filename).bufferedReader().use { it.readText() }
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_DEBUG, LOG_COMPONENT, "Shader file loaded: $filename (${content.length} chars)")
            content
        } catch (e: Exception) {
            VisualizerDebugLogger.log(VisualizerDebugLogger.LEVEL_ERROR, LOG_COMPONENT, "Failed to load shader from assets: $filename - ${e.message}")
            Log.e(TAG, "Failed to load shader from assets: $filename", e)
            ""
        }
    }
    
    /**
     * Calculate warm colors (core and edge) based on accent color.
     * Uses the accent color itself for warm emphasis.
     */
    private fun calculateWarmColors(accent: androidx.compose.ui.graphics.Color): Array<FloatArray> {
        val r = accent.red
        val g = accent.green
        val b = accent.blue
        
        // Boost saturation and brightness for visibility
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (r * 255).toInt(),
            (g * 255).toInt(),
            (b * 255).toInt(),
            hsv
        )
        
        // Ensure high saturation and brightness
        hsv[1] = hsv[1].coerceAtLeast(0.6f)  // Saturation
        hsv[2] = hsv[2].coerceAtLeast(0.7f)   // Value
        
        val warmColor = android.graphics.Color.HSVToColor(hsv)
        val warmR = android.graphics.Color.red(warmColor) / 255f
        val warmG = android.graphics.Color.green(warmColor) / 255f
        val warmB = android.graphics.Color.blue(warmColor) / 255f
        
        // Create core and edge colors (edge is slightly lighter)
        val warmCore = floatArrayOf(warmR, warmG, warmB)
        val warmEdge = floatArrayOf(
            warmR * 0.8f + 0.2f,
            warmG * 0.8f + 0.2f,
            warmB * 0.8f + 0.2f
        )
        
        return arrayOf(warmCore, warmEdge)
    }
    
    /**
     * Calculate cool colors (core and edge) based on accent color.
     * Uses complementary color (opposite on color wheel) for cool emphasis.
     */
    private fun calculateCoolColors(accent: androidx.compose.ui.graphics.Color): Array<FloatArray> {
        val r = accent.red
        val g = accent.green
        val b = accent.blue
        
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (r * 255).toInt(),
            (g * 255).toInt(),
            (b * 255).toInt(),
            hsv
        )
        
        // Create complementary color (hue + 180 degrees)
        hsv[0] = (hsv[0] + 180f) % 360f
        hsv[1] = hsv[1].coerceAtLeast(0.5f)  // Saturation
        hsv[2] = hsv[2].coerceAtLeast(0.7f)   // Value
        
        val coolColor = android.graphics.Color.HSVToColor(hsv)
        val coolR = android.graphics.Color.red(coolColor) / 255f
        val coolG = android.graphics.Color.green(coolColor) / 255f
        val coolB = android.graphics.Color.blue(coolColor) / 255f
        
        // Create core and edge colors (edge is slightly lighter)
        val coolCore = floatArrayOf(coolR, coolG, coolB)
        val coolEdge = floatArrayOf(
            coolR * 0.8f + 0.2f,
            coolG * 0.8f + 0.2f,
            coolB * 0.8f + 0.2f
        )
        
        return arrayOf(coolCore, coolEdge)
    }
}