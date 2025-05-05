package io.getstream.webrtc.sample.compose.webrtc.audio

import io.getstream.log.taggedLogger
import org.webrtc.ExternalAudioProcessingFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom audio processor implementation that intercepts audio frames.
 * This class can perform analysis or delegate processing to native code.
 * Includes spectral gating for stronger noise reduction.
 */
class MyAudioProcessor : ExternalAudioProcessingFactory.AudioProcessing {

  private val logger by taggedLogger("Call:MyAudioProcessor")
  private var sampleRateHz: Int = 0
  private var numChannels: Int = 0
  private var initialized = false
  private var nativeLibraryLoaded = false

  // Noise reduction parameters
  private var noiseSuppressionStrength: Float = 1.5f
  private var gateThreshold: Float = 0.02f  // Threshold for spectral gating
  private var useSpectralGating: Boolean = true  // Flag to enable/disable spectral gating

  // Load your native library if filtering is done in C++
  init {
    try {
      System.loadLibrary("rnnoise_filter_lib")
      logger.i { "Native filter library 'rnnoise_filter_lib' loaded successfully." }
      nativeLibraryLoaded = true
    } catch (e: UnsatisfiedLinkError) {
      logger.e(e) { "Failed to load native filter library 'rnnoise_filter_lib'! Processing will be skipped." }
      nativeLibraryLoaded = false
    }
  }

  /**
   * Called by WebRTC native code to initialize the processor.
   * @param sampleRateHz The sample rate of the audio stream (e.g., 48000).
   * @param numChannels The number of audio channels (e.g., 1 for mono).
   */
  override fun initialize(sampleRateHz: Int, numChannels: Int) {
    logger.i { "Initializing AudioProcessor: SampleRate=$sampleRateHz, Channels=$numChannels" }
    this.sampleRateHz = sampleRateHz
    this.numChannels = numChannels
    initialized = true
  }

  /**
   * Called by WebRTC native code when the audio format might change (e.g., resampling).
   * @param newRate The new sample rate.
   */
  override fun reset(newRate: Int) {
    logger.i { "Resetting AudioProcessor: New SampleRate=$newRate" }
    this.sampleRateHz = newRate
    initialized = true // Should remain initialized
  }

  /**
   * Set the noise suppression strength.
   * Higher values = more aggressive noise reduction.
   * @param strength Noise suppression strength (recommended range: 1.0 to 3.0)
   */
  fun setNoiseSuppressionStrength(strength: Float) {
    if (strength < 0.1f) {
      noiseSuppressionStrength = 0.1f
    } else if (strength > 5.0f) {
      noiseSuppressionStrength = 5.0f
    } else {
      noiseSuppressionStrength = strength
    }
    logger.i { "Noise suppression strength set to: $noiseSuppressionStrength" }
  }

  /**
   * Set the threshold for spectral gating.
   * Lower values = more sensitive gating (will silence more audio).
   * @param threshold Gate threshold (recommended range: 0.01 to 0.05)
   */
  fun setGateThreshold(threshold: Float) {
    if (threshold < 0.001f) {
      gateThreshold = 0.001f
    } else if (threshold > 0.1f) {
      gateThreshold = 0.1f
    } else {
      gateThreshold = threshold
    }
    logger.i { "Spectral gate threshold set to: $gateThreshold" }
  }

  /**
   * Enable or disable spectral gating.
   * @param enabled Whether to use spectral gating
   */
  fun setSpectralGatingEnabled(enabled: Boolean) {
    useSpectralGating = enabled
    logger.i { "Spectral gating ${if (enabled) "enabled" else "disabled"}" }
  }

  /**
   * Called frequently by WebRTC native code with chunks of audio data.
   * @param numBands Number of frequency bands (often 1 for full band).
   * @param numFrames Number of audio samples per channel in this chunk (e.g., 480 for 10ms at 48kHz).
   * @param buffer The ByteBuffer containing the raw PCM16 audio data. This buffer's
   *        content WILL BE MODIFIED if native filtering is successful and returns true.
   */
  override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
    if (!initialized || !nativeLibraryLoaded) {
      // Skip processing if not initialized or native lib failed to load
      if (!initialized) logger.w { "Process called before initialization." }
      return
    }

    // Ensure the buffer is direct or copy data if needed for native processing
    // Ensure byte order is Little Endian (standard for PCM16)
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    val success: Boolean
    if (buffer.isDirect) {
      // Process with native code including strength parameter and spectral gating
      success = applyNativeFilterDirect(
        buffer,
        buffer.remaining(),
        sampleRateHz,
        numChannels,
        noiseSuppressionStrength,
        gateThreshold,
        useSpectralGating
      )
    } else if (buffer.hasArray() && buffer.arrayOffset() == 0) {
      // Process array-backed buffer with spectral gating
      val audioData = buffer.array()
      val dataSize = buffer.remaining()
      success = applyNativeFilterArray(
        audioData,
        dataSize,
        sampleRateHz,
        numChannels,
        noiseSuppressionStrength,
        gateThreshold,
        useSpectralGating
      )
    } else {
      // Fallback: Copy data if it's not direct and not a simple backing array
      logger.v { "Buffer is non-direct/offset, copying data for JNI ($numFrames frames)..." }
      val audioData = ByteArray(buffer.remaining())
      val currentPosition = buffer.position() // Remember position
      buffer.get(audioData)

      success = applyNativeFilterArray(
        audioData,
        audioData.size,
        sampleRateHz,
        numChannels,
        noiseSuppressionStrength,
        gateThreshold,
        useSpectralGating
      )

      if (success) {
        // Copy modified data back into the original buffer
        buffer.position(currentPosition) // Reset position to overwrite correctly
        buffer.put(audioData)
      }
    }

    if (!success) {
      logger.w { "Native filtering returned false for $numFrames frames." }
    }

    // Buffer position might be modified by get/put or native code, rewind if needed
    // although WebRTC likely handles it.
    buffer.rewind()
  }

  /**
   * Calls the native C++ function to process audio data in a byte array.
   * The native code should modify the data in-place.
   */
  private external fun applyNativeFilterArray(
    audioData: ByteArray,
    size: Int,
    sampleRate: Int,
    channels: Int,
    strength: Float = noiseSuppressionStrength,
    gateThreshold: Float = this.gateThreshold,
    useSpectralGating: Boolean = this.useSpectralGating
  ): Boolean

  /**
   * Calls the native C++ function to process audio data in a direct ByteBuffer.
   * The native code should modify the data in-place.
   */
  private external fun applyNativeFilterDirect(
    buffer: ByteBuffer, // Pass the direct buffer
    size: Int,
    sampleRate: Int,
    channels: Int,
    strength: Float = noiseSuppressionStrength,
    gateThreshold: Float = this.gateThreshold,
    useSpectralGating: Boolean = this.useSpectralGating
  ): Boolean

  /**
   * Clean up resources when the processor is no longer needed.
   */
  fun cleanup() {
    logger.i { "Cleaning up MyAudioProcessor" }
    initialized = false
  }
}