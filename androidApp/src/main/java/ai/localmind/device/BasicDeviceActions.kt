package ai.localmind.device

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class DeviceActionResult(val success: Boolean, val message: String)

object BasicDeviceActions {
    fun openApp(activity: Activity, app: SupportedApp): DeviceActionResult {
        val intent = app.packageNames.firstNotNullOfOrNull(activity.packageManager::getLaunchIntentForPackage)
            ?: return DeviceActionResult(false, "${app.displayName} is not installed or cannot be opened on this phone.")
        return try {
            activity.startActivity(intent)
            DeviceActionResult(true, "Opened ${app.displayName}.")
        } catch (_: ActivityNotFoundException) {
            DeviceActionResult(false, "${app.displayName} could not be opened on this phone.")
        } catch (_: SecurityException) {
            DeviceActionResult(false, "Android did not allow LocalMind to open ${app.displayName}.")
        }
    }

    fun setTorch(context: Context, enabled: Boolean): DeviceActionResult {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return DeviceActionResult(false, "This phone does not have an available flashlight.")

        val verified = CountDownLatch(1)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, active: Boolean) {
                if (id == cameraId && active == enabled) verified.countDown()
            }
        }
        return try {
            manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            manager.setTorchMode(cameraId, enabled)
            if (verified.await(TORCH_VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                DeviceActionResult(true, "Flashlight turned ${if (enabled) "on" else "off"}.")
            } else {
                DeviceActionResult(false, "Android did not confirm the flashlight change.")
            }
        } catch (_: SecurityException) {
            DeviceActionResult(false, "Camera permission is required to control the flashlight.")
        } catch (_: Exception) {
            DeviceActionResult(false, "The flashlight is unavailable, possibly because the camera is in use.")
        } finally {
            runCatching { manager.unregisterTorchCallback(callback) }
        }
    }

    fun controlMedia(context: Context, command: MediaCommand): DeviceActionResult {
        val keyCode = when (command) {
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        return runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            DeviceActionResult(true, "Sent ${command.name.lowercase()} to the active media app.")
        }.getOrElse { DeviceActionResult(false, "Android could not send the media command.") }
    }

    fun changeVolume(context: Context, command: VolumeCommand): DeviceActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
        return runCatching {
            val direction = when (command) {
                VolumeCommand.UP -> AudioManager.ADJUST_RAISE
                VolumeCommand.DOWN -> AudioManager.ADJUST_LOWER
                VolumeCommand.MUTE -> AudioManager.ADJUST_MUTE
                VolumeCommand.UNMUTE -> AudioManager.ADJUST_UNMUTE
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            DeviceActionResult(true, "Music volume is $current of $maximum.")
        }.getOrElse { DeviceActionResult(false, "Android could not change the music volume.") }
    }

    private const val TORCH_VERIFY_TIMEOUT_SECONDS = 2L
}
