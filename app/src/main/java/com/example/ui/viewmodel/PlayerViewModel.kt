package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.VeloPlayApp
import com.example.data.local.BookmarkEntity
import com.example.data.local.FolderEntity
import com.example.data.local.MediaItemEntity
import com.example.data.local.PlaylistWithMedia
import com.example.data.model.AspectRatioMode
import com.example.data.model.LibraryFilter
import com.example.data.model.MediaType
import com.example.data.model.PopupSizePreset
import com.example.data.model.RepeatModeOption
import com.example.data.model.SleepTimerOption
import com.example.data.model.SortOption
import com.example.data.preferences.PlayerPreferencesState
import com.example.player.PlaybackService
import com.example.util.guessSubtitleMimeType
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FolderGroup(
    val id: String,
    val folderName: String,
    val treeUriString: String?,
    val isLinkedFolder: Boolean,
    val items: List<MediaItemEntity>
) {
    val videoCount: Int get() = items.count { it.mediaType == MediaType.VIDEO }
    val audioCount: Int get() = items.count { it.mediaType == MediaType.AUDIO }
    val totalDurationMs: Long get() = items.sumOf { it.durationMs }
    val hasVideo: Boolean get() = videoCount > 0
}

data class PlaybackUiState(
    val currentMedia: MediaItemEntity? = null,
    val queue: List<MediaItemEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val videoWidth: Int = 16,
    val videoHeight: Int = 9,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val shuffleEnabled: Boolean = false,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val isMirrored: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null,
    val sleepTimerRemainingMs: Long? = null,
    val sleepAtEndOfTrack: Boolean = false,
    val isPlayerScreenOpen: Boolean = false,
    val isFloatingPopupVisible: Boolean = false,
    val errorMessage: String? = null
) {
    val videoAspectRatio: Float
        get() = if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.45f, 2.35f)
        } else {
            16f / 9f
        }
}

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VeloPlayApp
    private val repository = app.repository
    private val preferences = app.preferences

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isScanningFolders = MutableStateFlow(false)
    val isScanningFolders: StateFlow<Boolean> = _isScanningFolders.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val prefsState: StateFlow<PlayerPreferencesState> = preferences.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerPreferencesState()
    )

    val allMedia: StateFlow<List<MediaItemEntity>> = repository.allMedia.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val linkedFolders: StateFlow<List<FolderEntity>> = repository.linkedFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val folderGroups: StateFlow<List<FolderGroup>> = combine(
        allMedia,
        linkedFolders
    ) { items, linked ->
        val groupedByKey = items.groupBy { item ->
            item.folderUriString?.let { "tree:$it::${item.folderName}" }
                ?: "group:${item.folderName}"
        }

        val groups = mutableListOf<FolderGroup>()

        // Ensure linked root folders show even if empty, plus all scanned sub-folders
        val representedTreeUris = mutableSetOf<String>()
        groupedByKey.forEach { (key, folderItems) ->
            val first = folderItems.first()
            first.folderUriString?.let { representedTreeUris.add(it) }
            groups.add(
                FolderGroup(
                    id = key,
                    folderName = first.folderName,
                    treeUriString = first.folderUriString,
                    isLinkedFolder = first.folderUriString != null,
                    items = folderItems.sortedBy { it.title.lowercase() }
                )
            )
        }

        linked.forEach { folder ->
            if (folder.treeUriString !in representedTreeUris) {
                groups.add(
                    FolderGroup(
                        id = "linked_empty:${folder.treeUriString}",
                        folderName = folder.folderName,
                        treeUriString = folder.treeUriString,
                        isLinkedFolder = true,
                        items = emptyList()
                    )
                )
            }
        }

        groups.sortedWith(
            compareByDescending<FolderGroup> { it.isLinkedFolder }
                .thenBy { it.folderName.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMedia: StateFlow<List<MediaItemEntity>> = combine(
        allMedia,
        _searchQuery,
        _libraryFilter,
        _sortOption
    ) { items, query, filter, sort ->
        val filtered = items.filter { item ->
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.VIDEO -> item.mediaType == MediaType.VIDEO
                LibraryFilter.AUDIO -> item.mediaType == MediaType.AUDIO
                LibraryFilter.FAVORITES -> item.isFavorite
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.artist.lowercase().contains(q) ||
                item.folderName.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
        when (sort) {
            SortOption.RECENT -> filtered.sortedWith(
                compareByDescending<MediaItemEntity> { it.lastPlayedAt }
                    .thenByDescending { it.addedAt }
            )
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.addedAt }
            SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithMedia>> = repository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val currentBookmarks: StateFlow<List<BookmarkEntity>> = _playbackState
        .flatMapLatest { state ->
            val id = state.currentMedia?.id
            if (id != null && id > 0L) repository.observeBookmarks(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var positionSaveCounter = 0
    private var sleepTimerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) saveCurrentPosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = (_mediaController.value?.duration ?: 0L).coerceAtLeast(0L)
                )
            }
            if (playbackState == Player.STATE_ENDED && _playbackState.value.sleepAtEndOfTrack) {
                _mediaController.value?.pause()
                cancelSleepTimer()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_playbackState.value.sleepAtEndOfTrack &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                _mediaController.value?.pause()
                cancelSleepTimer()
            }
            syncCurrentItemFromController()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _playbackState.update {
                    it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(errorMessage = "재생 오류: ${error.localizedMessage ?: "파일을 재생할 수 없습니다"}")
            }
        }
    }

    init {
        connectController()
        startPositionPoller()
        viewModelScope.launch {
            preferences.state.collect { prefs ->
                _playbackState.update {
                    it.copy(
                        playbackSpeed = prefs.playbackSpeed,
                        repeatMode = prefs.repeatMode,
                        shuffleEnabled = prefs.shuffleEnabled,
                        aspectRatioMode = prefs.defaultAspectRatio
                    )
                }
                _mediaController.value?.let { controller ->
                    applyPrefsToPlayer(controller, prefs)
                }
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val controller = future.get()
                _mediaController.value = controller
                controller.addListener(playerListener)
                applyPrefsToPlayer(controller, prefsState.value)
                syncCurrentItemFromController()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun applyPrefsToPlayer(controller: MediaController, prefs: PlayerPreferencesState) {
        controller.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
        controller.repeatMode = when (prefs.repeatMode) {
            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
        }
        controller.shuffleModeEnabled = prefs.shuffleEnabled
    }

    private fun startPositionPoller() {
        viewModelScope.launch {
            while (isActive) {
                val controller = _mediaController.value
                if (controller != null && controller.mediaItemCount > 0) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    val buf = controller.bufferedPosition.coerceAtLeast(0L)

                    val state = _playbackState.value
                    val startA = state.abLoopStartMs
                    val endB = state.abLoopEndMs
                    if (startA != null && endB != null && endB > startA && pos >= endB) {
                        controller.seekTo(startA)
                    }

                    _playbackState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0) dur else (it.currentMedia?.durationMs ?: 0L),
                            bufferedPositionMs = buf,
                            isPlaying = controller.isPlaying
                        )
                    }

                    if (controller.isPlaying) {
                        positionSaveCounter++
                        if (positionSaveCounter >= 12) {
                            positionSaveCounter = 0
                            saveCurrentPosition()
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun syncCurrentItemFromController() {
        val controller = _mediaController.value ?: return
        val index = controller.currentMediaItemIndex
        val mediaId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        val queue = _playbackState.value.queue
        val matched = queue.getOrNull(index)
            ?: mediaId?.let { id -> allMedia.value.find { it.id == id } }
        if (matched != null) {
            _playbackState.update {
                it.copy(
                    currentMedia = matched,
                    currentQueueIndex = index,
                    durationMs = controller.duration.takeIf { d -> d > 0 } ?: matched.durationMs,
                    videoWidth = if (matched.width > 0) matched.width else it.videoWidth,
                    videoHeight = if (matched.height > 0) matched.height else it.videoHeight,
                    abLoopStartMs = null,
                    abLoopEndMs = null,
                    errorMessage = null
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showMessage(message: String) {
        _statusBanner.value = message
    }

    fun importLocalFiles(uris: List<Uri>, autoPlayFirst: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.importLocalUris(uris) }
                .onSuccess { imported ->
                    if (imported.isNotEmpty()) {
                        _statusBanner.value = "${imported.size}개의 미디어가 추가되었습니다"
                        if (autoPlayFirst) {
                            playMedia(imported.first(), imported)
                        }
                    } else {
                        _statusBanner.value = "선택한 파일을 불러올 수 없습니다"
                    }
                }
                .onFailure {
                    _statusBanner.value = "파일을 불러오는 중 오류가 발생했습니다"
                }
        }
    }

    fun importFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _isScanningFolders.value = true
            runCatching { repository.importFolderTree(treeUri) }
                .onSuccess { result ->
                    _statusBanner.value = if (result.importedCount > 0) {
                        "'${result.folderName}' 폴더에서 ${result.importedCount}개의 미디어를 불러왔습니다"
                    } else {
                        "'${result.folderName}' 폴더가 연결되었습니다 (재생 가능한 파일 없음)"
                    }
                }
                .onFailure {
                    _statusBanner.value = "폴더를 스캔할 수 없습니다. 파일 추가를 이용해 주세요."
                }
            _isScanningFolders.value = false
        }
    }

    fun rescanAllFolders() {
        viewModelScope.launch {
            _isScanningFolders.value = true
            runCatching { repository.rescanAllLinkedFolders() }
                .onSuccess { count ->
                    _statusBanner.value = "연결된 폴더 새로고침 완료 (${count}개 파일 확인)"
                }
                .onFailure {
                    _statusBanner.value = "폴더 새로고침 중 오류가 발생했습니다"
                }
            _isScanningFolders.value = false
        }
    }

    fun removeFolderGroup(folder: FolderGroup, removeFiles: Boolean) {
        viewModelScope.launch {
            val currentId = _playbackState.value.currentMedia?.id
            val affectsCurrent = currentId != null && folder.items.any { it.id == currentId }
            val shouldStopCurrent = affectsCurrent && (removeFiles || !folder.isLinkedFolder)
            if (shouldStopCurrent) {
                _mediaController.value?.stop()
                _playbackState.update {
                    it.copy(
                        currentMedia = null,
                        isPlayerScreenOpen = false,
                        isFloatingPopupVisible = false
                    )
                }
            }
            if (folder.isLinkedFolder && folder.treeUriString != null) {
                repository.removeLinkedFolder(folder.treeUriString, removeFiles)
                _statusBanner.value = if (removeFiles) {
                    "'${folder.folderName}' 폴더와 포함된 목록을 삭제했습니다"
                } else {
                    "'${folder.folderName}' 폴더를 폴더 목록에서 제거했습니다"
                }
            } else {
                repository.removeAutoFolder(folder.folderName)
                _statusBanner.value = "'${folder.folderName}' 폴더 목록을 삭제했습니다"
            }
        }
    }

    fun removeFolderGroups(folders: List<FolderGroup>, removeFiles: Boolean) {
        if (folders.isEmpty()) return
        viewModelScope.launch {
            val currentId = _playbackState.value.currentMedia?.id
            val affectsCurrent = currentId != null && folders.any { folder ->
                (removeFiles || !folder.isLinkedFolder) && folder.items.any { it.id == currentId }
            }
            if (affectsCurrent) {
                _mediaController.value?.stop()
                _playbackState.update {
                    it.copy(
                        currentMedia = null,
                        isPlayerScreenOpen = false,
                        isFloatingPopupVisible = false
                    )
                }
            }
            folders.forEach { folder ->
                if (folder.isLinkedFolder && folder.treeUriString != null) {
                    repository.removeLinkedFolder(folder.treeUriString, removeFiles)
                } else {
                    repository.removeAutoFolder(folder.folderName)
                }
            }
            _statusBanner.value = "${folders.size}개 폴더가 목록에서 삭제되었습니다"
        }
    }

    fun playFolder(
        items: List<MediaItemEntity>,
        shuffle: Boolean = false,
        asPopup: Boolean = false
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (shuffle != prefsState.value.shuffleEnabled) {
                preferences.setShuffleEnabled(shuffle)
            }
            val ordered = if (asPopup) {
                val videos = items.filter { it.mediaType == MediaType.VIDEO }
                if (videos.isNotEmpty()) videos else items
            } else {
                items
            }
            val startItem = if (shuffle) ordered.random() else ordered.first()
            playMedia(
                item = startItem,
                queueItems = ordered,
                openPlayer = !asPopup,
                openAsFloatingPopup = asPopup
            )
        }
    }

    fun addAndPlayNetworkStream(url: String, title: String, mediaType: MediaType) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val item = repository.addNetworkStream(url, title, mediaType)
            _statusBanner.value = "'${item.title}' 스트림이 추가되었습니다"
            playMedia(item, listOf(item) + allMedia.value.filter { it.id != item.id })
        }
    }

    fun openExternalUri(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.importLocalUris(listOf(uri)) }
                .onSuccess { imported ->
                    imported.firstOrNull()?.let { item ->
                        playMedia(item, allMedia.value.ifEmpty { listOf(item) })
                    }
                }
                .onFailure {
                    _statusBanner.value = "외부 미디어를 열 수 없습니다"
                }
        }
    }

    fun playMedia(
        item: MediaItemEntity,
        queueItems: List<MediaItemEntity> = filteredMedia.value.ifEmpty { listOf(item) },
        openPlayer: Boolean = true,
        startFromBeginning: Boolean = false,
        openAsFloatingPopup: Boolean = false
    ) {
        val controller = _mediaController.value ?: return
        val normalizedQueue = if (queueItems.any { it.id == item.id }) queueItems else listOf(item) + queueItems
        val startIndex = normalizedQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)

        val mediaItems = normalizedQueue.map { buildMedia3Item(it) }
        val resumePos = if (!startFromBeginning &&
            prefsState.value.resumePlaybackPromptless &&
            item.lastPositionMs > 2000L &&
            (item.durationMs <= 0L || item.lastPositionMs < item.durationMs - 4000L)
        ) {
            item.lastPositionMs
        } else {
            0L
        }

        _playbackState.update {
            it.copy(
                currentMedia = item,
                queue = normalizedQueue,
                currentQueueIndex = startIndex,
                durationMs = item.durationMs,
                positionMs = resumePos,
                videoWidth = if (item.width > 0) item.width else 16,
                videoHeight = if (item.height > 0) item.height else 9,
                abLoopStartMs = null,
                abLoopEndMs = null,
                errorMessage = null,
                isPlayerScreenOpen = openPlayer && !openAsFloatingPopup,
                isFloatingPopupVisible = openAsFloatingPopup ||
                    (!openPlayer && it.isFloatingPopupVisible)
            )
        }

        controller.setMediaItems(mediaItems, startIndex, resumePos)
        controller.prepare()
        controller.play()

        viewModelScope.launch {
            repository.markPlayed(item.id)
        }
    }

    fun playPlaylist(playlist: PlaylistWithMedia, shuffle: Boolean = false) {
        if (playlist.items.isEmpty()) return
        viewModelScope.launch {
            if (shuffle != prefsState.value.shuffleEnabled) {
                preferences.setShuffleEnabled(shuffle)
            }
            val first = if (shuffle) playlist.items.random() else playlist.items.first()
            playMedia(first, playlist.items, openPlayer = true)
        }
    }

    fun addNextInQueue(item: MediaItemEntity) {
        val controller = _mediaController.value ?: return
        if (_playbackState.value.queue.isEmpty()) {
            playMedia(item, listOf(item), openPlayer = false)
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, buildMedia3Item(item))
        val updatedQueue = _playbackState.value.queue.toMutableList().apply {
            add(insertIndex.coerceAtMost(size), item)
        }
        _playbackState.update { it.copy(queue = updatedQueue) }
        _statusBanner.value = "'${item.title}' 다음 재생에 추가됨"
    }

    fun skipToQueueIndex(index: Int) {
        val controller = _mediaController.value ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun togglePlayPause() {
        val controller = _mediaController.value ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0L)
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = _mediaController.value ?: return
        val max = controller.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs
        val clamped = positionMs.coerceIn(0L, max.coerceAtLeast(0L))
        controller.seekTo(clamped)
        _playbackState.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val current = _mediaController.value?.currentPosition ?: _playbackState.value.positionMs
        seekTo(current + deltaMs)
    }

    fun skipNext() {
        _mediaController.value?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = _mediaController.value ?: return
        if (controller.currentPosition > 3500L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _mediaController.value?.playbackParameters = PlaybackParameters(clamped)
        viewModelScope.launch { preferences.setPlaybackSpeed(clamped) }
    }

    fun cycleRepeatMode() {
        val next = when (prefsState.value.repeatMode) {
            RepeatModeOption.OFF -> RepeatModeOption.ALL
            RepeatModeOption.ALL -> RepeatModeOption.ONE
            RepeatModeOption.ONE -> RepeatModeOption.OFF
        }
        viewModelScope.launch { preferences.setRepeatMode(next) }
    }

    fun toggleShuffle() {
        val next = !prefsState.value.shuffleEnabled
        viewModelScope.launch { preferences.setShuffleEnabled(next) }
    }

    fun cycleAspectRatioMode() {
        val values = AspectRatioMode.entries
        val next = values[(values.indexOf(_playbackState.value.aspectRatioMode) + 1) % values.size]
        setAspectRatioMode(next)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatioMode = mode) }
    }

    fun toggleMirrored() {
        _playbackState.update { it.copy(isMirrored = !it.isMirrored) }
    }

    fun toggleSubtitles() {
        val controller = _mediaController.value ?: return
        val next = !_playbackState.value.subtitlesEnabled
        _playbackState.update { it.copy(subtitlesEnabled = next) }
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !next)
            .build()
    }

    fun attachSubtitleToCurrent(subtitleUri: Uri) {
        val current = _playbackState.value.currentMedia ?: return
        val controller = _mediaController.value ?: return
        val currentPos = controller.currentPosition
        val wasPlaying = controller.isPlaying

        viewModelScope.launch {
            repository.attachSubtitle(current.id, subtitleUri)
            val updated = current.copy(subtitleUriString = subtitleUri.toString())
            val updatedQueue = _playbackState.value.queue.map {
                if (it.id == updated.id) updated else it
            }
            val idx = _playbackState.value.currentQueueIndex.coerceAtLeast(0)
            _playbackState.update {
                it.copy(currentMedia = updated, queue = updatedQueue, subtitlesEnabled = true)
            }
            controller.replaceMediaItem(idx, buildMedia3Item(updated))
            controller.seekTo(idx, currentPos)
            controller.prepare()
            if (wasPlaying) controller.play()
            _statusBanner.value = "자막 파일이 적용되었습니다"
        }
    }

    fun setAbPointA() {
        val pos = _mediaController.value?.currentPosition ?: _playbackState.value.positionMs
        _playbackState.update { state ->
            val end = state.abLoopEndMs?.takeIf { it > pos }
            state.copy(abLoopStartMs = pos, abLoopEndMs = end)
        }
    }

    fun setAbPointB() {
        val pos = _mediaController.value?.currentPosition ?: _playbackState.value.positionMs
        _playbackState.update { state ->
            val start = state.abLoopStartMs ?: 0L
            if (pos > start + 500L) {
                state.copy(abLoopStartMs = start, abLoopEndMs = pos)
            } else {
                state
            }
        }
    }

    fun clearAbLoop() {
        _playbackState.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option.minutes <= 0 && !option.endOfTrack) {
            cancelSleepTimer()
            return
        }
        if (option.endOfTrack) {
            _playbackState.update {
                it.copy(sleepTimerRemainingMs = null, sleepAtEndOfTrack = true)
            }
            _statusBanner.value = "현재 미디어 종료 시 자동 일시정지됩니다"
            return
        }
        val totalMs = option.minutes * 60_000L
        _playbackState.update {
            it.copy(sleepTimerRemainingMs = totalMs, sleepAtEndOfTrack = false)
        }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining -= 1000L
                _playbackState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            if (remaining <= 0L) {
                _mediaController.value?.pause()
                cancelSleepTimer()
                _statusBanner.value = "취침 타이머가 종료되어 재생을 일시정지했습니다"
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playbackState.update {
            it.copy(sleepTimerRemainingMs = null, sleepAtEndOfTrack = false)
        }
    }

    fun openFullPlayer() {
        _playbackState.update {
            it.copy(isPlayerScreenOpen = true, isFloatingPopupVisible = false)
        }
    }

    fun closeFullPlayer(keepPlaying: Boolean = true) {
        val current = _playbackState.value.currentMedia
        val isVideo = current?.mediaType == MediaType.VIDEO
        val showPopup = keepPlaying && isVideo && _playbackState.value.isPlaying
        _playbackState.update {
            it.copy(
                isPlayerScreenOpen = false,
                isFloatingPopupVisible = showPopup
            )
        }
        if (!keepPlaying) {
            _mediaController.value?.pause()
        }
    }

    fun minimizeToFloatingPopup() {
        _playbackState.update {
            it.copy(isPlayerScreenOpen = false, isFloatingPopupVisible = true)
        }
    }

    fun closeFloatingPopup(stopPlayback: Boolean = false) {
        _playbackState.update { it.copy(isFloatingPopupVisible = false) }
        if (stopPlayback) {
            _mediaController.value?.pause()
        }
    }

    fun setPopupWidthDp(widthDp: Float) {
        viewModelScope.launch {
            preferences.setPopupWidthDp(widthDp)
        }
    }

    fun applyPopupPreset(preset: PopupSizePreset) {
        setPopupWidthDp(preset.widthDp)
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, item.isFavorite)
            if (_playbackState.value.currentMedia?.id == item.id) {
                _playbackState.update {
                    it.copy(currentMedia = item.copy(isFavorite = !item.isFavorite))
                }
            }
        }
    }

    fun playMediaBatch(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        playMedia(
            item = items.first(),
            queueItems = items,
            openPlayer = true
        )
    }

    fun setFavoriteBatch(items: List<MediaItemEntity>, isFavorite: Boolean) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }
        viewModelScope.launch {
            repository.setFavoriteBatch(ids, isFavorite)
            val current = _playbackState.value.currentMedia
            if (current != null && current.id in ids) {
                _playbackState.update {
                    it.copy(currentMedia = current.copy(isFavorite = isFavorite))
                }
            }
            _statusBanner.value = if (isFavorite) {
                "${items.size}개 항목을 즐겨찾기에 추가했습니다"
            } else {
                "${items.size}개 항목의 즐겨찾기를 해제했습니다"
            }
        }
    }

    fun deleteMedia(item: MediaItemEntity) {
        viewModelScope.launch {
            if (_playbackState.value.currentMedia?.id == item.id) {
                _mediaController.value?.stop()
                _playbackState.update {
                    it.copy(
                        currentMedia = null,
                        isPlayerScreenOpen = false,
                        isFloatingPopupVisible = false
                    )
                }
            }
            repository.deleteMedia(item)
        }
    }

    fun deleteMediaBatch(items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }.toSet()
        viewModelScope.launch {
            if (_playbackState.value.currentMedia?.id in ids) {
                _mediaController.value?.stop()
                _playbackState.update {
                    it.copy(
                        currentMedia = null,
                        isPlayerScreenOpen = false,
                        isFloatingPopupVisible = false
                    )
                }
            }
            repository.deleteMediaBatch(items)
            _statusBanner.value = "${items.size}개 항목이 삭제되었습니다"
        }
    }

    fun createPlaylist(name: String, initialMediaId: Long? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name)
            if (initialMediaId != null) {
                repository.addMediaToPlaylist(playlistId, initialMediaId)
            }
            _statusBanner.value = "'$name' 재생목록이 생성되었습니다"
        }
    }

    fun createPlaylistWithMediaBatch(name: String, mediaIds: List<Long>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name)
            if (mediaIds.isNotEmpty()) {
                repository.addMediaBatchToPlaylist(playlistId, mediaIds)
            }
            _statusBanner.value = "'$name' 재생목록에 ${mediaIds.size}개 추가되었습니다"
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun addMediaToPlaylist(playlistId: Long, mediaId: Long) {
        viewModelScope.launch {
            repository.addMediaToPlaylist(playlistId, mediaId)
            _statusBanner.value = "재생목록에 추가되었습니다"
        }
    }

    fun addMediaBatchToPlaylist(playlistId: Long, mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            repository.addMediaBatchToPlaylist(playlistId, mediaIds)
            _statusBanner.value = "${mediaIds.size}개 항목이 재생목록에 추가되었습니다"
        }
    }

    fun removeMediaFromPlaylist(playlistId: Long, mediaId: Long) {
        viewModelScope.launch { repository.removeMediaFromPlaylist(playlistId, mediaId) }
    }

    fun addBookmarkAtCurrentPosition(label: String) {
        val current = _playbackState.value.currentMedia ?: return
        val pos = _mediaController.value?.currentPosition ?: _playbackState.value.positionMs
        viewModelScope.launch {
            repository.addBookmark(current.id, pos, label)
            _statusBanner.value = "현재 위치에 북마크가 저장되었습니다"
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { repository.deleteBookmark(bookmarkId) }
    }

    // Settings controls
    fun setBackgroundVideoAudio(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackgroundVideoAudio(enabled) }
    }

    fun setAutoPipOnLeave(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoPipOnLeave(enabled) }
    }

    fun setDefaultAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch { preferences.setDefaultAspectRatio(mode) }
    }

    fun setDarkThemeMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkThemeMode(enabled) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
    }

    fun setResumePromptless(enabled: Boolean) {
        viewModelScope.launch { preferences.setResumePlaybackPromptless(enabled) }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    fun saveCurrentPosition() {
        val current = _playbackState.value.currentMedia ?: return
        val pos = _mediaController.value?.currentPosition ?: _playbackState.value.positionMs
        viewModelScope.launch {
            repository.updatePlaybackPosition(current.id, pos)
        }
    }

    fun onAppBackgrounded(isInPipMode: Boolean) {
        saveCurrentPosition()
        val state = _playbackState.value
        val isVideo = state.currentMedia?.mediaType == MediaType.VIDEO
        if (!isInPipMode && isVideo && !prefsState.value.backgroundVideoAudio) {
            _mediaController.value?.pause()
        }
    }

    private fun buildMedia3Item(item: MediaItemEntity): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(item.id.toString())
            .setUri(item.uriString)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist.ifBlank { if (item.mediaType == MediaType.VIDEO) "동영상" else "오디오" })
                    .build()
            )
        item.subtitleUriString?.let { subUri ->
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUri))
                .setMimeType(guessSubtitleMimeType(subUri))
                .setLanguage("ko")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        return builder.build()
    }

    override fun onCleared() {
        saveCurrentPosition()
        _mediaController.value?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
