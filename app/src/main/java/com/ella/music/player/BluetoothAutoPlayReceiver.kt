package com.ella.music.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log

class BluetoothAutoPlayReceiver(
    private val isAutoPlayEnabled: () -> Boolean,
    private val onDeviceConnected: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BtAutoPlay"

        fun createIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }

        fun hasBluetoothConnectPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    if (hasBluetoothConnectPermission(context)) device?.name ?: "Unknown" else "Unknown"
                } catch (_: SecurityException) { "Unknown" }

                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Log.i(TAG, "Bluetooth A2DP connected: $deviceName")
                    if (isAutoPlayEnabled()) {
                        Log.i(TAG, "Bluetooth auto-play enabled, starting playback")
                        onDeviceConnected()
                    }
                }
            }
        }
    }
}
