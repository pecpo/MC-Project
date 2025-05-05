package io.getstream.webrtc.sample.compose.webrtc.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.getstream.log.StreamLog
import io.getstream.log.taggedLogger

interface AudioHandler {
  fun start()
  fun stop()
}

class AudioSwitchHandler constructor(private val context: Context) : AudioHandler {

  private val logger by taggedLogger(TAG)

  private var audioDeviceChangeListener: AudioDeviceChangeListener? = null
  private var onAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
  private var preferredDeviceList: List<Class<out AudioDevice>>? = null

  private var audioSwitch: AudioSwitch? = null

  private val handler = Handler(Looper.getMainLooper())

  override fun start() {
    logger.d { "[start] audioSwitch: $audioSwitch" }
    if (audioSwitch == null) {
      handler.removeCallbacksAndMessages(null)
      handler.post {
        val switch = AudioSwitch(
          context = context,
          audioFocusChangeListener = onAudioFocusChangeListener
            ?: defaultOnAudioFocusChangeListener,
          preferredDeviceList = preferredDeviceList ?: defaultPreferredDeviceList
        )
        audioSwitch = switch
        switch.start(audioDeviceChangeListener ?: defaultAudioDeviceChangeListener)
        switch.activate()
      }
    }
  }

  override fun stop() {
    logger.d { "[stop] no args" }
    handler.removeCallbacksAndMessages(null)
    handler.post {
      audioSwitch?.stop()
      audioSwitch = null
    }
  }

  companion object {
    private const val TAG = "Call:AudioSwitchHandler"
    private val defaultOnAudioFocusChangeListener by lazy(LazyThreadSafetyMode.NONE) {
      DefaultOnAudioFocusChangeListener()
    }
    private val defaultAudioDeviceChangeListener by lazy(LazyThreadSafetyMode.NONE) {
      object : AudioDeviceChangeListener {
        override fun invoke(
          audioDevices: List<AudioDevice>,
          selectedAudioDevice: AudioDevice?
        ) {
          StreamLog.i(TAG) { "[onAudioDeviceChange] selectedAudioDevice: $selectedAudioDevice" }
        }
      }
    }
    private val defaultPreferredDeviceList by lazy(LazyThreadSafetyMode.NONE) {
      listOf(
        AudioDevice.BluetoothHeadset::class.java,
        AudioDevice.WiredHeadset::class.java,
        AudioDevice.Earpiece::class.java,
        AudioDevice.Speakerphone::class.java
      )
    }

    private class DefaultOnAudioFocusChangeListener : AudioManager.OnAudioFocusChangeListener {
      override fun onAudioFocusChange(focusChange: Int) {
        val typeOfChange: String = when (focusChange) {
          AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
          AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
          else -> "AUDIOFOCUS_INVALID"
        }
        StreamLog.i(TAG) { "[onAudioFocusChange] focusChange: $typeOfChange" }
      }
    }
  }
}
