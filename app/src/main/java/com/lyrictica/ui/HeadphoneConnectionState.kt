package com.lyrictica.ui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberHeadphoneConnectionState(): Boolean {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var connected by remember(audioManager) { mutableStateOf(hasHeadphonesConnected(audioManager)) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                connected = hasHeadphonesConnected(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                connected = hasHeadphonesConnected(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        connected = hasHeadphonesConnected(audioManager)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return connected
}

private fun hasHeadphonesConnected(audioManager: AudioManager): Boolean {
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> true
            else -> false
        }
    }
}
