package io.getstream.webrtc.sample.compose

import android.app.Application
import io.getstream.log.android.AndroidStreamLogger

class WebRTCApp : Application() {

  override fun onCreate() {
    super.onCreate()

    AndroidStreamLogger.installOnDebuggableApp(this)
  }
}
