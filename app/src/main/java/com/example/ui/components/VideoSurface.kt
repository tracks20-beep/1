package com.example.ui.components

import android.graphics.Color
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.AspectRatioMode

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    isMirrored: Boolean = false,
    keepScreenOn: Boolean = true
) {
    val boxModifier = when (aspectRatioMode) {
        AspectRatioMode.RATIO_16_9 -> modifier.aspectRatio(16f / 9f)
        AspectRatioMode.RATIO_4_3 -> modifier.aspectRatio(4f / 3f)
        else -> modifier.fillMaxSize()
    }

    val resizeMode = when (aspectRatioMode) {
        AspectRatioMode.FIT, AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 ->
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = boxModifier.graphicsLayer {
                scaleX = if (isMirrored) -1f else 1f
            },
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_texture_player, null, false) as PlayerView
                view.setShutterBackgroundColor(Color.BLACK)
                view.keepScreenOn = keepScreenOn
                view.resizeMode = resizeMode
                view.player = player
                view
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
                if (view.resizeMode != resizeMode) {
                    view.resizeMode = resizeMode
                }
                view.keepScreenOn = keepScreenOn
            }
        )
    }
}
