package io.getstream.webrtc.sample.compose

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.getstream.webrtc.sample.compose.ui.screens.SplashScreen
import io.getstream.webrtc.sample.compose.ui.screens.stage.StageScreen
import io.getstream.webrtc.sample.compose.ui.screens.video.VideoCallScreen
import io.getstream.webrtc.sample.compose.ui.theme.WebrtcSampleComposeTheme
import io.getstream.webrtc.sample.compose.webrtc.ConnectionResult
import io.getstream.webrtc.sample.compose.webrtc.SignalingClient
import io.getstream.webrtc.sample.compose.webrtc.WebRTCSessionState
import io.getstream.webrtc.sample.compose.webrtc.peer.StreamPeerConnectionFactory
import io.getstream.webrtc.sample.compose.webrtc.sessions.LocalWebRtcSessionManager
import io.getstream.webrtc.sample.compose.webrtc.sessions.WebRtcSessionManager
import io.getstream.webrtc.sample.compose.webrtc.sessions.WebRtcSessionManagerImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
  private val TAG = "MainActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)

    val signalingClient = SignalingClient()

    val sessionManager: WebRtcSessionManager = WebRtcSessionManagerImpl(
      context = this,
      signalingClient = signalingClient,
      peerConnectionFactory = StreamPeerConnectionFactory(this)
    )

    setContent {
      WebrtcSampleComposeTheme {
        CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
          ) {
            var showSplash by remember { mutableStateOf(true) }
            var onCallScreen by remember { mutableStateOf(false) }

            val context = LocalContext.current
            val sessionState by signalingClient.sessionStateFlow.collectAsState()
            val connectionCode by signalingClient.connectionCodeFlow.collectAsState()

            // Debug logging
            LaunchedEffect(sessionState) {
              Log.d(TAG, "Session state changed to: $sessionState")
            }

            // Auto-navigate to call screen when session becomes active
            LaunchedEffect(sessionState) {
              if (sessionState == WebRTCSessionState.Active && !onCallScreen) {
                Log.d(TAG, "Session is ACTIVE - auto-navigating to call screen")
                onCallScreen = true
              }
            }

            // Listen for start call signals
            LaunchedEffect(Unit) {
              signalingClient.startCallSignalFlow.collectLatest { startCall ->
                if (startCall && !onCallScreen) {
                  Log.d(TAG, "START_CALL signal received - navigating to call screen")
                  // Small delay to ensure both devices have time to process the signal
                  delay(300)
                  onCallScreen = true
                }
              }
            }

            // Handle connection results (success/error)
            LaunchedEffect(Unit) {
              signalingClient.connectionResultFlow.collect { result ->
                when (result) {
                  is ConnectionResult.Success -> {
                    // Optional: Show success toast
                    Toast.makeText(context, "Connected to room: ${result.connectionCode}", Toast.LENGTH_SHORT).show()
                  }
                  is ConnectionResult.Error -> {
                    Toast.makeText(context, "Connection error: ${result.message}", Toast.LENGTH_LONG).show()
                  }
                }
              }
            }

            if (showSplash) {
              SplashScreen { showSplash = false }
            } else {
              if (!onCallScreen) {
                StageScreen(
                  state = sessionState,
                  connectionCode = connectionCode,
                  onCreateSession = { code ->
                    signalingClient.createSession(code)
                  },
                  onJoinSession = { code ->
                    signalingClient.joinSession(code)
                  },
                  onJoinCall = {
                    Log.d(TAG, "Manual navigation to call screen requested - sending START_CALL signal")
                    // Notify the other peer that we're starting the call
                    signalingClient.notifyCallStarting()
                    onCallScreen = true
                  }
                )
              } else {
                VideoCallScreen(
                  onBackPressed = {
                    Log.d(TAG, "Navigating back from call screen")
                    onCallScreen = false
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}