package com.ella.music.visualizer

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug logger for visualizer feature
 * Logs are saved to file and can be viewed in Settings
 */
object VisualizerDebugLogger {
    private const val TAG = "VisualizerDebug"
    private const val LOG_FILE_NAME = "visualizer_debug.log"
    
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    private var isInitialized = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // Log levels
    const val LEVEL_INFO = "INFO"
    const val LEVEL_ERROR = "ERROR"
    const val LEVEL_WARN = "WARN"
    const val LEVEL_DEBUG = "DEBUG"
    
    fun initialize(context: android.content.Context) {
        if (isInitialized) return
        
        try {
            val logDir = File(context.filesDir, "debug_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, LOG_FILE_NAME)
            fileWriter = FileWriter(logFile, true)
            isInitialized = true
            log(LEVEL_INFO, "VisualizerDebugLogger", "Logger initialized, log file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logger", e)
        }
    }
    
    fun log(level: String, component: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp [$level] [$component] $message"
        
        // Log to Android Log
        when (level) {
            LEVEL_ERROR -> Log.e(TAG, "$component: $message")
            LEVEL_WARN -> Log.w(TAG, "$component: $message")
            LEVEL_DEBUG -> Log.d(TAG, "$component: $message")
            else -> Log.i(TAG, "$component: $message")
        }
        
        // Log to file - use try-catch to handle file writer state
        try {
            if (fileWriter == null && logFile != null) {
                fileWriter = FileWriter(logFile, true)
            }
            fileWriter?.apply {
                write(logEntry + "\n")
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file: ${e.message}")
            // Try to reinitialize file writer
            try {
                if (logFile != null) {
                    fileWriter = FileWriter(logFile, true)
                    fileWriter?.write(logEntry + "\n")
                    fileWriter?.flush()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to reinitialize file writer", e2)
            }
        }
    }
    
    fun getLogFileContent(context: android.content.Context): String {
        return try {
            val file = File(File(context.filesDir, "debug_logs"), LOG_FILE_NAME)
            if (file.exists()) {
                val content = file.readText()
                if (content.isBlank()) {
                    "日志文件为空，请先打开可视化功能生成日志"
                } else {
                    content
                }
            } else {
                "日志文件不存在，请先打开可视化功能"
            }
        } catch (e: Exception) {
            "读取日志文件失败: ${e.message}"
        }
    }
    
    fun clearLogFile(context: android.content.Context) {
        try {
            fileWriter?.close()
            fileWriter = null
            
            val file = File(File(context.filesDir, "debug_logs"), LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            
            Log.i(TAG, "Log file cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log file", e)
        }
    }
    
    fun release() {
        try {
            fileWriter?.close()
            fileWriter = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close file writer", e)
        }
    }
}