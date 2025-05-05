import java.io.FileInputStream
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.android.application.get().pluginId)
  id(libs.plugins.kotlin.android.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
}

val localProperties = Properties()
localProperties.load(FileInputStream(rootProject.file("local.properties")))

android {
  namespace = "io.getstream.webrtc.sample.compose"
  compileSdk = Configurations.compileSdk

  defaultConfig {
    applicationId = "io.getstream.webrtc.sample.compose"
    minSdk = Configurations.minSdk
    targetSdk = Configurations.targetSdk
    versionCode = Configurations.versionCode
    versionName = Configurations.versionName

    buildConfigField(
      "String",
      "SIGNALING_SERVER_IP_ADDRESS",
      localProperties["SIGNALING_SERVER_IP_ADDRESS"].toString()
    )
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
  }

  lint {
    abortOnError = false
  }
}

dependencies {
  // compose
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.constraintlayout)

  // image loading
  implementation(libs.landscapist.glide)

  // webrtc
  implementation(libs.webrtc)
  implementation(libs.okhttp.logging)

  // coroutines
  implementation(libs.kotlinx.coroutines.android)

  // logger
  implementation(libs.stream.log)

  //theme
  implementation(libs.material)



  // Image processing
  implementation(libs.tensorflow.lite)
  implementation(libs.tensorflow.lite.gpu)
//  implementation(libs.segmentation.selfie.v1700)
//  implementation(libs.tasks.vision)
//  implementation("com.google.mediapipe:tasks-vision:0.10.10")
//  implementation(libs.play.services.mlkit.subject.segmentation)
  // ML Kit for segmentation
  implementation ("com.google.mlkit:segmentation-selfie:16.0.0-beta4")

// RenderScript for blur effects (usually included by default in Android)
//  implementation ("androidx.renderscript:renderscript:1.0.0")
  implementation ("jp.co.cyberagent.android:gpuimage:2.1.0")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.core.ktx)
  implementation(libs.vision.common)
  implementation(libs.androidx.compose.material.iconsExtended)
  implementation(libs.cronet.embedded)
}