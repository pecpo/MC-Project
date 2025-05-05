package io.getstream.webrtc.sample.compose.webrtc.peer

import io.getstream.log.taggedLogger
import io.getstream.webrtc.sample.compose.webrtc.utils.addRtcIceCandidate
import io.getstream.webrtc.sample.compose.webrtc.utils.createValue
import io.getstream.webrtc.sample.compose.webrtc.utils.setValue
import io.getstream.webrtc.sample.compose.webrtc.utils.stringify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription

class StreamPeerConnection(
  private val coroutineScope: CoroutineScope,
  private val type: StreamPeerType,
  private val mediaConstraints: MediaConstraints,
  private val onStreamAdded: ((MediaStream) -> Unit)?,
  private val onNegotiationNeeded: ((StreamPeerConnection, StreamPeerType) -> Unit)?,
  private val onIceCandidate: ((IceCandidate, StreamPeerType) -> Unit)?,
  private val onVideoTrack: ((RtpTransceiver?) -> Unit)?
) : PeerConnection.Observer {

  private val typeTag = type.stringify()

  private val logger by taggedLogger("Call:PeerConnection")


  lateinit var connection: PeerConnection
    private set


  private var statsJob: Job? = null


  private val pendingIceMutex = Mutex()
  private val pendingIceCandidates = mutableListOf<IceCandidate>()

  private val statsFlow: MutableStateFlow<RTCStatsReport?> = MutableStateFlow(null)

  init {
    logger.i { "<init> #sfu; #$typeTag; mediaConstraints: $mediaConstraints" }
  }

  fun initialize(peerConnection: PeerConnection) {
    logger.d { "[initialize] #sfu; #$typeTag; peerConnection: $peerConnection" }
    this.connection = peerConnection
  }

  suspend fun createOffer(): Result<SessionDescription> {
    logger.d { "[createOffer] #sfu; #$typeTag; no args" }
    return createValue { connection.createOffer(it, mediaConstraints) }
  }

  suspend fun createAnswer(): Result<SessionDescription> {
    logger.d { "[createAnswer] #sfu; #$typeTag; no args" }
    return createValue { connection.createAnswer(it, mediaConstraints) }
  }

  suspend fun setRemoteDescription(sessionDescription: SessionDescription): Result<Unit> {
    logger.d { "[setRemoteDescription] #sfu; #$typeTag; answerSdp: ${sessionDescription.stringify()}" }
    return setValue {
      connection.setRemoteDescription(
        it,
        SessionDescription(
          sessionDescription.type,
          sessionDescription.description.mungeCodecs()
        )
      )
    }.also {
      pendingIceMutex.withLock {
        pendingIceCandidates.forEach { iceCandidate ->
          logger.i { "[setRemoteDescription] #sfu; #subscriber; pendingRtcIceCandidate: $iceCandidate" }
          connection.addRtcIceCandidate(iceCandidate)
        }
        pendingIceCandidates.clear()
      }
    }
  }

  suspend fun setLocalDescription(sessionDescription: SessionDescription): Result<Unit> {
    val sdp = SessionDescription(
      sessionDescription.type,
      sessionDescription.description.mungeCodecs()
    )
    logger.d { "[setLocalDescription] #sfu; #$typeTag; offerSdp: ${sessionDescription.stringify()}" }
    return setValue { connection.setLocalDescription(it, sdp) }
  }

  suspend fun addIceCandidate(iceCandidate: IceCandidate): Result<Unit> {
    if (connection.remoteDescription == null) {
      logger.w { "[addIceCandidate] #sfu; #$typeTag; postponed (no remoteDescription): $iceCandidate" }
      pendingIceMutex.withLock {
        pendingIceCandidates.add(iceCandidate)
      }
      return Result.failure(RuntimeException("RemoteDescription is not set"))
    }
    logger.d { "[addIceCandidate] #sfu; #$typeTag; rtcIceCandidate: $iceCandidate" }
    return connection.addRtcIceCandidate(iceCandidate).also {
      logger.v { "[addIceCandidate] #sfu; #$typeTag; completed: $it" }
    }
  }

  override fun onIceCandidate(candidate: IceCandidate?) {
    logger.i { "[onIceCandidate] #sfu; #$typeTag; candidate: $candidate" }
    if (candidate == null) return

    onIceCandidate?.invoke(candidate, type)
  }

  override fun onAddStream(stream: MediaStream?) {
    logger.i { "[onAddStream] #sfu; #$typeTag; stream: $stream" }
    if (stream != null) {
      onStreamAdded?.invoke(stream)
    }
  }

  override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
    logger.i { "[onAddTrack] #sfu; #$typeTag; receiver: $receiver, mediaStreams: $mediaStreams" }
    mediaStreams?.forEach { mediaStream ->
      logger.v { "[onAddTrack] #sfu; #$typeTag; mediaStream: $mediaStream" }
      mediaStream.audioTracks?.forEach { remoteAudioTrack ->
        logger.v { "[onAddTrack] #sfu; #$typeTag; remoteAudioTrack: ${remoteAudioTrack.stringify()}" }
        remoteAudioTrack.setEnabled(true)
      }
      onStreamAdded?.invoke(mediaStream)
    }
  }

  override fun onRenegotiationNeeded() {
    logger.i { "[onRenegotiationNeeded] #sfu; #$typeTag; no args" }
    onNegotiationNeeded?.invoke(this, type)
  }

  override fun onRemoveStream(stream: MediaStream?) {}

  override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
    logger.i { "[onIceConnectionChange] #sfu; #$typeTag; newState: $newState" }
    when (newState) {
      PeerConnection.IceConnectionState.CLOSED,
      PeerConnection.IceConnectionState.FAILED,
      PeerConnection.IceConnectionState.DISCONNECTED -> statsJob?.cancel()
      PeerConnection.IceConnectionState.CONNECTED -> statsJob = observeStats()
      else -> Unit
    }
  }

  fun getStats(): StateFlow<RTCStatsReport?> {
    return statsFlow
  }

  private fun observeStats() = coroutineScope.launch {
    while (isActive) {
      delay(10_000L)
      connection.getStats {
        logger.v { "[observeStats] #sfu; #$typeTag; stats: $it" }
        statsFlow.value = it
      }
    }
  }

  override fun onTrack(transceiver: RtpTransceiver?) {
    logger.i { "[onTrack] #sfu; #$typeTag; transceiver: $transceiver" }
    onVideoTrack?.invoke(transceiver)
  }

  override fun onRemoveTrack(receiver: RtpReceiver?) {
    logger.i { "[onRemoveTrack] #sfu; #$typeTag; receiver: $receiver" }
  }

  override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
    logger.d { "[onSignalingChange] #sfu; #$typeTag; newState: $newState" }
  }

  override fun onIceConnectionReceivingChange(receiving: Boolean) {
    logger.i { "[onIceConnectionReceivingChange] #sfu; #$typeTag; receiving: $receiving" }
  }

  override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
    logger.i { "[onIceGatheringChange] #sfu; #$typeTag; newState: $newState" }
  }

  override fun onIceCandidatesRemoved(iceCandidates: Array<out org.webrtc.IceCandidate>?) {
    logger.i { "[onIceCandidatesRemoved] #sfu; #$typeTag; iceCandidates: $iceCandidates" }
  }

  override fun onIceCandidateError(event: IceCandidateErrorEvent?) {
    logger.e { "[onIceCandidateError] #sfu; #$typeTag; event: ${event?.stringify()}" }
  }

  override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
    logger.i { "[onConnectionChange] #sfu; #$typeTag; newState: $newState" }
  }

  override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
    logger.i { "[onSelectedCandidatePairChanged] #sfu; #$typeTag; event: $event" }
  }

  override fun onDataChannel(channel: DataChannel?): Unit = Unit

  override fun toString(): String =
    "StreamPeerConnection(type='$typeTag', constraints=$mediaConstraints)"

  private fun String.mungeCodecs(): String {
    return this.replace("vp9", "VP9").replace("vp8", "VP8").replace("h264", "H264")
  }
}
