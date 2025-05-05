package io.getstream.webrtc.sample.compose.webrtc.peer

import android.content.Context
import android.os.Build
import io.getstream.log.taggedLogger
import io.getstream.webrtc.sample.compose.webrtc.BackgroundBlurProcessor
import io.getstream.webrtc.sample.compose.webrtc.audio.MyAudioProcessor
import kotlinx.coroutines.CoroutineScope
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class StreamPeerConnectionFactory constructor(
  private val context: Context
) {
  private val webRtcLogger by taggedLogger("Call:WebRTC")
  private val audioLogger by taggedLogger("Call:AudioTrackCallback")
  private val backgroundBlurProcessor = BackgroundBlurProcessor(context)

  // Create the audio processor instance but handle exceptions
  private val myAudioProcessor by lazy {
    try {
      MyAudioProcessor()
    } catch (e: Exception) {
      webRtcLogger.e { "Failed to initialize MyAudioProcessor: ${e.message}" }
      null
    }
  }

  val eglBaseContext: EglBase.Context by lazy {
    EglBase.create().eglBaseContext
  }


  private val videoDecoderFactory by lazy {
    DefaultVideoDecoderFactory(
      eglBaseContext
    )
  }

  // rtcConfig contains STUN and TURN servers list
  val rtcConfig = PeerConnection.RTCConfiguration(
    arrayListOf(
      // adding google's standard server
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
  ).apply {
    // it's very important to use new unified sdp semantics PLAN_B is deprecated
    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
  }


  private val videoEncoderFactory by lazy {
    val hardwareEncoder = HardwareVideoEncoderFactory(eglBaseContext, true, true)
    SimulcastVideoEncoderFactory(hardwareEncoder, SoftwareVideoEncoderFactory())
  }


  private val factory by lazy {
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context)
        .setInjectableLogger({ message, severity, label ->
          when (severity) {
            Logging.Severity.LS_VERBOSE -> {
              webRtcLogger.v { "[onLogMessage] label: $label, message: $message" }
            }
            Logging.Severity.LS_INFO -> {
              webRtcLogger.i { "[onLogMessage] label: $label, message: $message" }
            }
            Logging.Severity.LS_WARNING -> {
              webRtcLogger.w { "[onLogMessage] label: $label, message: $message" }
            }
            Logging.Severity.LS_ERROR -> {
              webRtcLogger.e { "[onLogMessage] label: $label, message: $message" }
            }
            Logging.Severity.LS_NONE -> {
              webRtcLogger.d { "[onLogMessage] label: $label, message: $message" }
            }
            else -> {}
          }
        }, Logging.Severity.LS_VERBOSE)
        .createInitializationOptions()
    )

    // Create a PeerConnectionFactory.Options object
    val options = PeerConnectionFactory.Options()

    // Build the audio processing module
    val adm = JavaAudioDeviceModule.builder(context)
      .setUseHardwareAcousticEchoCanceler(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      .setUseHardwareNoiseSuppressor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      .setAudioRecordErrorCallback(object :
        JavaAudioDeviceModule.AudioRecordErrorCallback {
        override fun onWebRtcAudioRecordInitError(p0: String?) {
          audioLogger.w { "[onWebRtcAudioRecordInitError] $p0" }
        }

        override fun onWebRtcAudioRecordStartError(
          p0: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
          p1: String?
        ) {
          audioLogger.w { "[onWebRtcAudioRecordInitError] $p1" }
        }

        override fun onWebRtcAudioRecordError(p0: String?) {
          audioLogger.w { "[onWebRtcAudioRecordError] $p0" }
        }
      })
      .setAudioTrackErrorCallback(object :
        JavaAudioDeviceModule.AudioTrackErrorCallback {
        override fun onWebRtcAudioTrackInitError(p0: String?) {
          audioLogger.w { "[onWebRtcAudioTrackInitError] $p0" }
        }

        override fun onWebRtcAudioTrackStartError(
          p0: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
          p1: String?
        ) {
          audioLogger.w { "[onWebRtcAudioTrackStartError] $p0" }
        }

        override fun onWebRtcAudioTrackError(p0: String?) {
          audioLogger.w { "[onWebRtcAudioTrackError] $p0" }
        }
      })
      .setAudioRecordStateCallback(object :
        JavaAudioDeviceModule.AudioRecordStateCallback {
        override fun onWebRtcAudioRecordStart() {
          audioLogger.d { "[onWebRtcAudioRecordStart] no args" }
        }

        override fun onWebRtcAudioRecordStop() {
          audioLogger.d { "[onWebRtcAudioRecordStop] no args" }
        }
      })
      .setAudioTrackStateCallback(object :
        JavaAudioDeviceModule.AudioTrackStateCallback {
        override fun onWebRtcAudioTrackStart() {
          audioLogger.d { "[onWebRtcAudioTrackStart] no args" }
        }

        override fun onWebRtcAudioTrackStop() {
          audioLogger.d { "[onWebRtcAudioTrackStop] no args" }
        }
      })
      .createAudioDeviceModule().also {
        it.setMicrophoneMute(false)
        it.setSpeakerMute(false)
      }

    // Build the peer connection factory
    PeerConnectionFactory.builder()
      .setVideoDecoderFactory(videoDecoderFactory)
      .setVideoEncoderFactory(videoEncoderFactory)
      .setAudioDeviceModule(adm)
      .setOptions(options)
      .createPeerConnectionFactory()
  }


  fun makePeerConnection(
    coroutineScope: CoroutineScope,
    configuration: PeerConnection.RTCConfiguration,
    type: StreamPeerType,
    mediaConstraints: MediaConstraints,
    onStreamAdded: ((MediaStream) -> Unit)? = null,
    onNegotiationNeeded: ((StreamPeerConnection, StreamPeerType) -> Unit)? = null,
    onIceCandidateRequest: ((IceCandidate, StreamPeerType) -> Unit)? = null,
    onVideoTrack: ((RtpTransceiver?) -> Unit)? = null
  ): StreamPeerConnection {
    val peerConnection = StreamPeerConnection(
      coroutineScope = coroutineScope,
      type = type,
      mediaConstraints = mediaConstraints,
      onStreamAdded = onStreamAdded,
      onNegotiationNeeded = onNegotiationNeeded,
      onIceCandidate = onIceCandidateRequest,
      onVideoTrack = onVideoTrack
    )
    val connection = makePeerConnectionInternal(
      configuration = configuration,
      observer = peerConnection
    )
    return peerConnection.apply { initialize(connection) }
  }


  private fun makePeerConnectionInternal(
    configuration: PeerConnection.RTCConfiguration,
    observer: PeerConnection.Observer?
  ): PeerConnection {
    return requireNotNull(
      factory.createPeerConnection(
        configuration,
        observer
      )
    )
  }


  fun makeVideoSource(isScreencast: Boolean): VideoSource =
    factory.createVideoSource(isScreencast).apply {
      setVideoProcessor(backgroundBlurProcessor) // Pass the processor directly
    }


  fun makeVideoTrack(
    source: VideoSource,
    trackId: String
  ): VideoTrack = factory.createVideoTrack(trackId, source)


  fun makeAudioSource(constraints: MediaConstraints = MediaConstraints()): AudioSource =
    factory.createAudioSource(constraints)


  fun makeAudioTrack(
    source: AudioSource,
    trackId: String
  ): AudioTrack = factory.createAudioTrack(trackId, source)


  fun dispose() {
    try {
      myAudioProcessor?.cleanup()
    } catch (e: Exception) {
      webRtcLogger.e { "Error during audio processor cleanup: ${e.message}" }
    }
  }
}