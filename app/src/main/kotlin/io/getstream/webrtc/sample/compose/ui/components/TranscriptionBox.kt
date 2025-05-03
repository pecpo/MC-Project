package io.getstream.webrtc.sample.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TranscriptionBox(
  text: String,
  isActive: Boolean,
  modifier: Modifier = Modifier
) {
  if (text.isNotEmpty() || isActive) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .background(
          color = Color.Black.copy(alpha = 0.6f),
          shape = RoundedCornerShape(8.dp)
        )
        .padding(8.dp)
    ) {
      Text(
        text = if (text.isEmpty() && isActive) "Listening..." else text,
        color = Color.White,
        style = MaterialTheme.typography.body1,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}