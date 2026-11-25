package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.example.data.local.BookmarkEntity
import com.example.data.model.MediaType
import com.example.data.model.SleepTimerOption
import com.example.ui.components.BookmarksAndAbLoopSheet
import com.example.ui.components.PlaybackSpeedSheet
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.viewmodel.PlaybackUiState

@Composable
fun PlayerScreen(
    player: Player?,
    playbackState: PlaybackUiState,
    bookmarks: List<BookmarkEntity>,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onMinimizeToPopup: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleMirrored: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onSetAbA: () -> Unit,
    onSetAbB: () -> Unit,
    onClearAb: () -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSelectSleepTimer: (SleepTimerOption) -> Unit,
    onSelectQueueIndex: (Int) -> Unit
) {
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = !isInPipMode) {
        onBack()
    }

    val isVideo = playbackState.currentMedia?.mediaType != MediaType.AUDIO

    if (isVideo) {
        VideoPlayerView(
            player = player,
            playbackState = playbackState,
            isInPipMode = isInPipMode,
            onBack = onBack,
            onMinimizeToPopup = onMinimizeToPopup,
            onEnterSystemPip = onEnterSystemPip,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onSeekBy = onSeekBy,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onCycleAspectRatio = onCycleAspectRatio,
            onToggleMirrored = onToggleMirrored,
            onToggleSubtitles = onToggleSubtitles,
            onPickSubtitleFile = onPickSubtitleFile,
            onOpenSpeedSheet = { showSpeedSheet = true },
            onOpenBookmarksSheet = { showBookmarksSheet = true },
            onOpenSleepTimerSheet = { showSleepSheet = true },
            onOpenQueueSheet = { showQueueSheet = true }
        )
    } else {
        AudioPlayerView(
            playbackState = playbackState,
            onCollapse = onBack,
            onToggleFavorite = onToggleFavorite,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onOpenSpeedSheet = { showSpeedSheet = true },
            onOpenBookmarksSheet = { showBookmarksSheet = true },
            onOpenSleepTimerSheet = { showSleepSheet = true },
            onOpenQueueSheet = { showQueueSheet = true }
        )
    }

    if (showSpeedSheet) {
        PlaybackSpeedSheet(
            currentSpeed = playbackState.playbackSpeed,
            onSelectSpeed = onSelectSpeed,
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            remainingMs = playbackState.sleepTimerRemainingMs,
            sleepAtEndOfTrack = playbackState.sleepAtEndOfTrack,
            onSelectOption = onSelectSleepTimer,
            onDismiss = { showSleepSheet = false }
        )
    }

    if (showBookmarksSheet) {
        BookmarksAndAbLoopSheet(
            currentPositionMs = playbackState.positionMs,
            abStartMs = playbackState.abLoopStartMs,
            abEndMs = playbackState.abLoopEndMs,
            bookmarks = bookmarks,
            onSetA = onSetAbA,
            onSetB = onSetAbB,
            onClearAb = onClearAb,
            onAddBookmark = onAddBookmark,
            onSeekTo = onSeekTo,
            onDeleteBookmark = onDeleteBookmark,
            onDismiss = { showBookmarksSheet = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.currentQueueIndex,
            onSelectIndex = onSelectQueueIndex,
            onDismiss = { showQueueSheet = false }
        )
    }
}
