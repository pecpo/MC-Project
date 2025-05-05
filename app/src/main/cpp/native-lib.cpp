// Location: app/src/main/cpp/native-lib.cpp

#include <jni.h>
#include <cstdint>
#include <vector>
#include <cmath>
#include <android/log.h>

// Include the public RNNoise API header
#include "rnnoise.h" // Assumes rnnoise/include is in CMake include path

#define LOG_TAG "NativeAudioFilterRN"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// RNNoise State (static to keep it within this file's scope)
static DenoiseState* rnnoise_state = nullptr;

// RNNoise processes audio in frames of 480 samples (10ms @ 48kHz)
#ifndef RNNOISE_FRAME_SIZE
#define RNNOISE_FRAME_SIZE 480
#endif

// --- Helper functions for data conversion ---
inline float s16_to_float(int16_t sample) {
    return static_cast<float>(sample) / 32768.0f;
}

inline int16_t float_to_s16(float sample) {
    float scaled = sample * 32767.0f;
    // Clamping
    if (scaled >= 32767.0f) return 32767;
    if (scaled <= -32768.0f) return -32768; // Use -32768 for lower bound
    return static_cast<int16_t>(scaled);
}


// --- JNI Lifecycle (Optional but Recommended) ---
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
LOGI("JNI_OnLoad: Initializing RNNoise state.");
if (rnnoise_state != nullptr) {
LOGW("RNNoise state already exists. Destroying previous.");
rnnoise_destroy(rnnoise_state);
}
rnnoise_state = rnnoise_create(nullptr); // Use default model
if (!rnnoise_state) {
LOGE("Failed to create RNNoise state!");
return JNI_ERR;
}
LOGI("RNNoise state created successfully.");
return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload: Destroying RNNoise state.");
    if (rnnoise_state) {
        rnnoise_destroy(rnnoise_state);
        rnnoise_state = nullptr;
        LOGI("RNNoise state destroyed.");
    }
}


// --- Core Processing Logic ---
/**
 * Processes a chunk of audio samples using RNNoise.
 * Modifies the 'samples' buffer in-place.
 * Handles mono or processes the first channel of stereo.
 */
bool process_audio_with_rnnoise(int16_t* samples, int numSamples, int sampleRate, int channels,
                                float strength, float gateThreshold, bool useSpectralGating) {
    if (!rnnoise_state) {
        LOGE("RNNoise state is null! Cannot process audio.");
        return false;
    }
    if (channels <= 0) {
        LOGE("Invalid channel count: %d", channels);
        return false;
    }
    if (numSamples <= 0) {
        LOGV("Empty audio chunk received.");
        return true; // Nothing to process
    }

    // RNNoise expects specific sample rates (e.g., 48k) and operates on mono frames.
    // Basic check - add resampling if needed for other rates.
    if (sampleRate != 48000) {
        LOGW("RNNoise running at %dHz, expected 48000Hz. Quality may be affected.", sampleRate);
        // TODO: Add resampling logic if supporting other rates is required.
    }

    // Temporary float buffers for processing one frame
    // Using static vectors might have slight performance edge over heap allocation
    // but are not thread-safe if JNI methods were called concurrently (not expected here).
    static std::vector<float> float_in(RNNOISE_FRAME_SIZE);
    static std::vector<float> float_out(RNNOISE_FRAME_SIZE);
    static std::vector<float> float_original(RNNOISE_FRAME_SIZE);

    int samples_per_channel = numSamples / channels;

    // Process frame by frame
    for (int frame_start_sample = 0; frame_start_sample < samples_per_channel; frame_start_sample += RNNOISE_FRAME_SIZE) {
        int samples_in_this_frame = std::min(RNNOISE_FRAME_SIZE, samples_per_channel - frame_start_sample);

        if (samples_in_this_frame <= 0) break;

        // 1. Convert S16 interleaved chunk to Float mono chunk
        for (int i = 0; i < samples_in_this_frame; ++i) {
            // Read from the first channel (index * channels)
            float_in[i] = s16_to_float(samples[(frame_start_sample + i) * channels]);
            float_original[i] = float_in[i]; // Save original for enhanced processing
        }
        // Zero-pad if it's the last, potentially partial frame
        for (int i = samples_in_this_frame; i < RNNOISE_FRAME_SIZE; ++i) {
            float_in[i] = 0.0f;
            float_original[i] = 0.0f;
        }

        // 2. Process with RNNoise (operates on the float buffers)
        rnnoise_process_frame(rnnoise_state, float_out.data(), float_in.data());

        // 3. Apply enhanced noise reduction with variable strength
        for (int i = 0; i < RNNOISE_FRAME_SIZE; ++i) {
            // Calculate how much signal was removed by RNNoise
            float noise_reduction = float_original[i] - float_out[i];

            // Apply more aggressive reduction by increasing the amount of noise removed
            float_out[i] = float_original[i] - (noise_reduction * strength);

            // Apply spectral gating if enabled
            if (useSpectralGating && std::abs(float_out[i]) < gateThreshold) {
                float_out[i] = 0.0f; // Gate very quiet signals completely
            }

            // Clamp to avoid distortion
            if (float_out[i] > 1.0f) float_out[i] = 1.0f;
            if (float_out[i] < -1.0f) float_out[i] = -1.0f;
        }

        // 4. Convert processed Float mono chunk back to S16 interleaved chunk
        for (int i = 0; i < samples_in_this_frame; ++i) {
            int sample_index = (frame_start_sample + i) * channels;
            samples[sample_index] = float_to_s16(float_out[i]);
            // Option: How to handle other channels if stereo?
            // - Zero them out: samples[sample_index + c] = 0;
            // - Copy original: (do nothing here if samples ptr is correct)
            // - Copy processed mono: samples[sample_index + c] = samples[sample_index];
            // For simplicity, we modify only the first channel here.
        }
    }

    return true; // Indicate success
}


// --- JNI Function Implementations ---
extern "C" {

/** JNI bridge for processing array-backed buffers */
JNIEXPORT jboolean JNICALL
Java_io_getstream_webrtc_sample_compose_webrtc_audio_MyAudioProcessor_applyNativeFilterArray(
        JNIEnv *env, jobject thiz, jbyteArray audioData, jint size, jint sampleRate, jint channels,
        jfloat strength, jfloat gateThreshold, jboolean useSpectralGating) {

    jbyte *dataPtr = env->GetByteArrayElements(audioData, nullptr);
    if (!dataPtr) {
        LOGE("applyNativeFilterArray: Failed to get byte array elements");
        return JNI_FALSE;
    }
    int16_t *samples = reinterpret_cast<int16_t *>(dataPtr);
    int numSamples = size / 2; // Assuming 16-bit samples

    bool success = process_audio_with_rnnoise(samples, numSamples, sampleRate, channels,
                                              strength, gateThreshold, useSpectralGating);

    // Release mode 0 copies changes back, JNI_ABORT discards changes
    env->ReleaseByteArrayElements(audioData, dataPtr, success ? 0 : JNI_ABORT);

    return success ? JNI_TRUE : JNI_FALSE;
}

/** JNI bridge for processing direct buffers */
JNIEXPORT jboolean JNICALL
Java_io_getstream_webrtc_sample_compose_webrtc_audio_MyAudioProcessor_applyNativeFilterDirect(
        JNIEnv *env, jobject thiz, jobject buffer, jint size, jint sampleRate, jint channels,
        jfloat strength, jfloat gateThreshold, jboolean useSpectralGating) {

    void *bufferPtr = env->GetDirectBufferAddress(buffer);
    if (!bufferPtr) {
        LOGE("applyNativeFilterDirect: Failed to get direct buffer address");
        return JNI_FALSE;
    }
    int16_t *samples = reinterpret_cast<int16_t *>(bufferPtr);
    int numSamples = size / 2; // Assuming 16-bit samples

    bool success = process_audio_with_rnnoise(samples, numSamples, sampleRate, channels,
                                              strength, gateThreshold, useSpectralGating);

    // Direct buffers are modified in place, no release needed to copy back

    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"