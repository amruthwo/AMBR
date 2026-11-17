package com.ambr.reloaded.ui.screens

import com.ambr.reloaded.util.str

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ambr.reloaded.R
import com.ambr.reloaded.ui.components.PixelField
import com.ambr.reloaded.ui.theme.Ambr

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ambr.Ink, Ambr.Night))),
    ) {
        PixelField(Modifier.fillMaxSize(), count = 30, intensity = 0.6f)
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.ambr_logo), null, Modifier.fillMaxWidth(0.55f))
            Spacer(Modifier.height(32.dp))
            Text(
                str(R.string.permission_feed_the_brain),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                str(R.string.permission_ambr_plays_the_videos_in) +
                    str(R.string.permission_it_needs_all_files_access),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onGrant,
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                modifier = Modifier.height(ButtonDefaults.LargeContainerHeight),
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight)))
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.LargeContainerHeight)))
                Text(str(R.string.permission_grant_access), style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
            }
        }
    }
}
