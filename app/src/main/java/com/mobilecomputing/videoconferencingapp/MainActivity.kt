package com.mobilecomputing.videoconferencingapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mobilecomputing.videoconferencingapp.ui.theme.VideoConferencingAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            style = MaterialTheme.typography.headlineMedium,
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
                style = MaterialTheme.typography.bodyLarge,
                color = if (status.contains("Signed in")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
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
            .setServerClientId("GOOGLE_WEB_CLIENT_ID") // replace with your actual Google client ID
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
                color = MaterialTheme.colorScheme.onSurface
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
    when (response.credential) {
        is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential -> {
            val googleCredential = response.credential as com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
            val idToken = googleCredential.idToken
            if (idToken != null) {
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            onSuccess("Signed in as ${auth.currentUser?.displayName}")
                        } else {
                            onFailure("Firebase auth failed: ${task.exception?.message}")
                        }
                    }
            } else {
                onFailure("No ID token received from Google")
            }
        }
        else -> {
            onFailure("Unexpected credential type: ${response.credential.javaClass.simpleName}")
        }
    }
}

private fun generateNonce(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

//@Preview(showBackground = true)
//@Composable
//fun PreviewGoogleSignInScreen() {
//    VideoConferencingAppTheme {
//        GoogleSignInScreen()
//    }
//}
