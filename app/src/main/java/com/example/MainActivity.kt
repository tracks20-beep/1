package com.example

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.MediaType
import com.example.ui.components.AddStreamUrlDialog
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.FloatingVideoPopup
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoPlayerView
import com.example.ui.theme.VeloPlayTheme
import com.example.ui.viewmodel.PlayerViewModel
import kotlinx.serialization.Serializable

@Serializable object LibraryRoute
@Serializable object PlaylistsRoute
@Serializable object FoldersRoute
@Serializable object SettingsRoute

private data class TopDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(LibraryRoute, "라이브러리", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_library"),
    TopDestination(PlaylistsRoute, "재생목록", Icons.AutoMirrored.Rounded.PlaylistPlay, Icons.AutoMirrored.Outlined.PlaylistPlay, "nav_playlists"),
    TopDestination(FoldersRoute, "폴더", Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_folders"),
    TopDestination(SettingsRoute, "설정", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
)

private const val ACTION_PIP_CONTROL = "com.example.veloplay.PIP_CONTROL"
private const val EXTRA_PIP_COMMAND = "pip_command"
private const val PIP_CMD_REWIND = 1
private const val PIP_CMD_PLAY_PAUSE = 2
private const val PIP_CMD_FORWARD = 3

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private val isInPipState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isInPipState.value = isInPictureInPictureMode
            addOnPictureInPictureModeChangedListener(Consumer { info ->
                isInPipState.value = info.isInPictureInPictureMode
            })
        }

        handleIncomingIntent(intent)

        setContent {
            val prefs by viewModel.prefsState.collectAsStateWithLifecycle()
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            val mediaController by viewModel.mediaController.collectAsStateWithLifecycle()
            val filteredMedia by viewModel.filteredMedia.collectAsStateWithLifecycle()
            val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            val bookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()
            val folderGroups by viewModel.folderGroups.collectAsStateWithLifecycle()
            val isScanningFolders by viewModel.isScanningFolders.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val activeFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
            val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
            val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
            val isInPipMode by isInPipState

            var hasNotificationPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                )
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasNotificationPermission = granted
            }

            val supportsSystemPip = remember {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            }

            val documentPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.importLocalFiles(uris, autoPlayFirst = allMedia.isEmpty())
                }
            }

            val fallbackMediaPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uris = extractUrisFromIntent(result.data)
                    if (uris.isNotEmpty()) {
                        viewModel.importLocalFiles(uris, autoPlayFirst = allMedia.isEmpty())
                    }
                }
            }

            val galleryVideoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.importLocalFiles(uris, autoPlayFirst = allMedia.isEmpty())
                }
            }

            val subtitlePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { viewModel.attachSubtitleToCurrent(it) }
            }

            val fallbackSubtitleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    extractUrisFromIntent(result.data).firstOrNull()?.let {
                        viewModel.attachSubtitleToCurrent(it)
                    }
                }
            }

            val folderPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { treeUri ->
                treeUri?.let { viewModel.importFolderTree(it) }
            }

            fun launchCompatibleMediaPicker(videoOnly: Boolean = false) {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = if (videoOnly) "video/*" else "*/*"
                    if (!videoOnly) {
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
                    }
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val launchedGetContent = runCatching {
                    fallbackMediaPickerLauncher.launch(getContentIntent)
                }.isSuccess
                if (launchedGetContent) return

                val pickIntent = Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).apply {
                    type = if (videoOnly) "video/*" else "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                val launchedPick = runCatching {
                    fallbackMediaPickerLauncher.launch(pickIntent)
                }.isSuccess
                if (!launchedPick) {
                    viewModel.showMessage("이 기기에 시스템 파일/미디어 선택기가 없습니다. 스트림 URL 추가를 이용해 보세요.")
                }
            }

            fun launchDocumentPicker() {
                val safLaunched = runCatching {
                    documentPickerLauncher.launch(arrayOf("video/*", "audio/*"))
                }.isSuccess
                if (!safLaunched) {
                    launchCompatibleMediaPicker(videoOnly = false)
                }
            }

            fun launchGalleryVideoPicker() {
                val photoPickerLaunched = runCatching {
                    galleryVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }.isSuccess
                if (!photoPickerLaunched) {
                    launchCompatibleMediaPicker(videoOnly = true)
                }
            }

            fun launchFolderPicker() {
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                val hasTreeActivity = treeIntent.resolveActivity(packageManager) != null
                val launched = if (hasTreeActivity) {
                    runCatching { folderPickerLauncher.launch(null) }.isSuccess
                } else {
                    false
                }
                if (!launched) {
                    viewModel.showMessage("이 기기는 폴더 선택기를 지원하지 않아 호환 파일 선택기로 연결합니다.")
                    launchCompatibleMediaPicker(videoOnly = false)
                }
            }

            fun launchSubtitlePicker() {
                val safLaunched = runCatching {
                    subtitlePickerLauncher.launch(
                        arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")
                    )
                }.isSuccess
                if (!safLaunched) {
                    val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    runCatching { fallbackSubtitleLauncher.launch(fallback) }
                        .onFailure {
                            viewModel.showMessage("이 기기에서 자막 파일 선택기를 열 수 없습니다.")
                        }
                }
            }

            // Configure PiP auto-enter and remote actions safely
            val shouldAutoPip = supportsSystemPip &&
                prefs.autoPipOnLeave &&
                playbackState.isPlaying &&
                playbackState.currentMedia?.mediaType == MediaType.VIDEO &&
                (playbackState.isPlayerScreenOpen || playbackState.isFloatingPopupVisible)

            LaunchedEffect(supportsSystemPip, shouldAutoPip, playbackState.isPlaying, playbackState.videoAspectRatio) {
                if (supportsSystemPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        setPictureInPictureParams(
                            buildPipParams(
                                aspectRatio = playbackState.videoAspectRatio,
                                isPlaying = playbackState.isPlaying,
                                autoEnter = shouldAutoPip
                            )
                        )
                    }
                }
            }

            if (supportsSystemPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                DisposableEffect(shouldAutoPip, playbackState.videoAspectRatio, playbackState.isPlaying) {
                    val listener = Runnable {
                        if (shouldAutoPip) {
                            enterSystemPip(
                                aspectRatio = playbackState.videoAspectRatio,
                                isPlaying = playbackState.isPlaying
                            )
                        }
                    }
                    addOnUserLeaveHintListener(listener)
                    onDispose { removeOnUserLeaveHintListener(listener) }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action != ACTION_PIP_CONTROL) return
                            when (intent.getIntExtra(EXTRA_PIP_COMMAND, 0)) {
                                PIP_CMD_REWIND -> viewModel.seekBy(-10_000L)
                                PIP_CMD_PLAY_PAUSE -> viewModel.togglePlayPause()
                                PIP_CMD_FORWARD -> viewModel.seekBy(10_000L)
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        IntentFilter(ACTION_PIP_CONTROL),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    onDispose { unregisterReceiver(receiver) }
                }
            }

            VeloPlayTheme(
                darkTheme = prefs.darkThemeMode,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                if (isInPipMode) {
                    VideoPlayerView(
                        player = mediaController,
                        playbackState = playbackState,
                        isInPipMode = true,
                        onBack = {},
                        onMinimizeToPopup = {},
                        onEnterSystemPip = {},
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSeekTo = viewModel::seekTo,
                        onSeekBy = viewModel::seekBy,
                        onSkipPrevious = viewModel::skipPrevious,
                        onSkipNext = viewModel::skipNext,
                        onCycleAspectRatio = viewModel::cycleAspectRatioMode,
                        onToggleMirrored = viewModel::toggleMirrored,
                        onToggleSubtitles = viewModel::toggleSubtitles,
                        onPickSubtitleFile = {},
                        onOpenSpeedSheet = {},
                        onOpenBookmarksSheet = {},
                        onOpenSleepTimerSheet = {},
                        onOpenQueueSheet = {}
                    )
                    return@VeloPlayTheme
                }

                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                var showStreamDialog by remember { mutableStateOf(false) }
                var mediaIdsForPlaylistDialog by remember { mutableStateOf<List<Long>?>(null) }

                LaunchedEffect(statusBanner) {
                    statusBanner?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearStatusBanner()
                    }
                }

                LaunchedEffect(playbackState.errorMessage) {
                    playbackState.errorMessage?.let { err ->
                        snackbarHostState.showSnackbar(err)
                        viewModel.clearError()
                    }
                }

                // Request notification permission nicely once playback starts on Android 13+
                LaunchedEffect(playbackState.isPlaying) {
                    if (playbackState.isPlaying &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useNavRail = maxWidth >= 700.dp

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            if (!useNavRail && !playbackState.isPlayerScreenOpen) {
                                Column(
                                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                ) {
                                    AnimatedVisibility(
                                        visible = playbackState.currentMedia != null &&
                                            !playbackState.isFloatingPopupVisible
                                    ) {
                                        MiniPlayerBar(
                                            playbackState = playbackState,
                                            onOpenPlayer = { viewModel.openFullPlayer() },
                                            onTogglePlayPause = viewModel::togglePlayPause,
                                            onSkipNext = viewModel::skipNext,
                                            onOpenPopup = { viewModel.minimizeToFloatingPopup() }
                                        )
                                    }

                                    NavigationBar {
                                        val currentDest = backStackEntry?.destination
                                        topDestinations.forEach { dest ->
                                            val selected = currentDest?.hasRoute(dest.route::class) == true
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    navController.navigate(dest.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                        contentDescription = dest.label
                                                    )
                                                },
                                                label = { Text(dest.label) },
                                                modifier = Modifier.testTag(dest.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useNavRail && !playbackState.isPlayerScreenOpen) {
                                NavigationRail {
                                    val currentDest = backStackEntry?.destination
                                    topDestinations.forEach { dest ->
                                        val selected = currentDest?.hasRoute(dest.route::class) == true
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(dest.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = dest.label
                                                )
                                            },
                                            label = { Text(dest.label) },
                                            modifier = Modifier.testTag(dest.testTag)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = LibraryRoute,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        composable<LibraryRoute> {
                                            LibraryScreen(
                                                mediaItems = filteredMedia,
                                                allMediaCount = allMedia.size,
                                                currentPlayingId = playbackState.currentMedia?.id,
                                                searchQuery = searchQuery,
                                                activeFilter = activeFilter,
                                                sortOption = sortOption,
                                                onSearchChange = viewModel::setSearchQuery,
                                                onFilterChange = viewModel::setLibraryFilter,
                                                onSortChange = viewModel::setSortOption,
                                                onPickDocuments = ::launchDocumentPicker,
                                                onPickVideoFromGallery = ::launchGalleryVideoPicker,
                                                onOpenStreamUrlDialog = { showStreamDialog = true },
                                                onPlayMedia = { item, fromStart ->
                                                    viewModel.playMedia(
                                                        item = item,
                                                        queueItems = filteredMedia,
                                                        openPlayer = true,
                                                        startFromBeginning = fromStart
                                                    )
                                                },
                                                onToggleFavorite = viewModel::toggleFavorite,
                                                onAddNextInQueue = viewModel::addNextInQueue,
                                                onAddToPlaylist = { mediaIdsForPlaylistDialog = listOf(it.id) },
                                                onDeleteMedia = viewModel::deleteMedia,
                                                onPlaySelected = viewModel::playMediaBatch,
                                                onSetFavoriteSelected = viewModel::setFavoriteBatch,
                                                onAddSelectedToPlaylist = { items ->
                                                    mediaIdsForPlaylistDialog = items.map { it.id }
                                                },
                                                onDeleteSelected = viewModel::deleteMediaBatch
                                            )
                                        }

                                        composable<PlaylistsRoute> {
                                            PlaylistsScreen(
                                                playlists = playlists,
                                                onCreatePlaylist = { viewModel.createPlaylist(it) },
                                                onDeletePlaylist = viewModel::deletePlaylist,
                                                onPlayPlaylist = { pl, shuffle ->
                                                    viewModel.playPlaylist(pl, shuffle)
                                                },
                                                onPlayItem = { item, items ->
                                                    viewModel.playMedia(item, items, openPlayer = true)
                                                },
                                                onRemoveFromPlaylist = viewModel::removeMediaFromPlaylist
                                            )
                                        }

                                        composable<FoldersRoute> {
                                            FoldersScreen(
                                                folderGroups = folderGroups,
                                                currentPlayingId = playbackState.currentMedia?.id,
                                                isScanning = isScanningFolders,
                                                onPickFolder = ::launchFolderPicker,
                                                onRescanFolders = viewModel::rescanAllFolders,
                                                onRemoveFolder = viewModel::removeFolderGroup,
                                                onRemoveFolders = viewModel::removeFolderGroups,
                                                onPlayFolder = { items, shuffle, asPopup ->
                                                    viewModel.playFolder(items, shuffle, asPopup)
                                                },
                                                onPlayMediaInFolder = { item, folderItems ->
                                                    viewModel.playMedia(item, folderItems, openPlayer = true)
                                                }
                                            )
                                        }

                                        composable<SettingsRoute> {
                                            SettingsScreen(
                                                prefs = prefs,
                                                notificationPermissionGranted = hasNotificationPermission,
                                                supportsSystemPip = supportsSystemPip,
                                                onRequestNotificationPermission = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    }
                                                },
                                                onBackgroundVideoAudioChange = viewModel::setBackgroundVideoAudio,
                                                onAutoPipChange = viewModel::setAutoPipOnLeave,
                                                onPopupWidthChange = viewModel::setPopupWidthDp,
                                                onDefaultAspectRatioChange = viewModel::setDefaultAspectRatio,
                                                onResumePromptlessChange = viewModel::setResumePromptless,
                                                onDarkThemeChange = viewModel::setDarkThemeMode,
                                                onDynamicColorChange = viewModel::setDynamicColorEnabled
                                            )
                                        }
                                    }
                                }

                                if (useNavRail &&
                                    !playbackState.isPlayerScreenOpen &&
                                    playbackState.currentMedia != null &&
                                    !playbackState.isFloatingPopupVisible
                                ) {
                                    MiniPlayerBar(
                                        playbackState = playbackState,
                                        onOpenPlayer = { viewModel.openFullPlayer() },
                                        onTogglePlayPause = viewModel::togglePlayPause,
                                        onSkipNext = viewModel::skipNext,
                                        onOpenPopup = { viewModel.minimizeToFloatingPopup() }
                                    )
                                }
                            }
                        }
                    }

                    // Resizable & Draggable Floating Video Popup Overlay
                    AnimatedVisibility(
                        visible = playbackState.isFloatingPopupVisible &&
                            !playbackState.isPlayerScreenOpen &&
                            playbackState.currentMedia != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FloatingVideoPopup(
                            player = mediaController,
                            playbackState = playbackState,
                            savedWidthDp = prefs.popupWidthDp,
                            onWidthChanged = viewModel::setPopupWidthDp,
                            onSelectPreset = viewModel::applyPopupPreset,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekBy = viewModel::seekBy,
                            onExpandFullscreen = { viewModel.openFullPlayer() },
                            onEnterSystemPip = {
                                if (!enterSystemPip(
                                        aspectRatio = playbackState.videoAspectRatio,
                                        isPlaying = playbackState.isPlaying
                                    )
                                ) {
                                    viewModel.minimizeToFloatingPopup()
                                    viewModel.showMessage("이 기기는 시스템 PIP를 지원하지 않아 앱 내 팝업 창으로 전환합니다")
                                }
                            },
                            onClose = { viewModel.closeFloatingPopup(stopPlayback = false) }
                        )
                    }

                    // Fullscreen Video / Audio Player Overlay
                    AnimatedVisibility(
                        visible = playbackState.isPlayerScreenOpen && playbackState.currentMedia != null,
                        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
                    ) {
                        PlayerScreen(
                            player = mediaController,
                            playbackState = playbackState,
                            bookmarks = bookmarks,
                            isInPipMode = false,
                            onBack = {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                viewModel.closeFullPlayer(keepPlaying = true)
                            },
                            onMinimizeToPopup = {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                viewModel.minimizeToFloatingPopup()
                            },
                            onEnterSystemPip = {
                                if (!enterSystemPip(
                                        aspectRatio = playbackState.videoAspectRatio,
                                        isPlaying = playbackState.isPlaying
                                    )
                                ) {
                                    viewModel.minimizeToFloatingPopup()
                                    viewModel.showMessage("이 기기는 시스템 PIP를 지원하지 않아 앱 내 팝업 창으로 전환합니다")
                                }
                            },
                            onToggleFavorite = {
                                playbackState.currentMedia?.let { viewModel.toggleFavorite(it) }
                            },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSeekTo = viewModel::seekTo,
                            onSeekBy = viewModel::seekBy,
                            onSkipPrevious = viewModel::skipPrevious,
                            onSkipNext = viewModel::skipNext,
                            onSelectSpeed = viewModel::setPlaybackSpeed,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onCycleRepeat = viewModel::cycleRepeatMode,
                            onCycleAspectRatio = viewModel::cycleAspectRatioMode,
                            onToggleMirrored = viewModel::toggleMirrored,
                            onToggleSubtitles = viewModel::toggleSubtitles,
                            onPickSubtitleFile = ::launchSubtitlePicker,
                            onSetAbA = viewModel::setAbPointA,
                            onSetAbB = viewModel::setAbPointB,
                            onClearAb = viewModel::clearAbLoop,
                            onAddBookmark = viewModel::addBookmarkAtCurrentPosition,
                            onDeleteBookmark = viewModel::deleteBookmark,
                            onSelectSleepTimer = viewModel::setSleepTimer,
                            onSelectQueueIndex = viewModel::skipToQueueIndex
                        )
                    }

                    if (showStreamDialog) {
                        AddStreamUrlDialog(
                            onDismiss = { showStreamDialog = false },
                            onConfirm = { url, title, type ->
                                showStreamDialog = false
                                viewModel.addAndPlayNetworkStream(url, title, type)
                            }
                        )
                    }

                    mediaIdsForPlaylistDialog?.let { targetIds ->
                        AddToPlaylistDialog(
                            playlists = playlists,
                            onSelectPlaylist = { playlistId ->
                                if (targetIds.size == 1) {
                                    viewModel.addMediaToPlaylist(playlistId, targetIds.first())
                                } else {
                                    viewModel.addMediaBatchToPlaylist(playlistId, targetIds)
                                }
                            },
                            onCreatePlaylist = { name ->
                                if (targetIds.size == 1) {
                                    viewModel.createPlaylist(name, initialMediaId = targetIds.first())
                                } else {
                                    viewModel.createPlaylistWithMediaBatch(name, targetIds)
                                }
                            },
                            onDismiss = { mediaIdsForPlaylistDialog = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isInPictureInPictureMode else false
        viewModel.onAppBackgrounded(isInPipMode = inPip)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                viewModel.openExternalUri(uri)
            }
        }
    }

    private fun extractUrisFromIntent(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val result = LinkedHashSet<Uri>()
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { result.add(it) }
            }
        }
        intent.data?.let { result.add(it) }
        return result.toList()
    }

    private fun enterSystemPip(aspectRatio: Float, isPlaying: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return runCatching {
            val params = buildPipParams(aspectRatio, isPlaying, autoEnter = false)
            enterPictureInPictureMode(params)
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        aspectRatio: Float,
        isPlaying: Boolean,
        autoEnter: Boolean
    ): PictureInPictureParams {
        val clamped = aspectRatio.coerceIn(0.45f, 2.2f)
        val rational = Rational((clamped * 1000f).toInt(), 1000)

        val actions = listOf(
            createPipAction(
                iconRes = android.R.drawable.ic_media_rew,
                title = getString(R.string.pip_action_rewind),
                command = PIP_CMD_REWIND,
                requestCode = 101
            ),
            createPipAction(
                iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                title = getString(if (isPlaying) R.string.pip_action_pause else R.string.pip_action_play),
                command = PIP_CMD_PLAY_PAUSE,
                requestCode = 102
            ),
            createPipAction(
                iconRes = android.R.drawable.ic_media_ff,
                title = getString(R.string.pip_action_forward),
                command = PIP_CMD_FORWARD,
                requestCode = 103
            )
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipAction(
        iconRes: Int,
        title: String,
        command: Int,
        requestCode: Int
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(packageName)
            putExtra(EXTRA_PIP_COMMAND, command)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pendingIntent
        )
    }
}
