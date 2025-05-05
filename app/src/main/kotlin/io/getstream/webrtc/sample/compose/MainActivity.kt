package io.getstream.webrtc.sample.compose

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.getstream.webrtc.sample.compose.ui.screens.SplashScreen
import io.getstream.webrtc.sample.compose.ui.screens.stage.StageScreen
import io.getstream.webrtc.sample.compose.ui.screens.video.VideoCallScreen
import io.getstream.webrtc.sample.compose.ui.theme.WebrtcSampleComposeTheme
import io.getstream.webrtc.sample.compose.webrtc.SignalingClient
import io.getstream.webrtc.sample.compose.webrtc.peer.StreamPeerConnectionFactory
import io.getstream.webrtc.sample.compose.webrtc.sessions.LocalWebRtcSessionManager
import io.getstream.webrtc.sample.compose.webrtc.sessions.WebRtcSessionManager
import io.getstream.webrtc.sample.compose.webrtc.sessions.WebRtcSessionManagerImpl

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)

    val sessionManager: WebRtcSessionManager = WebRtcSessionManagerImpl(
      context = this,
      signalingClient = SignalingClient(),
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
            // This state now controls navigation between Stage and Video screens
            var onCallScreen by remember { mutableStateOf(false) }
            val state by sessionManager.signalingClient.sessionStateFlow.collectAsState()

            if (showSplash) {
              SplashScreen { showSplash = false }
            } else {
              if (!onCallScreen) {
                StageScreen(state = state) {
                  onCallScreen = true
                }
              } else {
                // Pass lambda to VideoCallScreen to navigate *back* to StageScreen
                VideoCallScreen(
                  onLeaveCall = { onCallScreen = false } // Pass the callback here
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onBackPressed() {
       super.onBackPressed()
  }

  override fun onDestroy() {
    super.onDestroy()
  }
}