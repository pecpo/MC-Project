package io.getstream.webrtc.sample.compose.webrtc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.getstream.log.taggedLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TranscriptionManager(private val context: Context) {

  private val logger by taggedLogger("Call:TranscriptionManager")

  private var speechRecognizer: SpeechRecognizer? = null

  // StateFlow to expose the transcribed text to the UI
  private val _transcriptionFlow = MutableStateFlow<String>("")
  val transcriptionFlow: StateFlow<String> = _transcriptionFlow

  // StateFlow to track if transcription is active
  private val _isTranscribing = MutableStateFlow(false)
  val isTranscribing: StateFlow<Boolean> = _isTranscribing

  // Initialize the speech recognizer
  fun initialize() {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
      speechRecognizer?.setRecognitionListener(createRecognitionListener())
    } else {
      logger.e { "Speech recognition not available on this device" }
    }
  }

  // Start transcription
  fun startTranscription() {
    if (speechRecognizer == null) {
      initialize()
    }

    val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
      // For continuous recognition
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
    }

    try {
      _isTranscribing.value = true
      speechRecognizer?.startListening(recognizerIntent)
    } catch (e: Exception) {
      logger.e { "Error starting transcription: ${e.message}" }
      _isTranscribing.value = false
    }
  }

  // Stop transcription
  fun stopTranscription() {
    speechRecognizer?.stopListening()
    _isTranscribing.value = false
  }

  // Release resources
  fun release() {
    stopTranscription()
    speechRecognizer?.destroy()
    speechRecognizer = null
  }

  // Create recognition listener
  private fun createRecognitionListener(): RecognitionListener {
    return object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {
        logger.d { "Ready for speech" }
      }

      override fun onBeginningOfSpeech() {
        logger.d { "Beginning of speech" }
      }

      override fun onRmsChanged(rmsdB: Float) {
        // Not implementing, but can be used to show volume level
      }

      override fun onBufferReceived(buffer: ByteArray?) {
        // Not needed for basic implementation
      }

      override fun onEndOfSpeech() {
        logger.d { "End of speech" }
        // Restart listening for continuous transcription
        if (_isTranscribing.value) {
          startTranscription()
        }
      }

      override fun onError(error: Int) {
        val errorMessage = when (error) {
          SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
          SpeechRecognizer.ERROR_CLIENT -> "Client side error"
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
          SpeechRecognizer.ERROR_NETWORK -> "Network error"
          SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
          SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
          SpeechRecognizer.ERROR_SERVER -> "Server error"
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
          else -> "Unknown error"
        }
        logger.e { "Error in speech recognition: $errorMessage" }

        // Restart listening on certain errors
        if (_isTranscribing.value && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
          startTranscription()
        }
      }

      override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          val recognizedText = matches[0]
          logger.d { "Final transcription result: $recognizedText" }
          // Append to current text with a space
          _transcriptionFlow.value += " $recognizedText"
        }

        // Restart listening for continuous transcription
        if (_isTranscribing.value) {
          startTranscription()
        }
      }

      override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          val recognizedText = matches[0]
          logger.d { "Partial transcription result: $recognizedText" }
          // Show partial results
          _transcriptionFlow.value = recognizedText
        }
      }

      override fun onEvent(eventType: Int, params: Bundle?) {
        // Not needed for basic implementation
      }
    }
  }
}