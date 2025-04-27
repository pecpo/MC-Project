/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.webrtc.sample.compose

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
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
import com.google.firebase.auth.FirebaseAuth

import android.content.Context
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CustomCredential
import io.getstream.webrtc.sample.compose.ui.theme.VideoConferencingAppTheme


class MainActivity : ComponentActivity() {

  private lateinit var auth: FirebaseAuth

  override fun onCreate(savedInstanceState: Bundle?) {
    auth = FirebaseAuth.getInstance()
    super.onCreate(savedInstanceState)

    requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)

    val sessionManager: WebRtcSessionManager = WebRtcSessionManagerImpl(
      context = this,
      signalingClient = SignalingClient(),
      peerConnectionFactory = StreamPeerConnectionFactory(this)
    )

    setContent {
      VideoConferencingAppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          GoogleSignInScreen(
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }

//    setContent {
//      WebrtcSampleComposeTheme {
//        CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
//          Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colors.background
//          ) {
//            var showSplash by remember { mutableStateOf(true) }
//            var onCallScreen by remember { mutableStateOf(false) }
//            val state by sessionManager.signalingClient.sessionStateFlow.collectAsState()
//
//            if (showSplash) {
//              SplashScreen { showSplash = false }
//            } else {
//              if (!onCallScreen) {
//                StageScreen(state = state) { onCallScreen = true }
//              } else {
//                VideoCallScreen()
//              }
//            }
//          }
//        }
//      }
//    }
//  }
}

@Composable
fun GoogleSignInScreen(modifier: Modifier = Modifier) {
  var authStatus by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // App Logo
//        Image(
//            painter = painterResource(id = R.drawable.app_logo), // Replace with your actual logo
//            contentDescription = "App Logo",
//            modifier = Modifier.size(120.dp)
//        )

    Spacer(modifier = Modifier.height(16.dp))

    // App Title
    Text(
      text = "Video Conferencing App",
//      style = MaterialTheme.typography.headlineMedium,
      fontSize = 24.sp
    )

    Spacer(modifier = Modifier.height(24.dp))

    GoogleSignInButton(
      onSignInSuccess = { message -> authStatus = message },
      onSignInFailure = { error -> authStatus = error }
    )

    authStatus?.let { status ->
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = status,
//        style = MaterialTheme.typography.bodyLarge,
        color = if (status.contains("Signed in")) {
          MaterialTheme.colors.primary
        } else {
          MaterialTheme.colors.error
        }
      )
    }
  }
}

@Composable
fun GoogleSignInButton(
  onSignInSuccess: (String) -> Unit,
  onSignInFailure: (String) -> Unit
) {
  val context = LocalContext.current
  val firebaseAuth = remember { FirebaseAuth.getInstance() }
  val credentialManager = remember { CredentialManager.create(context) }
  val coroutineScope = rememberCoroutineScope()

  suspend fun signInWithGoogle(
    context: Context,
    credentialManager: CredentialManager,
    auth: FirebaseAuth,
    onSignInSuccess: (String) -> Unit,
    onSignInFailure: (String) -> Unit
  ) {
    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false)
      .setServerClientId("GoogleWebClientID") // replace with your actual Google client ID
      .setNonce(generateNonce())
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    try {
      val response = credentialManager.getCredential(context, request)
      handleSignInResponse(response, auth, onSignInSuccess, onSignInFailure)
    } catch (e: Exception) {
      onSignInFailure("Sign-in failed: ${e.javaClass.simpleName} - ${e.localizedMessage}")
    }
  }

  Button(
    onClick = {
      coroutineScope.launch {
        signInWithGoogle(context, credentialManager, firebaseAuth, onSignInSuccess, onSignInFailure)
      }
    },
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .height(50.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.google_logo), // Replace with your actual Google logo
        contentDescription = "Google Logo",
        modifier = Modifier
          .size(40.dp)
          .padding(end = 8.dp)
      )

      Text(
        text = "Sign in with Google",
        fontSize = 18.sp,
        color = MaterialTheme.colors.onSurface
      )
    }
  }
//    {
//        Text(text = "Sign in with Google", fontSize = 18.sp)
//    }
}

private fun handleSignInResponse(
  response: GetCredentialResponse,
  auth: FirebaseAuth,
  onSuccess: (String) -> Unit,
  onFailure: (String) -> Unit
) {
  val credential = response.credential

  when {
    credential is CustomCredential &&
      credential.type == "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL" -> {
      try {
        // Parse the received CustomCredential as a GoogleIdTokenCredential
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken

        // Use the token to authenticate with Firebase
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
          .addOnCompleteListener { task ->
            if (task.isSuccessful) {
              onSuccess("Signed in as ${auth.currentUser?.displayName}")
            } else {
              onFailure("Firebase auth failed: ${task.exception?.message}")
            }
          }
      } catch (e: Exception) {
        onFailure("Failed to process Google ID token: ${e.message}")
      }
    }
    credential is GoogleIdTokenCredential -> {
      // This is the original path for handling GoogleIdTokenCredential directly
      val idToken = credential.idToken
      val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
      auth.signInWithCredential(firebaseCredential)
        .addOnCompleteListener { task ->
          if (task.isSuccessful) {
            onSuccess("Signed in as ${auth.currentUser?.displayName}")
          } else {
            onFailure("Firebase auth failed: ${task.exception?.message}")
          }
        }
    }
    else -> {
      onFailure("Unexpected credential type: ${credential.javaClass.simpleName}")
    }
  }
}


private fun generateNonce(): String {
  val bytes = ByteArray(16)
  java.security.SecureRandom().nextBytes(bytes)
  return bytes.joinToString("") { "%02x".format(it) }
}
