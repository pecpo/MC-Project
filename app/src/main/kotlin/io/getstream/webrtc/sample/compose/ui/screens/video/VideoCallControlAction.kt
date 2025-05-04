package io.getstream.webrtc.sample.compose.ui.screens.video // Adjust package if needed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector // Import ImageVector
import androidx.compose.ui.res.stringResource
import io.getstream.webrtc.sample.compose.R // Still needed for string resources
import io.getstream.webrtc.sample.compose.ui.theme.Disabled
import io.getstream.webrtc.sample.compose.ui.theme.Primary

///**
// * Represents the state relevant for call controls.
// */
//data class CallMediaState(
//  val isMicrophoneEnabled: Boolean = true,
//  val isCameraEnabled: Boolean = true
//)

/**
 * Defines the possible actions a user can take via the call controls.
 */
sealed class CallAction {
  data class ToggleMicroPhone(val isEnabled: Boolean) : CallAction()
  data class ToggleCamera(val isEnabled: Boolean) : CallAction()
  object FlipCamera : CallAction()
  object LeaveCall : CallAction()
}

/**
 * Data class holding all the necessary information to render a single
 * video call control button (icon vector, colors, action, description).
 */
data class VideoCallControlAction(
  val icon: ImageVector, // Changed type to ImageVector
  val iconTint: Color,
  val background: Color,
  val callAction: CallAction,
  val description: String // Accessibility description
)

/**
 * Builds the list of [VideoCallControlAction] items using Material Icons,
 * dynamically adjusting icons, backgrounds, and descriptions based on the [callMediaState].
 *
 * @param callMediaState The current state of the microphone and camera.
 * @return A list of [VideoCallControlAction] ready for rendering.
 */
@Composable
fun buildDefaultCallControlActions(
  callMediaState: CallMediaState
): List<VideoCallControlAction> {
  val micEnabled = callMediaState.isMicrophoneEnabled
  val cameraEnabled = callMediaState.isCameraEnabled

  // --- Microphone Action ---
  val microphoneIconVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff // Use Material Icon
  val micBackground = if (micEnabled) Primary else Disabled // Dynamic background
  val micDescription = stringResource(id = if (micEnabled) R.string.acc_mute_microphone else R.string.acc_unmute_microphone)

  // --- Camera Action ---
  val cameraIconVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff // Use Material Icon
  val cameraBackground = if (cameraEnabled) Primary else Disabled // Dynamic background
  val cameraDescription = stringResource(id = if (cameraEnabled) R.string.acc_turn_off_camera else R.string.acc_turn_on_camera)

  // --- Flip Camera Action ---
  val flipCameraDescription = stringResource(id = R.string.acc_flip_camera)

  // --- Leave Call Action ---
  val leaveCallDescription = stringResource(id = R.string.acc_leave_call)


  // Assemble the list of actions
  return listOf(
    VideoCallControlAction(
      icon = microphoneIconVector, // Pass ImageVector
      iconTint = Color.White,
      background = micBackground,
      callAction = CallAction.ToggleMicroPhone(micEnabled),
      description = micDescription
    ),
    VideoCallControlAction(
      icon = cameraIconVector, // Pass ImageVector
      iconTint = Color.White,
      background = cameraBackground,
      callAction = CallAction.ToggleCamera(cameraEnabled),
      description = cameraDescription
    ),
    VideoCallControlAction(
      icon = Icons.Default.Cameraswitch, // Pass ImageVector
      iconTint = Color.White,
      background = Primary,
      callAction = CallAction.FlipCamera,
      description = flipCameraDescription
    ),
    VideoCallControlAction(
      icon = Icons.Default.CallEnd, // Pass ImageVector
      iconTint = Color.White,
      background = Color.Red, // Or Color.Red
      callAction = CallAction.LeaveCall,
      description = leaveCallDescription
    )
  )
}