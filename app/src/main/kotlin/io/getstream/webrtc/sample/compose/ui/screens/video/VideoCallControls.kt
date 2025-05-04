package io.getstream.webrtc.sample.compose.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon // Material Icon composable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
// Other necessary imports (Color, theme colors, CallMediaState, CallAction, etc.)
// *** Make sure you have the material-icons imports listed in step 2 ***

@Composable
fun VideoCallControls(
  modifier: Modifier = Modifier,
  callMediaState: CallMediaState,
  actions: List<VideoCallControlAction> = buildDefaultCallControlActions(callMediaState = callMediaState),
  onCallAction: (CallAction) -> Unit
) {
  LazyRow(
    modifier = modifier
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceAround
  ) {
    items(actions) { action ->
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(action.background)
          .clickable { onCallAction(action.callAction) }
          .padding(10.dp),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = action.icon, // *** Use imageVector parameter ***
          contentDescription = action.description,
          tint = action.iconTint
        )
      }
    }
  }
}