package io.getstream.webrtc.sample.compose.webrtc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A VideoProcessor implementation that blurs the background while keeping human subjects clear.
 * Uses ML Kit for segmentation and a custom blur implementation.
 */
class BackgroundBlurProcessor(private val context: Context) : VideoProcessor {
  companion object {
    private const val TAG = "BackgroundBlurProcessor"
    // Reduced blur radius for less aggressive effect
    private const val BLUR_RADIUS = 10f
    private const val SKIP_FRAMES = 5 // Process every 5th frame to improve performance

    // Debug flag - set to true to enable color correction logging
    private const val DEBUG_COLOR = false
  }

  private var videoSink: VideoSink? = null
  private val processLock = Object()

  // ML Kit segmenter for person detection
  private val segmenter: Segmenter by lazy {
    val options = SelfieSegmenterOptions.Builder()
      .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
      .build()
    Segmentation.getClient(options)
  }

  // Track the last mask
  private var lastMask: Bitmap? = null

  // Add frame counter to implement SKIP_FRAMES
  private var frameCounter = 0

  // Add mask blending factor for smoother transitions
  private var maskBlendFactor = 0.3f

  // Keep track of timestamps to ensure monotonically increasing values
  private var lastTimestampNs: Long = 0

  override fun onCapturerStarted(success: Boolean) {
    Log.d(TAG, "Capturer started: $success")
    lastTimestampNs = 0 // Reset timestamp tracking
    frameCounter = 0
  }

  override fun onCapturerStopped() {
    try {
      segmenter.close()
      videoSink = null
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping processor", e)
    }
  }

  override fun setSink(sink: VideoSink?) {
    videoSink = sink
  }

  override fun onFrameCaptured(frame: VideoFrame) {
    processFrame(frame)
  }

  override fun onFrameCaptured(frame: VideoFrame, parameters: VideoProcessor.FrameAdaptationParameters) {
    // Apply frame adaptation if needed
    val adaptedFrame = VideoProcessor.applyFrameAdaptationParameters(frame, parameters)
    if (adaptedFrame != null) {
      processFrame(adaptedFrame)
      adaptedFrame.release()
    } else {
      // Fallback to original frame if adaptation fails
      processFrame(frame)
    }
  }

  private fun processFrame(frame: VideoFrame) {
    try {
      // Implement frame skipping
      frameCounter = (frameCounter + 1) % SKIP_FRAMES
      val shouldProcess = frameCounter == 0

      // Get the original timestamp
      var originalTimestampNs = frame.timestampNs

      // Ensure monotonically increasing timestamps
      synchronized(processLock) {
        if (lastTimestampNs > 0 && originalTimestampNs <= lastTimestampNs) {
          // If this frame's timestamp isn't greater than the last one,
          // adjust it to be 1ms after the last timestamp
          originalTimestampNs = lastTimestampNs + 1_000_000 // Add 1ms in nanoseconds
          Log.d(TAG, "Adjusted timestamp: original=${frame.timestampNs}, adjusted=$originalTimestampNs")
        }
        lastTimestampNs = originalTimestampNs
      }

      // If we should skip processing this frame and don't have a previous mask, just pass it through
      if (!shouldProcess && lastMask == null) {
        videoSink?.onFrame(frame)
        return
      }

      // For debugging color issues - if enabled, pass unmodified frames through occasionally
      if (DEBUG_COLOR && frameCounter == 2) {
        Log.d(TAG, "DEBUG: Passing through original frame for color comparison")
        videoSink?.onFrame(frame)
        return
      }

      // Convert frame to bitmap for processing
      val bitmap = frameToBitmap(frame)
      if (bitmap != null) {
        if (DEBUG_COLOR) {
          // Log some pixel values for debugging
          val centerX = bitmap.width / 2
          val centerY = bitmap.height / 2
          val pixel = bitmap.getPixel(centerX, centerY)
          Log.d(TAG, "DEBUG: Center pixel RGB: r=${Color.red(pixel)}, g=${Color.green(pixel)}, b=${Color.blue(pixel)}")
        }

        // Process within synchronized block to maintain timestamp order
        synchronized(processLock) {
          try {
            val processedFrame = blurBackground(frame, bitmap, originalTimestampNs, shouldProcess)
            videoSink?.onFrame(processedFrame)
            // We don't release processedFrame here - the videoSink takes ownership
          } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}", e)
            // If timestamp changed, create a frame with the adjusted timestamp
            if (originalTimestampNs != frame.timestampNs) {
              try {
                val i420Buffer = frame.buffer.toI420()
                if (i420Buffer != null) {
                  val adjustedFrame = VideoFrame(i420Buffer, frame.rotation, originalTimestampNs)
                  videoSink?.onFrame(adjustedFrame)
                  // The videoSink takes ownership
                } else {
                  videoSink?.onFrame(frame)
                }
              } catch (e2: Exception) {
                Log.e(TAG, "Error creating fallback frame: ${e2.message}", e2)
                videoSink?.onFrame(frame)
              }
            } else {
              videoSink?.onFrame(frame)
            }
          }
        }
      } else {
        // If bitmap conversion failed but timestamp changed, create adjusted frame
        if (originalTimestampNs != frame.timestampNs) {
          try {
            val i420Buffer = frame.buffer.toI420()
            if (i420Buffer != null) {
              val adjustedFrame = VideoFrame(i420Buffer, frame.rotation, originalTimestampNs)
              videoSink?.onFrame(adjustedFrame)
              // The videoSink takes ownership
            } else {
              videoSink?.onFrame(frame)
            }
          } catch (e: Exception) {
            Log.e(TAG, "Error creating adjusted frame: ${e.message}", e)
            videoSink?.onFrame(frame)
          }
        } else {
          videoSink?.onFrame(frame)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in processFrame: ${e.message}", e)
      videoSink?.onFrame(frame)
    }
  }

  private fun frameToBitmap(frame: VideoFrame): Bitmap? {
    try {
      val buffer = frame.buffer
      val width = buffer.width
      val height = buffer.height

      // Convert I420 buffer to bitmap
      val i420Buffer = buffer.toI420() ?: return null

      // Create a mutable bitmap that will hold our color data
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

      // Get the YUV data from the I420 buffer
      val yBuffer = i420Buffer.dataY
      val uBuffer = i420Buffer.dataU
      val vBuffer = i420Buffer.dataV
      val yStride = i420Buffer.strideY
      val uStride = i420Buffer.strideU
      val vStride = i420Buffer.strideV

      // Allocate an array for the bitmap pixels
      val pixels = IntArray(width * height)

      // Convert YUV to RGB directly
      for (j in 0 until height) {
        for (i in 0 until width) {
          val yIndex = j * yStride + i
          // For U and V, we're dealing with half-resolution in both dimensions
          val uvIndex = (j / 2) * (uStride) + (i / 2)

          // Get Y, U, V values
          val y = yBuffer.get(yIndex).toInt() and 0xFF
          val u = uBuffer.get(uvIndex).toInt() and 0xFF
          val v = vBuffer.get(uvIndex).toInt() and 0xFF

          // YUV to RGB conversion
          // Adjust for YUV offset
          val yValue = y - 16
          val uValue = u - 128
          val vValue = v - 128

          // BT.601 standard conversion
          var r = (1.164 * yValue + 1.596 * vValue).toInt()
          var g = (1.164 * yValue - 0.813 * vValue - 0.391 * uValue).toInt()
          var b = (1.164 * yValue + 2.018 * uValue).toInt()

          // Clamp RGB values
          r = r.coerceIn(0, 255)
          g = g.coerceIn(0, 255)
          b = b.coerceIn(0, 255)

          // Create ARGB pixel
          pixels[j * width + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
      }

      // Set the pixels to the bitmap
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

      i420Buffer.release()
      return bitmap
    } catch (e: Exception) {
      Log.e(TAG, "Error converting frame to bitmap: ${e.message}", e)
      return null
    }
  }

  private fun blurBackground(frame: VideoFrame, bitmap: Bitmap, originalTimestampNs: Long, shouldUpdateMask: Boolean): VideoFrame {
    val width = bitmap.width
    val height = bitmap.height

    // Only update the segmentation mask if we should process this frame
    var mask = lastMask
    if (shouldUpdateMask) {
      try {
        // Create input image for ML Kit
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Process the image with ML Kit
        val segmentationTask = segmenter.process(inputImage)
        val segmentationResult = Tasks.await(segmentationTask)

        // Create a new mask
        val newMask = createMaskFromSegmentation(segmentationResult, width, height)

        // If we have a previous mask, blend them for smoother transitions
        if (mask != null) {
          mask = blendMasks(mask, newMask)
        } else {
          mask = newMask
        }

        lastMask = mask
      } catch (e: Exception) {
        Log.e(TAG, "Segmentation failed: ${e.message}", e)
        // If we have a previous mask, use it, otherwise just blur everything
        if (mask == null) {
          // Blur the entire image
          val blurred = blurBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, true), BLUR_RADIUS)
          val blurredFrame = bitmapToVideoFrame(
            blurred,
            frame,
            originalTimestampNs
          )
          blurred.recycle()
          return blurredFrame
        }
      }
    }

    // If we still don't have a mask, just return the original frame
    if (mask == null) {
      try {
        val i420Buffer = frame.buffer.toI420()
        if (i420Buffer != null) {
          return VideoFrame(i420Buffer, frame.rotation, originalTimestampNs)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error creating frame from original buffer", e)
      }
      return frame
    }

    // Apply blur effect with mask
    val result = applyBlurWithMask(bitmap, mask)

    // Convert back to VideoFrame
    val videoFrame = bitmapToVideoFrame(
      result,
      frame,
      originalTimestampNs
    )

    // Clean up
    result.recycle()

    return videoFrame
  }

  // New method to blend masks for smoother transitions
  private fun blendMasks(oldMask: Bitmap, newMask: Bitmap): Bitmap {
    val width = oldMask.width
    val height = oldMask.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    // Get pixels from both masks
    val oldPixels = IntArray(width * height)
    val newPixels = IntArray(width * height)
    val resultPixels = IntArray(width * height)

    oldMask.getPixels(oldPixels, 0, width, 0, 0, width, height)
    newMask.getPixels(newPixels, 0, width, 0, 0, width, height)

    // Blend the pixels
    for (i in oldPixels.indices) {
      val oldAlpha = Color.alpha(oldPixels[i])
      val newAlpha = Color.alpha(newPixels[i])

      // Weighted average of alpha values
      val blendedAlpha = (oldAlpha * (1 - maskBlendFactor) + newAlpha * maskBlendFactor).toInt()

      // Create new pixel with blended alpha
      resultPixels[i] = Color.argb(blendedAlpha, 0, 0, 0)
    }

    // Set the blended pixels to the result bitmap
    result.setPixels(resultPixels, 0, width, 0, 0, width, height)
    return result
  }

  private fun createMaskFromSegmentation(segmentationMask: SegmentationMask, width: Int, height: Int): Bitmap {
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    // Get confidence values for the "person" class (foreground)
    val buffer = segmentationMask.buffer
    val maskWidth = segmentationMask.width
    val maskHeight = segmentationMask.height

    // Scale factors if the mask size is different from bitmap size
    val scaleX = width.toFloat() / maskWidth
    val scaleY = height.toFloat() / maskHeight

    // Create an array for pixel values
    val pixels = IntArray(width * height)

    try {
      // Process each pixel
      for (y in 0 until height) {
        for (x in 0 until width) {
          val maskX = (x / scaleX).toInt().coerceIn(0, maskWidth - 1)
          val maskY = (y / scaleY).toInt().coerceIn(0, maskHeight - 1)

          // Calculate index into the buffer - ML Kit uses one float per pixel
          val index = maskY * maskWidth + maskX

          // Only read the buffer if we're within bounds
          if (index >= 0 && index < buffer.limit() / 4) {
            // Get confidence for foreground (person)
            // Each float is 4 bytes and we're reading the first component only
            val confidence = buffer.getFloat(index * 4)

            // Convert confidence to alpha (0 = transparent, 255 = opaque)
            val alpha = (confidence * 255).toInt().coerceIn(0, 255)

            // Create a pixel with the calculated alpha
            // Person detected (foreground) - make transparent (not to be blurred)
            // Background - make opaque (to be blurred)
            pixels[y * width + x] = if (alpha > 128) {
              Color.TRANSPARENT
            } else {
              Color.BLACK
            }
          } else {
            // Out of bounds - assume background
            pixels[y * width + x] = Color.BLACK
          }
        }
      }
    } catch (e: Exception) {
      // Handle any errors by filling with a safe default
      Log.e(TAG, "Error creating mask: ${e.message}", e)
      pixels.fill(Color.BLACK)
    }

    // Set the pixels to the mask bitmap
    mask.setPixels(pixels, 0, width, 0, 0, width, height)
    return mask
  }

  private fun blurBitmap(input: Bitmap, radius: Float): Bitmap {
    try {
      // Use a stack blur algorithm - CPU-based implementation
      // For better performance, we start with a scaled-down version
      val scale = 0.5f
      val width = (input.width * scale).toInt()
      val height = (input.height * scale).toInt()

      // Resize down for faster processing
      val scaled = Bitmap.createScaledBitmap(input, width, height, true)

      // Apply the blur effect
      val result = stackBlur(scaled, radius.toInt())

      // Scale back up to the original size
      val final = Bitmap.createScaledBitmap(result, input.width, input.height, true)

      // Clean up
      scaled.recycle()
      result.recycle()

      return final
    } catch (e: Exception) {
      Log.e(TAG, "Error blurring bitmap: ${e.message}", e)
      return input // Return original on error
    }
  }

  /**
   * Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
   * Adapted for simplicity and performance in Kotlin
   */
  private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
    var radius = radius
    if (radius < 1) return sentBitmap

    // Fix the type mismatch by wrapping with ?.let for null safety
    val bitmap = sentBitmap.config?.let { sentBitmap.copy(it, true) }
      ?: sentBitmap.copy(Bitmap.Config.ARGB_8888, true)

    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)

    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val div = radius + radius + 1
    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)

    var rsum: Int
    var gsum: Int
    var bsum: Int
    var x: Int
    var y: Int
    var i: Int
    var p: Int
    var yp: Int
    var yi: Int
    var yw: Int

    val vmin = IntArray(Math.max(w, h))
    var divsum = (div + 1) shr 1
    divsum *= divsum

    val dv = IntArray(256 * divsum)
    for (i in 0 until 256 * divsum) {
      dv[i] = i / divsum
    }

    yi = 0
    yw = 0

    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var rbs: Int
    val r1 = radius + 1

    var routsum: Int
    var goutsum: Int
    var boutsum: Int
    var rinsum: Int
    var ginsum: Int
    var binsum: Int

    for (y in 0 until h) {
      rinsum = 0
      ginsum = 0
      binsum = 0
      routsum = 0
      goutsum = 0
      boutsum = 0
      rsum = 0
      gsum = 0
      bsum = 0

      for (i in -radius..radius) {
        p = pixels[yi + (i.coerceIn(0, wm))]
        sir = stack[i + radius]
        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = p and 0x0000ff

        rbs = r1 - Math.abs(i)
        rsum += sir[0] * rbs
        gsum += sir[1] * rbs
        bsum += sir[2] * rbs

        if (i > 0) {
          rinsum += sir[0]
          ginsum += sir[1]
          binsum += sir[2]
        } else {
          routsum += sir[0]
          goutsum += sir[1]
          boutsum += sir[2]
        }
      }
      stackpointer = radius

      for (x in 0 until w) {
        r[yi] = dv[rsum]
        g[yi] = dv[gsum]
        b[yi] = dv[bsum]

        rsum -= routsum
        gsum -= goutsum
        bsum -= boutsum

        stackstart = stackpointer - radius + div
        sir = stack[stackstart % div]

        routsum -= sir[0]
        goutsum -= sir[1]
        boutsum -= sir[2]

        if (y == 0) {
          vmin[x] = Math.min(x + radius + 1, wm)
        }

        p = pixels[yw + vmin[x]]

        sir[0] = (p and 0xff0000) shr 16
        sir[1] = (p and 0x00ff00) shr 8
        sir[2] = p and 0x0000ff

        rinsum += sir[0]
        ginsum += sir[1]
        binsum += sir[2]

        rsum += rinsum
        gsum += ginsum
        bsum += binsum

        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer % div]

        routsum += sir[0]
        goutsum += sir[1]
        boutsum += sir[2]

        rinsum -= sir[0]
        ginsum -= sir[1]
        binsum -= sir[2]

        yi++
      }
      yw += w
    }

    for (x in 0 until w) {
      rinsum = 0
      ginsum = 0
      binsum = 0
      routsum = 0
      goutsum = 0
      boutsum = 0
      rsum = 0
      gsum = 0
      bsum = 0

      yp = -radius * w
      for (i in -radius..radius) {
        yi = Math.max(0, yp) + x

        sir = stack[i + radius]

        sir[0] = r[yi]
        sir[1] = g[yi]
        sir[2] = b[yi]

        rbs = r1 - Math.abs(i)

        rsum += r[yi] * rbs
        gsum += g[yi] * rbs
        bsum += b[yi] * rbs

        if (i > 0) {
          rinsum += sir[0]
          ginsum += sir[1]
          binsum += sir[2]
        } else {
          routsum += sir[0]
          goutsum += sir[1]
          boutsum += sir[2]
        }

        if (i < hm) {
          yp += w
        }
      }

      yi = x
      stackpointer = radius

      for (y in 0 until h) {
        pixels[yi] = (0xff000000.toInt() or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])

        rsum -= routsum
        gsum -= goutsum
        bsum -= boutsum

        stackstart = stackpointer - radius + div
        sir = stack[stackstart % div]

        routsum -= sir[0]
        goutsum -= sir[1]
        boutsum -= sir[2]

        if (x == 0) {
          vmin[y] = Math.min(y + r1, hm) * w
        }
        p = x + vmin[y]

        sir[0] = r[p]
        sir[1] = g[p]
        sir[2] = b[p]

        rinsum += sir[0]
        ginsum += sir[1]
        binsum += sir[2]

        rsum += rinsum
        gsum += ginsum
        bsum += binsum

        stackpointer = (stackpointer + 1) % div
        sir = stack[stackpointer]

        routsum += sir[0]
        goutsum += sir[1]
        boutsum += sir[2]

        rinsum -= sir[0]
        ginsum -= sir[1]
        binsum -= sir[2]

        yi += w
      }
    }

    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    return bitmap
  }

  private fun applyBlurWithMask(original: Bitmap, mask: Bitmap): Bitmap {
    try {
      // Create a blurred version of the original
      val blurred = blurBitmap(original.copy(Bitmap.Config.ARGB_8888, true), BLUR_RADIUS)

      // Create a result bitmap
      val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(result)

      // Always draw the original image first to keep the full color information
      canvas.drawBitmap(original, 0f, 0f, null)

      // Create separate Paint objects for clarity
      val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
      val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)

      // Create a temporary bitmap for the masked blur area
      val maskedBlur = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
      val maskCanvas = Canvas(maskedBlur)

      // First, draw the blur to this canvas
      maskCanvas.drawBitmap(blurred, 0f, 0f, null)

      // Then apply the mask - only keep blur where mask is BLACK (background)
      maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
      maskCanvas.drawBitmap(mask, 0f, 0f, maskPaint)

      // Now draw the masked blur on top of the original
      blurPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      canvas.drawBitmap(maskedBlur, 0f, 0f, blurPaint)

      // Clean up
      blurred.recycle()
      maskedBlur.recycle()

      return result
    } catch (e: Exception) {
      Log.e(TAG, "Error applying blur with mask: ${e.message}", e)
      // Return the original if there's an error
      return original.copy(Bitmap.Config.ARGB_8888, true)
    }
  }

  private fun bitmapToVideoFrame(bitmap: Bitmap, originalFrame: VideoFrame, originalTimestampNs: Long): VideoFrame {
    val width = bitmap.width
    val height = bitmap.height

    // Create a new I420Buffer
    val i420Buffer = JavaI420Buffer.allocate(width, height)

    // Convert bitmap to I420
    bitmapToI420(bitmap, i420Buffer)

    // Create a new VideoFrame with the ORIGINAL timestamp but our new buffer
    return VideoFrame(i420Buffer, originalFrame.rotation, originalTimestampNs)
  }

  private fun bitmapToI420(bitmap: Bitmap, i420Buffer: JavaI420Buffer) {
    val width = bitmap.width
    val height = bitmap.height

    // Get bitmap pixels
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    // Access I420 component buffers
    val yBuffer = i420Buffer.dataY
    val uBuffer = i420Buffer.dataU
    val vBuffer = i420Buffer.dataV
    val yStride = i420Buffer.strideY
    val uStride = i420Buffer.strideU
    val vStride = i420Buffer.strideV

    // Temporary arrays to hold the U and V values before averaging
    val uTemp = Array(height / 2) { FloatArray(width / 2) }
    val vTemp = Array(height / 2) { FloatArray(width / 2) }
    val uCount = Array(height / 2) { IntArray(width / 2) }
    val vCount = Array(height / 2) { IntArray(width / 2) }

    // Convert RGB to YUV (BT.601 full range standard)
    for (j in 0 until height) {
      for (i in 0 until width) {
        val pixelIndex = j * width + i
        val pixel = pixels[pixelIndex]

        // Extract RGB components
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Calculate Y (luma)
        // Y offset for "studio swing" of 16-235 is unnecessary for our purpose
        val y = ((0.299 * r + 0.587 * g + 0.114 * b)).toInt().coerceIn(0, 255)

        // Calculate U and V (chroma)
        // Store in accumulator arrays for downsampling later
        val uValue = (-0.14713 * r - 0.28886 * g + 0.436 * b + 128).toFloat()
        val vValue = (0.615 * r - 0.51499 * g - 0.10001 * b + 128).toFloat()

        // Store Y component in buffer
        yBuffer.put(j * yStride + i, y.toByte())

        // Accumulate U and V values for 4:2:0 downsampling
        val uRow = j / 2
        val uCol = i / 2
        uTemp[uRow][uCol] += uValue
        vTemp[uRow][uCol] += vValue
        uCount[uRow][uCol]++
        vCount[uRow][uCol]++
      }
    }

    // Write the averaged U and V values to the buffer
    for (j in 0 until height / 2) {
      for (i in 0 until width / 2) {
        val count = uCount[j][i].coerceAtLeast(1) // Avoid division by zero
        val u = (uTemp[j][i] / count).toInt().coerceIn(0, 255)
        val v = (vTemp[j][i] / count).toInt().coerceIn(0, 255)

        uBuffer.put(j * uStride + i, u.toByte())
        vBuffer.put(j * vStride + i, v.toByte())
      }
    }
  }
}