package io.getstream.webrtc.sample.compose.ui.screens.stage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.getstream.webrtc.sample.compose.R
import io.getstream.webrtc.sample.compose.webrtc.WebRTCSessionState
import android.widget.Toast

/**
 * Enum representing the different modes of the StageScreen
 */
enum class ConnectionMode {
  INITIAL,  // Initial screen with create/join options
  CREATE,   // Create a new meeting
  JOIN      // Join with a code
}

/**
 * StageScreen composable that displays the home screen of the app
 *
 * @param state The current state of the WebRTC session
 * @param connectionCode The current connection code (if any)
 * @param onCreateSession Callback when creating a new session with code
 * @param onJoinSession Callback when joining a session with code
 * @param onJoinCall Callback when starting the call
 */
@Composable
fun StageScreen(
  state: WebRTCSessionState,
  connectionCode: String? = null,
  onCreateSession: (String) -> Unit = {},
  onJoinSession: (String) -> Unit = {},
  onJoinCall: () -> Unit = {}
) {
  val context = LocalContext.current
  var connectionMode by remember { mutableStateOf(ConnectionMode.INITIAL) }
  var inputCode by remember { mutableStateOf("") }
  val clipboardManager = LocalClipboardManager.current

  // Generate a random connection code when we enter CREATE mode
  LaunchedEffect(connectionMode) {
    if (connectionMode == ConnectionMode.CREATE && connectionCode == null) {
      // In a real app, you'd call the server to generate a code
      // For now, generate it locally
      val code = generateConnectionCode()
      onCreateSession(code)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    when (connectionMode) {
      ConnectionMode.INITIAL -> {
        // Initial mode with create/join options
        InitialModeContent(
          onCreateClicked = { connectionMode = ConnectionMode.CREATE },
          onJoinClicked = { connectionMode = ConnectionMode.JOIN }
        )
      }

      ConnectionMode.CREATE -> {
        // Create mode with generated code display
        CreateModeContent(
          state = state,
          connectionCode = connectionCode,
          clipboardManager = clipboardManager,
          onJoinCall = onJoinCall,
          onBackClicked = { connectionMode = ConnectionMode.INITIAL }
        )
      }

      ConnectionMode.JOIN -> {
        // Join mode with code input
        JoinModeContent(
          state = state,
          inputCode = inputCode,
          onCodeChanged = { inputCode = it.uppercase() },
          onJoinClicked = { onJoinSession(inputCode) },
          onBackClicked = { connectionMode = ConnectionMode.INITIAL },
          onJoinCall = onJoinCall,
          matchedCode = connectionCode
        )
      }
    }
  }
}

@Composable
private fun InitialModeContent(
  onCreateClicked: () -> Unit,
  onJoinClicked: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Video Connect",
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colors.primary,
      modifier = Modifier.padding(bottom = 48.dp)
    )

    Button(
      onClick = onCreateClicked,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(8.dp)
    ) {
      Text(
        text = "Create New Meeting",
        fontSize = 18.sp
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Divider(modifier = Modifier.weight(1f))
      Text(
        text = " OR ",
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
      )
      Divider(modifier = Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
      onClick = onJoinClicked,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(8.dp)
    ) {
      Text(
        text = "Join with Code",
        fontSize = 18.sp
      )
    }
  }
}

@Composable
private fun CreateModeContent(
  state: WebRTCSessionState,
  connectionCode: String?,
  clipboardManager: ClipboardManager,
  onJoinCall: () -> Unit,
  onBackClicked: () -> Unit
) {
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Video Connect",
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colors.primary,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Text(
      text = "Your Meeting Code",
      fontSize = 16.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
      modifier = Modifier.padding(bottom = 16.dp)
    )

    connectionCode?.let { code ->
      // Display the code in a card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        elevation = 4.dp
      ) {
        Row(
          modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = code,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
          )

          IconButton(onClick = {
            clipboardManager.setText(AnnotatedString(code))
            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
          }) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy code",
              tint = MaterialTheme.colors.primary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Share this code with the person you want to call",
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Status based on session state
      when (state) {
        WebRTCSessionState.Ready -> {
          Text(
            text = "Ready to connect!",
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )

          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = onJoinCall,
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = "Start Call",
              fontSize = 18.sp
            )
          }
        }
        WebRTCSessionState.Creating -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Waiting for someone to join...",
              fontSize = 16.sp
            )
          }
        }
        WebRTCSessionState.Active -> {
          // If we're in Active state, automatically trigger the call join
          LaunchedEffect(state) {
            onJoinCall()
          }

          Text(
            text = "Connection established! Starting call...",
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(16.dp))

          CircularProgressIndicator()
        }
        WebRTCSessionState.Impossible -> {
          Text(
            text = "Connection not possible",
            color = MaterialTheme.colors.error,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }
        else -> {
          // Other states
        }
      }
    } ?: run {
      // No code yet, show loading
      CircularProgressIndicator(
        modifier = Modifier.padding(24.dp)
      )
    }

    // Back button (only show if not ready to call or active)
    if (state != WebRTCSessionState.Ready && state != WebRTCSessionState.Active) {
      Spacer(modifier = Modifier.height(24.dp))

      OutlinedButton(
        onClick = onBackClicked,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Go Back")
      }
    }
  }
}

@Composable
private fun JoinModeContent(
  state: WebRTCSessionState,
  inputCode: String,
  matchedCode: String?,
  onCodeChanged: (String) -> Unit,
  onJoinClicked: () -> Unit,
  onBackClicked: () -> Unit,
  onJoinCall: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Video Connect",
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colors.primary,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Text(
      text = "Enter Meeting Code",
      fontSize = 16.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
      modifier = Modifier.padding(bottom = 16.dp)
    )

    OutlinedTextField(
      value = inputCode,
      onValueChange = onCodeChanged,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text("6-digit code") },
      keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Done
      ),
      keyboardActions = KeyboardActions(
        onDone = {
          if (inputCode.length >= 6) {
            onJoinClicked()
          }
        }
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = onJoinClicked,
      enabled = inputCode.length >= 6,
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
      shape = RoundedCornerShape(8.dp)
    ) {
      Text(
        text = "Join Meeting",
        fontSize = 18.sp
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Status based on session state (after attempting to join)
    if (matchedCode != null && matchedCode == inputCode) {
      when (state) {
        WebRTCSessionState.Ready -> {
          Text(
            text = "Ready to connect!",
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 16.dp)
          )

          Button(
            onClick = onJoinCall,
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = "Start Call",
              fontSize = 18.sp
            )
          }
        }
        WebRTCSessionState.Creating -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 16.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Joining meeting...",
              fontSize = 16.sp
            )
          }
        }
        WebRTCSessionState.Active -> {
          // If we're in Active state, automatically trigger the call join
          LaunchedEffect(state) {
            onJoinCall()
          }

          Text(
            text = "Connection established! Starting call...",
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
          )

          CircularProgressIndicator()
        }
        WebRTCSessionState.Impossible -> {
          Text(
            text = "Could not join meeting",
            color = MaterialTheme.colors.error,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 16.dp)
          )
        }
        else -> {
          // Other states
        }
      }
    }

    // Back button (only show if not ready to call or active)
    if (state != WebRTCSessionState.Ready && state != WebRTCSessionState.Active) {
      Spacer(modifier = Modifier.height(16.dp))

      OutlinedButton(
        onClick = onBackClicked,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Go Back")
      }
    }
  }
}

// Function to generate a random connection code
// In a real app, this would be generated on the server
private fun generateConnectionCode(): String {
  val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed similar-looking characters
  return (1..6)
    .map { allowedChars.random() }
    .joinToString("")
}