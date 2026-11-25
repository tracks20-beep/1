package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.data.model.AspectRatioMode
import com.example.ui.components.VideoSurface
import com.example.ui.viewmodel.PlaybackUiState
import com.example.util.formatDuration
import com.example.util.formatSpeed
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode { NONE, BRIGHTNESS, VOLUME, SEEK }

@Composable
fun VideoPlayerView(
    player: Player?,
    playbackState: PlaybackUiState,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onMinimizeToPopup: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleMirrored: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onOpenBookmarksSheet: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isInPipMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                aspectRatioMode = AspectRatioMode.FIT,
                isMirrored = playbackState.isMirrored,
                keepScreenOn = true
            )
        }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPositionMs by remember(playbackState.positionMs) {
        mutableLongStateOf(playbackState.positionMs)
    }

    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    var currentBrightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial in 0f..1f) initial else 0.5f)
    }
    var gestureVolumeFloat by remember { mutableFloatStateOf(0f) }
    var gestureSeekStartMs by remember { mutableLongStateOf(0L) }
    var gestureSeekTargetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(showControls, playbackState.isPlaying, isSeeking, isLocked) {
        if (showControls && playbackState.isPlaying && !isSeeking && !isLocked) {
            delay(4000L)
            showControls = false
        }
    }

    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(700L)
            doubleTapFeedback = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        VideoSurface(
            player = player,
            modifier = Modifier.fillMaxSize(),
            aspectRatioMode = playbackState.aspectRatioMode,
            isMirrored = playbackState.isMirrored,
            keepScreenOn = true
        )

        // Gesture Detector Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            if (!isLocked) {
                                if (offset.x < widthPx / 2f) {
                                    onSeekBy(-10_000L)
                                    doubleTapFeedback = "-10초"
                                } else {
                                    onSeekBy(10_000L)
                                    doubleTapFeedback = "+10초"
                                }
                            }
                        }
                    )
                }
                .pointerInput(isLocked, playbackState.durationMs) {
                    if (isLocked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            gestureMode = GestureMode.NONE
                            gestureSeekStartMs = playbackState.positionMs
                            gestureSeekTargetMs = playbackState.positionMs
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            gestureVolumeFloat = curVol.toFloat() / maxVol.toFloat()
                            if (startOffset.x < 0f) Unit
                        },
                        onDragEnd = {
                            if (gestureMode == GestureMode.SEEK) {
                                onSeekTo(gestureSeekTargetMs)
                            }
                            gestureMode = GestureMode.NONE
                            gestureHudText = null
                            gestureHudIcon = null
                        },
                        onDragCancel = {
                            gestureMode = GestureMode.NONE
                            gestureHudText = null
                            gestureHudIcon = null
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        if (gestureMode == GestureMode.NONE) {
                            gestureMode = if (abs(dragAmount.x) > abs(dragAmount.y) * 1.2f) {
                                GestureMode.SEEK
                            } else if (change.position.x < widthPx / 2f) {
                                GestureMode.BRIGHTNESS
                            } else {
                                GestureMode.VOLUME
                            }
                        }

                        when (gestureMode) {
                            GestureMode.BRIGHTNESS -> {
                                val delta = -dragAmount.y / (heightPx * 0.65f)
                                currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = currentBrightness
                                    win.attributes = lp
                                }
                                gestureHudIcon = Icons.Rounded.BrightnessHigh
                                gestureHudText = "밝기 ${(currentBrightness * 100).roundToInt()}%"
                            }
                            GestureMode.VOLUME -> {
                                val delta = -dragAmount.y / (heightPx * 0.65f)
                                gestureVolumeFloat = (gestureVolumeFloat + delta).coerceIn(0f, 1f)
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val targetVol = (gestureVolumeFloat * maxVol).roundToInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                gestureHudIcon = Icons.Rounded.VolumeUp
                                gestureHudText = "음량 ${(gestureVolumeFloat * 100).roundToInt()}%"
                            }
                            GestureMode.SEEK -> {
                                val totalDur = playbackState.durationMs.coerceAtLeast(60_000L)
                                val seekSpan = totalDur.coerceAtMost(180_000L)
                                val deltaMs = ((dragAmount.x / widthPx) * seekSpan).toLong()
                                gestureSeekTargetMs = (gestureSeekTargetMs + deltaMs).coerceIn(0L, totalDur)
                                val diffSec = (gestureSeekTargetMs - gestureSeekStartMs) / 1000L
                                val sign = if (diffSec >= 0) "+" else ""
                                gestureHudIcon = null
                                gestureHudText = "${formatDuration(gestureSeekTargetMs)} (${sign}${diffSec}초)"
                            }
                            GestureMode.NONE -> Unit
                        }
                    }
                }
        )

        // Buffering Indicator
        if (playbackState.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Gesture / Double-Tap Feedback Pill
        val overlayFeedback = gestureHudText ?: doubleTapFeedback
        if (overlayFeedback != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureHudIcon?.let { icon ->
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = overlayFeedback,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Screen Locked Floating Unlock Button
        if (isLocked && showControls) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 32.dp)
                    .clickable { isLocked = false }
                    .testTag("unlock_screen_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = "화면 잠금 해제", tint = MaterialTheme.colorScheme.primary)
                    Text("잠금 해제", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Full HUD Controls
        AnimatedVisibility(
            visible = showControls && !isLocked,
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
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playbackState.currentMedia?.title ?: "재생 중",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (playbackState.abLoopStartMs != null) {
                                Text(
                                    text = if (playbackState.abLoopEndMs != null) {
                                        "구간반복 ${formatDuration(playbackState.abLoopStartMs)}~${formatDuration(playbackState.abLoopEndMs)}"
                                    } else {
                                        "A지점 ${formatDuration(playbackState.abLoopStartMs)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (playbackState.sleepTimerRemainingMs != null) {
                                Text(
                                    text = "타이머 ${formatDuration(playbackState.sleepTimerRemainingMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // In-App Resizable Popup Button
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onMinimizeToPopup)
                            .testTag("player_inapp_popup_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.BrandingWatermark,
                                contentDescription = "팝업 재생",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "팝업",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEnterSystemPip,
                        modifier = Modifier.testTag("player_system_pip_button")
                    ) {
                        Icon(
                            Icons.Rounded.PictureInPictureAlt,
                            contentDescription = "시스템 PIP",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            activity?.let { act ->
                                val isLandscape = widthPx > heightPx
                                act.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        },
                        modifier = Modifier.testTag("player_rotate_button")
                    ) {
                        Icon(
                            Icons.Rounded.ScreenRotation,
                            contentDescription = "화면 회전",
                            tint = Color.White
                        )
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "이전", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = { onSeekBy(-10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "10초 뒤로", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "일시정지" else "재생",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(onClick = { onSeekBy(10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "10초 앞으로", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "다음", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom Bar: Seekbar + Pro Features Strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val maxDur = playbackState.durationMs.coerceAtLeast(1L)
                    val shownPos = if (isSeeking) sliderPositionMs else playbackState.positionMs

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatDuration(shownPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = shownPos.toFloat().coerceIn(0f, maxDur.toFloat()),
                            onValueChange = {
                                isSeeking = true
                                sliderPositionMs = it.toLong()
                            },
                            onValueChangeFinished = {
                                onSeekTo(sliderPositionMs)
                                isSeeking = false
                            },
                            valueRange = 0f..maxDur.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatDuration(playbackState.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.Speed,
                                label = formatSpeed(playbackState.playbackSpeed),
                                onClick = onOpenSpeedSheet,
                                testTag = "tool_speed"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.AspectRatio,
                                label = playbackState.aspectRatioMode.label,
                                onClick = onCycleAspectRatio,
                                testTag = "tool_aspect_ratio"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.Lock,
                                label = "화면 잠금",
                                onClick = { isLocked = true },
                                testTag = "tool_lock"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.Flip,
                                label = if (playbackState.isMirrored) "반전 켜짐" else "좌우 반전",
                                active = playbackState.isMirrored,
                                onClick = onToggleMirrored,
                                testTag = "tool_mirror"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = if (playbackState.subtitlesEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                                label = "자막 토글",
                                active = playbackState.subtitlesEnabled && playbackState.currentMedia?.subtitleUriString != null,
                                onClick = onToggleSubtitles,
                                testTag = "tool_subtitle_toggle"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.ClosedCaption,
                                label = "자막 파일",
                                onClick = onPickSubtitleFile,
                                testTag = "tool_subtitle_pick"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.Bookmark,
                                label = "구간반복·북마크",
                                active = playbackState.abLoopStartMs != null,
                                onClick = onOpenBookmarksSheet,
                                testTag = "tool_bookmarks_ab"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.Rounded.Timer,
                                label = if (playbackState.sleepTimerRemainingMs != null) {
                                    formatDuration(playbackState.sleepTimerRemainingMs)
                                } else {
                                    "타이머"
                                },
                                active = playbackState.sleepTimerRemainingMs != null || playbackState.sleepAtEndOfTrack,
                                onClick = onOpenSleepTimerSheet,
                                testTag = "tool_sleep_timer"
                            )
                        }
                        item {
                            PlayerToolChip(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                label = "대기열 (${playbackState.queue.size})",
                                onClick = onOpenQueueSheet,
                                testTag = "tool_queue"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerToolChip(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
            )
        }
    }
}
