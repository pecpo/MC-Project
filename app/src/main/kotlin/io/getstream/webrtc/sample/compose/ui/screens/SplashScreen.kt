package io.getstream.webrtc.sample.compose.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.getstream.webrtc.sample.compose.R
import kotlinx.coroutines.delay

private val BrandColor = Color(0xFF6469D9)
private val TextColor = Color.Black

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
  var startAnimation by remember { mutableStateOf(false) }
  var isVisible by remember { mutableStateOf(true) }

  val fadeOut = animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 300),
    finishedListener = { if (!isVisible) onSplashFinished() }
  )

  val logoAlpha = animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0f,
    animationSpec = tween(durationMillis = 1000)
  )

  val logoScale = animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0.3f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    )
  )

  val titleAlpha = animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0f,
    animationSpec = tween(durationMillis = 1000, delayMillis = 500)
  )

  val sloganAlpha = animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0f,
    animationSpec = tween(durationMillis = 1000, delayMillis = 1000)
  )

  val titleOffset = animateFloatAsState(
    targetValue = if (startAnimation) 0f else 50f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
    )
  )

  LaunchedEffect(key1 = true) {
    startAnimation = true
    delay(2000)
    isVisible = false
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .alpha(fadeOut.value)
      .background(
        Brush.verticalGradient(
          colors = listOf(Color.White, Color(0xFFF5F5FF))
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      Image(
        painter = painterResource(id = R.drawable.app_logo),
        contentDescription = "Logo",
        modifier = Modifier
          .size(180.dp)
          .alpha(logoAlpha.value)
          .scale(logoScale.value)
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Konnekt",
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        color = BrandColor,
        modifier = Modifier
          .alpha(titleAlpha.value)
          .offset(y = titleOffset.value.dp)
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Empowering Seamless Communication",
        fontSize = 18.sp,
        color = TextColor,
        modifier = Modifier.alpha(sloganAlpha.value)
      )
    }
  }
}