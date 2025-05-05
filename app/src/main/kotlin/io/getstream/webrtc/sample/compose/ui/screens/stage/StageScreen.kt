package io.getstream.webrtc.sample.compose.ui.screens.stage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.getstream.webrtc.sample.compose.R
import io.getstream.webrtc.sample.compose.webrtc.WebRTCSessionState
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun StageScreen(
  state: WebRTCSessionState,
  onJoinCall: () -> Unit
) {
  // Add random wifi strength simulation for demo purposes
  var wifiStrength by remember { mutableStateOf(Random.nextInt(0, 5)) }

  // Periodically update wifi strength for demonstration
  LaunchedEffect(key1 = Unit) {
    while(true) {
      delay(5000)  // Update every 5 seconds
      wifiStrength = Random.nextInt(0, 5)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
  ) {
    // WiFi strength indicator in top-right corner
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Signal: ",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
      )
      Icon(
        imageVector = when (wifiStrength) {
          4 -> Icons.Rounded.SignalWifi4Bar
          3 -> Icons.Rounded.SignalWifi4Bar
          2 -> Icons.Rounded.SignalWifiBad
          1 -> Icons.Rounded.SignalWifiBad
          else -> Icons.Rounded.SignalWifiOff
        },
        contentDescription = "WiFi Signal Strength",
        tint = when (wifiStrength) {
          0 -> Color.Red
          1, 2 -> Color(0xFFFFA000) // Amber
          else -> Color(0xFF4CAF50) // Green
        },
        modifier = Modifier.size(24.dp)
      )
    }

    // App title
    Text(
      text = "Konnekt",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = colorResource(id = R.color.brand_color),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 48.dp)
    )

    // Main content
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Status card
      Card(
        modifier = Modifier
          .fillMaxWidth(0.85f)
          .padding(16.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Status indicator
          val (statusColor, statusText) = when (state) {
            WebRTCSessionState.Offline -> Pair(Color.Gray, stringResource(id = R.string.button_start_session))
            WebRTCSessionState.Impossible -> Pair(Color.Red, stringResource(id = R.string.session_impossible))
            WebRTCSessionState.Ready -> Pair(Color(0xFF4CAF50), stringResource(id = R.string.session_ready))
            WebRTCSessionState.Creating -> Pair(Color(0xFFFFA000), stringResource(id = R.string.session_creating))
            WebRTCSessionState.Active -> Pair(Color(0xFF2196F3), stringResource(id = R.string.session_active))
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            // Animated pulse for status indicator
            val pulseRadius by animateFloatAsState(
              targetValue = if (state == WebRTCSessionState.Creating) 1.2f else 1f,
              animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
              )
            )

            Box(
              modifier = Modifier
                .size(16.dp * pulseRadius)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(statusColor)
              )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Text(
              text = "Status: $statusText",
              fontSize = 18.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colors.onSurface
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Description based on state
          Text(
            text = when (state) {
              WebRTCSessionState.Offline -> "Your device is currently offline. Please check your connection."
              WebRTCSessionState.Impossible -> "Cannot establish connection. Please try again later."
              WebRTCSessionState.Ready -> "Your device is ready to join a video call."
              WebRTCSessionState.Creating -> "Setting up your call. Please wait..."
              WebRTCSessionState.Active -> "You're currently in an active call."
            },
            fontSize = 14.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      // Join button
      val enabledCall by remember(state) {
        mutableStateOf(state == WebRTCSessionState.Ready || state == WebRTCSessionState.Creating)
      }

      Button(
        onClick = { onJoinCall.invoke() },
        enabled = enabledCall,
        modifier = Modifier
          .size(width = 200.dp, height = 56.dp)
          .clip(RoundedCornerShape(28.dp)),
        colors = ButtonDefaults.buttonColors(
          backgroundColor = MaterialTheme.colors.primary,
          disabledBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "Join Call",
            tint = if (enabledCall) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
          )

          Spacer(modifier = Modifier.size(8.dp))

          Text(
            text = "Join Call",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabledCall) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
          )
        }
      }

      // Conditional UI element for active calls
      AnimatedVisibility(
        visible = state == WebRTCSessionState.Active,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
      ) {
        Text(
          text = "Call is currently active",
          fontSize = 16.sp,
          color = Color(0xFF2196F3),
          modifier = Modifier.padding(top = 16.dp)
        )
      }
    }
  }
}