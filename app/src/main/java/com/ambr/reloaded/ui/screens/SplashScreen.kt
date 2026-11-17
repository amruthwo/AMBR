package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ambr.reloaded.R
import com.ambr.reloaded.ui.components.PixelField
import com.ambr.reloaded.ui.theme.Ambr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val tilt = remember { Animatable(-8f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f)) }
        launch { tilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 180f)) }
        launch { delay(250); textAlpha.animateTo(1f, tween(450)) }
        delay(1400)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Ambr.Plum, Ambr.Ink, Ambr.Night), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        PixelField(Modifier.fillMaxSize(), count = 60, speed = 2.5f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painterResource(R.drawable.ambr_logo),
                contentDescription = "AMBR",
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value
                        rotationZ = tilt.value
                    },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                str(R.string.splash_airplane_mode_brain_rot),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Spacer(Modifier.height(28.dp))
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { alpha = textAlpha.value },
            )
        }
    }
}
