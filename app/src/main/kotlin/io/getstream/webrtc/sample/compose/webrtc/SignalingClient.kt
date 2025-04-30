package io.getstream.webrtc.sample.compose.webrtc

import android.util.Log
import io.getstream.log.taggedLogger
import io.getstream.webrtc.sample.compose.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class SignalingClient {
  private val TAG = "SignalingClient"
  private val logger by taggedLogger("Call:SignalingClient")
  private val signalingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val client = OkHttpClient.Builder()
    .pingInterval(15, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

  private val request = Request
    .Builder()
    .url(BuildConfig.SIGNALING_SERVER_IP_ADDRESS)
    .build()

  // opening web socket with signaling server
  private var ws: WebSocket? = null
  private val webSocketListener = SignalingWebSocketListener()

  init {
    Log.d(TAG, "Initializing SignalingClient with server: ${BuildConfig.SIGNALING_SERVER_IP_ADDRESS}")
    connectToSignalingServer()
  }

  private fun connectToSignalingServer() {
    Log.d(TAG, "Connecting to signaling server...")
    ws = client.newWebSocket(request, webSocketListener)
    Log.d(TAG, "WebSocket connection initiated")
  }

  // session flow to send information about the session state to the subscribers
  private val _sessionStateFlow = MutableStateFlow(WebRTCSessionState.Offline)
  val sessionStateFlow: StateFlow<WebRTCSessionState> = _sessionStateFlow

  // Connection code flow to share the current connection code
  private val _connectionCodeFlow = MutableStateFlow<String?>(null)
  val connectionCodeFlow: StateFlow<String?> = _connectionCodeFlow

  // Signal to notify when to start the call
  private val _startCallSignalFlow = MutableSharedFlow<Boolean>()
  val startCallSignalFlow: SharedFlow<Boolean> = _startCallSignalFlow

  // signaling commands to send commands to value pairs to the subscribers
  private val _signalingCommandFlow = MutableSharedFlow<Pair<SignalingCommand, String>>()
  val signalingCommandFlow: SharedFlow<Pair<SignalingCommand, String>> = _signalingCommandFlow

  // Connection result flow for success/failure of connection attempts
  private val _connectionResultFlow = MutableSharedFlow<ConnectionResult>()
  val connectionResultFlow: SharedFlow<ConnectionResult> = _connectionResultFlow

  fun sendCommand(signalingCommand: SignalingCommand, message: String) {
    val fullMessage = "$signalingCommand $message"
    Log.d(TAG, "Sending command: $fullMessage")

    ws?.send(fullMessage) ?: run {
      Log.e(TAG, "⚠️ WebSocket is null! Reconnecting and trying again...")
      connectToSignalingServer()
      // Give it a moment to connect before trying again
      signalingScope.launch {
        delay(1000)
        ws?.send(fullMessage) ?: Log.e(TAG, "❌ Still couldn't send message after reconnection attempt")
      }
    }
  }

  // Create a new session with a generated code
  fun createSession(connectionCode: String) {
    Log.d(TAG, "Creating session with code: $connectionCode")
    _connectionCodeFlow.value = connectionCode
    _sessionStateFlow.value = WebRTCSessionState.Creating
    sendCommand(SignalingCommand.CONNECTION, connectionCode)
  }

  // Join an existing session with a code
  fun joinSession(connectionCode: String) {
    Log.d(TAG, "Joining session with code: $connectionCode")
    _connectionCodeFlow.value = connectionCode
    _sessionStateFlow.value = WebRTCSessionState.Creating
    sendCommand(SignalingCommand.CONNECTION, connectionCode)
  }

  // Notify the other peer that we're starting the call
  fun notifyCallStarting() {
    Log.d(TAG, "Notifying peer that call is starting")
    sendCommand(SignalingCommand.START_CALL, "")

    // Also immediately emit the start call signal locally
    signalingScope.launch {
      _startCallSignalFlow.emit(true)
    }

    // Ensure state is set to Active (as a fallback)
    if (_sessionStateFlow.value != WebRTCSessionState.Active) {
      Log.d(TAG, "Forcing state to Active during call start")
      _sessionStateFlow.value = WebRTCSessionState.Active
    }
  }

  private inner class SignalingWebSocketListener : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      Log.d(TAG, "✅ WebSocket connection opened successfully")
      signalingScope.launch {
        _sessionStateFlow.value = WebRTCSessionState.Offline
        // Request current state
        sendCommand(SignalingCommand.STATE, "")
      }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      Log.d(TAG, "📩 Received message: $text")

      when {
        text.startsWith("WAITING_FOR_CONNECTION_CODE") -> {
          Log.d(TAG, "Server is waiting for connection code")
        }
        text.startsWith(SignalingCommand.STATE.toString(), true) -> {
          Log.d(TAG, "Received STATE message")
          handleStateMessage(text)
        }
        text.startsWith(SignalingCommand.CONNECTION_RESPONSE.toString(), true) -> {
          Log.d(TAG, "Received CONNECTION_RESPONSE message")
          handleConnectionResponse(text)
        }
        text.startsWith(SignalingCommand.START_CALL.toString(), true) -> {
          Log.d(TAG, "Received START_CALL message")
          handleStartCallMessage()
        }
        text.startsWith(SignalingCommand.OFFER.toString(), true) -> {
          Log.d(TAG, "Received OFFER message")
          handleSignalingCommand(SignalingCommand.OFFER, text)
        }
        text.startsWith(SignalingCommand.ANSWER.toString(), true) -> {
          Log.d(TAG, "Received ANSWER message")
          handleSignalingCommand(SignalingCommand.ANSWER, text)
        }
        text.startsWith(SignalingCommand.ICE.toString(), true) -> {
          Log.d(TAG, "Received ICE message")
          handleSignalingCommand(SignalingCommand.ICE, text)
        }
        else -> {
          Log.d(TAG, "⚠️ Received unknown message type: $text")
        }
      }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
      _sessionStateFlow.value = WebRTCSessionState.Offline
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      Log.e(TAG, "❌ WebSocket failure: ${t.message}, response=${response?.message}", t)
      _sessionStateFlow.value = WebRTCSessionState.Offline

      // Try to reconnect
      signalingScope.launch {
        delay(3000) // Wait 3 seconds before reconnecting
        Log.d(TAG, "Attempting to reconnect to signaling server...")
        connectToSignalingServer()
      }
    }
  }

  private fun handleStateMessage(message: String) {
    try {
      val state = getSeparatedMessage(message)
      Log.d(TAG, "Handling state message: $state")

      val newState = WebRTCSessionState.valueOf(state)
      Log.d(TAG, "Updated session state: $newState (was: ${_sessionStateFlow.value})")
      _sessionStateFlow.value = newState

      // Hack: If entering Active state, also emit the start call signal
      if (newState == WebRTCSessionState.Active) {
        Log.d(TAG, "State is now ACTIVE - emitting start call signal")
        signalingScope.launch {
          _startCallSignalFlow.emit(true)
        }
      }

    } catch (e: Exception) {
      Log.e(TAG, "Error handling state message", e)
    }
  }

  private fun handleConnectionResponse(message: String) {
    try {
      val response = getSeparatedMessage(message)
      Log.d(TAG, "Handling connection response: $response")

      val parts = response.split(" ", limit = 2)

      when (parts[0]) {
        "CONNECTED" -> {
          if (parts.size > 1) {
            val code = parts[1]
            Log.d(TAG, "Successfully connected to room: $code")
            _connectionCodeFlow.value = code
            signalingScope.launch {
              _connectionResultFlow.emit(ConnectionResult.Success(code))
            }
          } else {
            Log.e(TAG, "Connected response missing code parameter")
          }
        }
        "ROOM_FULL" -> {
          Log.d(TAG, "Room is full")
          signalingScope.launch {
            _connectionResultFlow.emit(ConnectionResult.Error("Room is full"))
          }
          _sessionStateFlow.value = WebRTCSessionState.Impossible
        }
        else -> {
          Log.d(TAG, "Unknown connection response: ${parts[0]}")
          signalingScope.launch {
            _connectionResultFlow.emit(ConnectionResult.Error("Unknown connection response"))
          }
          _sessionStateFlow.value = WebRTCSessionState.Impossible
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling connection response", e)
    }
  }

  private fun handleStartCallMessage() {
    Log.d(TAG, "Received start call signal from peer")
    signalingScope.launch {
      _startCallSignalFlow.emit(true)

      // Also ensure state is Active
      if (_sessionStateFlow.value != WebRTCSessionState.Active) {
        Log.d(TAG, "Forcing state to Active from peer START_CALL signal")
        _sessionStateFlow.value = WebRTCSessionState.Active
      }
    }
  }

  private fun handleSignalingCommand(command: SignalingCommand, text: String) {
    try {
      val value = getSeparatedMessage(text)
      Log.d(TAG, "Handling signaling command: $command")

      signalingScope.launch {
        _signalingCommandFlow.emit(command to value)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling signaling command", e)
    }
  }

  private fun getSeparatedMessage(text: String): String {
    val separated = text.substringAfter(' ')
    Log.d(TAG, "Parsed message content: $separated")
    return separated
  }

  fun dispose() {
    Log.d(TAG, "Disposing SignalingClient")
    _sessionStateFlow.value = WebRTCSessionState.Offline
    _connectionCodeFlow.value = null

    ws?.close(1000, "Client closed connection")
    ws = null

    signalingScope.cancel()
  }
}

enum class WebRTCSessionState {
  Active, // Offer and Answer messages has been sent
  Creating, // Creating session, offer has been sent
  Ready, // Both clients available and ready to initiate session
  Impossible, // We have less than two clients connected to the server
  Offline // unable to connect signaling server
}

enum class SignalingCommand {
  STATE, // Command for WebRTCSessionState
  CONNECTION, // for connecting to a specific room
  CONNECTION_RESPONSE, // response to connection attempt
  START_CALL, // signal to start the call (added)
  OFFER, // to send or receive offer
  ANSWER, // to send or receive answer
  ICE // to send and receive ice candidates
}

sealed class ConnectionResult {
  data class Success(val connectionCode: String) : ConnectionResult()
  data class Error(val message: String) : ConnectionResult()
}