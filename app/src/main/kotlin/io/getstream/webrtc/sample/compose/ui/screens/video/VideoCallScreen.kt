package io.getstream.webrtc.sample.compose.ui.screens.video

import android.media.AudioAttributes // Import AudioAttributes
import android.media.SoundPool // Import SoundPool
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.* // Use specific imports if preferred
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.* // Import DisposableEffect, remember, etc.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext // Import LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.getstream.webrtc.sample.compose.R // Import R class for resources
import io.getstream.webrtc.sample.compose.ui.components.VideoRenderer
import io.getstream.webrtc.sample.compose.webrtc.sessions.LocalWebRtcSessionManager
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun VideoCallScreen(
  onLeaveCall: () -> Unit
) {
  val sessionManager = LocalWebRtcSessionManager.current
  val context = LocalContext.current // Get context for SoundPool

  // --- Sound Pool Setup ---
  var soundPool by remember { mutableStateOf<SoundPool?>(null) }
  var toggleOnSoundId by remember { mutableStateOf<Int?>(null) }
  var toggleOffSoundId by remember { mutableStateOf<Int?>(null) }

  // Create and load sounds
  LaunchedEffect(Unit) {
    val audioAttributes = AudioAttributes.Builder()
      // Usage for UI feedback sounds
      .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

    soundPool = SoundPool.Builder()
      .setMaxStreams(2) // Allow playing 2 sounds simultaneously if needed
      .setAudioAttributes(audioAttributes)
      .build().apply {
        // Load sounds and store their IDs
        toggleOnSoundId = load(context, R.raw.toggle_on, 1)
        toggleOffSoundId = load(context, R.raw.toggle_off, 1)
      }
  }

  // Release SoundPool when the composable is disposed
  DisposableEffect(Unit) {
    onDispose {
      soundPool?.release()
      soundPool = null
    }
  }
  // --- End Sound Pool Setup ---


  // State for call duration (same as before)
  var callStartTime by remember { mutableStateOf<Long?>(null) }
  var callDuration by remember { mutableStateOf("00:00") }

  LaunchedEffect(key1 = Unit) {
    sessionManager.onSessionScreenReady()
  }

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    var parentSize: IntSize by remember { mutableStateOf(IntSize(0, 0)) }

    val remoteVideoTrackState by sessionManager.remoteVideoTrackFlow.collectAsState(null)
    val remoteVideoTrack = remoteVideoTrackState

    val localVideoTrackState by sessionManager.localVideoTrackFlow.collectAsState(null)
    val localVideoTrack = localVideoTrackState

    var callMediaState by remember { mutableStateOf(CallMediaState()) }

    // Timer Logic (same as before)
    LaunchedEffect(remoteVideoTrack) {
      if (remoteVideoTrack != null && callStartTime == null) {
        callStartTime = System.currentTimeMillis()
        while (true) {
          val currentStartTime = callStartTime
          if (currentStartTime != null) {
            val elapsedMillis = System.currentTimeMillis() - currentStartTime
            callDuration = formatDuration(elapsedMillis)
          } else {
            break
          }
          delay(1000)
        }
      } else if (remoteVideoTrack == null) {
        callStartTime = null
        callDuration = "00:00"
      }
    }


    if (remoteVideoTrack != null) {
      VideoRenderer(
        videoTrack = remoteVideoTrack,
        modifier = Modifier
          .fillMaxSize()
          .onSizeChanged { parentSize = it }
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
      ) {
        Text("Connecting...", color = Color.White, fontSize = 18.sp)
      }
    }

    // Display Timer (same as before)
    if (callStartTime != null) {
      Text(
        text = callDuration,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 16.dp)
          .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      )
    }

    if (localVideoTrack != null && callMediaState.isCameraEnabled) {
      FloatingVideoRenderer(
        modifier = Modifier
          .size(width = 150.dp, height = 210.dp)
          .clip(RoundedCornerShape(16.dp))
          .align(Alignment.TopEnd)
          .padding(16.dp),
        videoTrack = localVideoTrack,
        parentBounds = parentSize,
        paddingValues = PaddingValues(0.dp)
      )
    }

    VideoCallControls(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .padding(bottom = 24.dp),
      callMediaState = callMediaState,
      onCallAction = { action -> // Modified lambda
        var playSoundId: Int? = null // Sound to play for this action

        when (action) {
          is CallAction.ToggleMicroPhone -> {
            val newState = callMediaState.isMicrophoneEnabled.not()
            playSoundId = if (newState) toggleOnSoundId else toggleOffSoundId // Select sound based on NEW state
            callMediaState = callMediaState.copy(isMicrophoneEnabled = newState)
            sessionManager.enableMicrophone(newState)
          }
          is CallAction.ToggleCamera -> {
            val newState = callMediaState.isCameraEnabled.not()
            playSoundId = if (newState) toggleOnSoundId else toggleOffSoundId // Select sound based on NEW state
            callMediaState = callMediaState.copy(isCameraEnabled = newState)
            sessionManager.enableCamera(newState)
          }
          CallAction.FlipCamera -> sessionManager.flipCamera()
          CallAction.LeaveCall -> {
            sessionManager.disconnect()
            onLeaveCall()
          }
        }

        // Play the selected sound, if any
        playSoundId?.let { soundId ->
          soundPool?.play(soundId, 0.5f, 0.5f, 1, 0, 1.0f) // Adjust volume (0.0 to 1.0)
        }
      }
    )
  }
}

// Helper function formatDuration (same as before)
private fun formatDuration(millis: Long): String {
  val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
  val hours = TimeUnit.SECONDS.toHours(totalSeconds)
  val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
  val seconds = totalSeconds % 60

  return if (hours > 0) {
    String.format("%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format("%02d:%02d", minutes, seconds)
  }
}