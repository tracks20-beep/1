package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.data.model.AspectRatioMode
import com.example.data.model.PopupSizePreset
import com.example.ui.viewmodel.PlaybackUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingVideoPopup(
    player: Player?,
    playbackState: PlaybackUiState,
    savedWidthDp: Float,
    onWidthChanged: (Float) -> Unit,
    onSelectPreset: (PopupSizePreset) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onExpandFullscreen: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val minWidthDp = 156f
        val maxWidthDp = (with(density) { constraints.maxWidth.toDp().value } - 24f).coerceAtLeast(220f)
        var currentWidthDp by remember(savedWidthDp) {
            mutableFloatStateOf(savedWidthDp.coerceIn(minWidthDp, maxWidthDp))
        }
        val aspect = playbackState.videoAspectRatio.coerceIn(0.6f, 2.2f)
        val currentHeightDp = (currentWidthDp / aspect).coerceIn(96f, 340f)

        val widthPx = with(density) { currentWidthDp.dp.toPx() }
        val heightPx = with(density) { currentHeightDp.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val bottomSafePx = with(density) { 96.dp.toPx() }

        var offsetX by remember {
            mutableFloatStateOf((constraints.maxWidth - widthPx - marginPx).coerceAtLeast(marginPx))
        }
        var offsetY by remember {
            mutableFloatStateOf((constraints.maxHeight - heightPx - bottomSafePx).coerceAtLeast(marginPx))
        }

        val maxOffsetX = (constraints.maxWidth - widthPx - marginPx).coerceAtLeast(marginPx)
        val maxOffsetY = (constraints.maxHeight - heightPx - marginPx).coerceAtLeast(marginPx)
        offsetX = offsetX.coerceIn(marginPx, maxOffsetX)
        offsetY = offsetY.coerceIn(marginPx, maxOffsetY)

        var showControls by remember { mutableStateOf(true) }
        var isResizing by remember { mutableStateOf(false) }

        LaunchedEffect(showControls, playbackState.isPlaying, isResizing) {
            if (showControls && playbackState.isPlaying && !isResizing) {
                delay(3200L)
                showControls = false
            }
        }

        val progress = if (playbackState.durationMs > 0L) {
            (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(currentWidthDp.dp)
                .height(currentHeightDp.dp)
                .shadow(18.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(
                    width = 1.5.dp,
                    color = if (isResizing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(maxOffsetX, maxOffsetY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxOffsetX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxOffsetY)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            isResizing = true
                            val next = (currentWidthDp * zoom).coerceIn(minWidthDp, maxWidthDp)
                            currentWidthDp = next
                            onWidthChanged(next)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { onExpandFullscreen() }
                    )
                }
                .testTag("floating_video_popup")
        ) {
            VideoSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                aspectRatioMode = AspectRatioMode.FIT,
                isMirrored = playbackState.isMirrored,
                keepScreenOn = true
            )

            AnimatedVisibility(
                visible = showControls || isResizing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                ) {
                    // Top action bar: Size presets + PiP + Expand + Close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PopupSizePreset.entries.forEach { preset ->
                                val selected = kotlin.math.abs(currentWidthDp - preset.widthDp) < 25f
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clickable {
                                            val clamped = preset.widthDp.coerceIn(minWidthDp, maxWidthDp)
                                            currentWidthDp = clamped
                                            onSelectPreset(preset)
                                        }
                                        .testTag("popup_size_${preset.name.lowercase()}")
                                ) {
                                    Text(
                                        text = when (preset) {
                                            PopupSizePreset.SMALL -> "S"
                                            PopupSizePreset.MEDIUM -> "M"
                                            PopupSizePreset.LARGE -> "L"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onEnterSystemPip,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("popup_system_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = "시스템 PIP",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onExpandFullscreen,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("popup_expand_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fullscreen,
                                    contentDescription = "전체 화면",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("popup_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "팝업 닫기",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Center transport controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSeekBy(-10_000L) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "10초 뒤로",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), CircleShape)
                                .testTag("popup_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "일시정지" else "재생",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { onSeekBy(10_000L) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "10초 앞으로",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Live size readout while resizing
                    if (isResizing) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${currentWidthDp.roundToInt()} × ${currentHeightDp.roundToInt()} dp",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom-right corner resize handle (always accessible)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(minWidthDp, maxWidthDp) {
                        detectDragGestures(
                            onDragStart = { isResizing = true },
                            onDragEnd = {
                                isResizing = false
                                onWidthChanged(currentWidthDp)
                            },
                            onDragCancel = { isResizing = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.x.toDp().value }
                            currentWidthDp = (currentWidthDp + deltaDp).coerceIn(minWidthDp, maxWidthDp)
                        }
                    }
                    .testTag("popup_resize_handle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "팝업 크기 조절 핸들",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Thin progress bar at bottom edge
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}
