package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.MediaItemEntity
import com.example.data.model.LibraryFilter
import com.example.data.model.MediaType
import com.example.data.model.SortOption
import com.example.util.formatDuration
import com.example.util.formatFileSize
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    mediaItems: List<MediaItemEntity>,
    allMediaCount: Int,
    currentPlayingId: Long?,
    searchQuery: String,
    activeFilter: LibraryFilter,
    sortOption: SortOption,
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onPickDocuments: () -> Unit,
    onPickVideoFromGallery: () -> Unit,
    onOpenStreamUrlDialog: () -> Unit,
    onPlayMedia: (MediaItemEntity, Boolean) -> Unit,
    onToggleFavorite: (MediaItemEntity) -> Unit,
    onAddNextInQueue: (MediaItemEntity) -> Unit,
    onAddToPlaylist: (MediaItemEntity) -> Unit,
    onDeleteMedia: (MediaItemEntity) -> Unit,
    onPlaySelected: (List<MediaItemEntity>) -> Unit,
    onSetFavoriteSelected: (List<MediaItemEntity>, Boolean) -> Unit,
    onAddSelectedToPlaylist: (List<MediaItemEntity>) -> Unit,
    onDeleteSelected: (List<MediaItemEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val availableIds = remember(mediaItems) { mediaItems.map { it.id }.toSet() }
    LaunchedEffect(availableIds) {
        if (availableIds.isEmpty()) {
            selectionMode = false
            selectedIds = emptySet()
        } else {
            selectedIds = selectedIds.intersect(availableIds)
        }
    }

    val selectedItems = remember(mediaItems, selectedIds) {
        mediaItems.filter { it.id in selectedIds }
    }
    val allSelected = mediaItems.isNotEmpty() && selectedIds.size == mediaItems.size

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    val recentResumeItem = remember(mediaItems) {
        mediaItems.filter { it.lastPlayedAt > 0L && it.lastPositionMs > 2000L }
            .maxByOrNull { it.lastPlayedAt }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_selection_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("library_exit_selection_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "선택 취소")
                            }
                            Text(
                                text = "${selectedItems.size}개 선택됨",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    mediaItems.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("library_toggle_select_all_button")
                        ) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (allSelected) "전체 해제" else "전체 선택")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onPlaySelected(selectedItems)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedItems.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("library_batch_play_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("재생")
                        }

                        FilledTonalButton(
                            onClick = {
                                onAddSelectedToPlaylist(selectedItems)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedItems.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("library_batch_playlist_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("재생목록")
                        }

                        val allFavorites = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite }
                        FilledTonalButton(
                            onClick = {
                                onSetFavoriteSelected(selectedItems, !allFavorites)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedItems.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("library_batch_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (allFavorites) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (allFavorites) "즐겨찾기 해제" else "즐겨찾기")
                        }

                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            enabled = selectedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("library_batch_delete_button")
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("삭제")
                        }
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 310.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Studio Hero Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_library_hero),
                            contentDescription = "VeloPlay 스튜디오 배너",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xE60B0F1A),
                                            Color(0x990B0F1A),
                                            Color(0x440B0F1A)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "VeloPlay Studio",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "백그라운드 재생 · 리사이즈 팝업 · 폴더별 연속 재생",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onPickDocuments,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("pick_media_files_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("파일 추가", style = MaterialTheme.typography.labelLarge)
                                }

                                OutlinedButton(
                                    onClick = onPickVideoFromGallery,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("pick_gallery_video_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("갤러리", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }

                                IconButton(
                                    onClick = onOpenStreamUrlDialog,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .testTag("open_stream_url_dialog_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = "스트림 URL 추가",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Continue Watching / Listening quick card
            if (recentResumeItem != null && searchQuery.isBlank() && !selectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayMedia(recentResumeItem, false) }
                            .testTag("continue_watching_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "이어서 재생",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    recentResumeItem.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                formatDuration(recentResumeItem.lastPositionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Search + Sort Row
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("제목, 아티스트 검색...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "검색어 지우기")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("library_search_input")
                    )

                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { showSortMenu = true }
                                .testTag("library_sort_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "정렬 기준")
                            }
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onSortChange(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips + Selection Controls Row
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibraryFilter.entries) { filter ->
                            FilterChip(
                                selected = filter == activeFilter,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }

                    if (mediaItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "미디어 ${mediaItems.size}개",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!selectionMode) {
                                    TextButton(
                                        onClick = { selectionMode = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("library_enter_selection_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Checklist,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("선택")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (!selectionMode || !allSelected) {
                                            selectionMode = true
                                            selectedIds = mediaItems.map { it.id }.toSet()
                                        } else {
                                            selectedIds = emptySet()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("library_select_all_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (selectionMode && allSelected) "선택 해제" else "전체 선택")
                                }
                            }
                        }
                    }
                }
            }

            // Empty State or Media Items
            if (mediaItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = if (allMediaCount == 0) {
                                    "미디어 라이브러리가 비어 있습니다"
                                } else {
                                    "검색 또는 필터와 일치하는 파일이 없습니다"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "기기의 동영상·오디오 파일을 불러오거나, 스트림 URL을 추가해 백그라운드 및 팝업 재생을 바로 시작해 보세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (allMediaCount == 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = onPickDocuments) {
                                        Text("파일 선택하기")
                                    }
                                    OutlinedButton(onClick = onOpenStreamUrlDialog) {
                                        Text("URL 스트림")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(mediaItems, key = { it.id }) { item ->
                    val isSelected = item.id in selectedIds
                    MediaItemCard(
                        item = item,
                        isCurrentlyPlaying = item.id == currentPlayingId,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (isSelected) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            } else {
                                onPlayMedia(item, false)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(item.id)
                            } else {
                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(item) },
                        onPlayFromStart = { onPlayMedia(item, true) },
                        onAddNext = { onAddNextInQueue(item) },
                        onAddToPlaylist = { onAddToPlaylist(item) },
                        onDelete = { onDeleteMedia(item) }
                    )
                }
            }
        }
    }

    if (showBatchDeleteConfirm && selectedItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("선택한 파일 삭제") },
            text = {
                Text("선택한 ${selectedItems.size}개의 미디어를 라이브러리 목록에서 삭제할까요? (기기의 원본 파일은 삭제되지 않습니다.)")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelected(selectedItems)
                        showBatchDeleteConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_library_batch_delete_button")
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaItemCard(
    item: MediaItemEntity,
    isCurrentlyPlaying: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayFromStart: () -> Unit,
    onAddNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        border = if (selectionMode && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = when {
                selectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_item_card_${item.id}")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("media_item_checkbox_${item.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (item.thumbnailPath != null) {
                        AsyncImage(
                            model = File(item.thumbnailPath),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else if (item.mediaType == MediaType.AUDIO) {
                        Image(
                            painter = painterResource(id = R.drawable.img_audio_artwork),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Type Badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (item.mediaType == MediaType.VIDEO) {
                                Icons.Rounded.Videocam
                            } else {
                                Icons.Rounded.MusicNote
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(12.dp)
                        )
                    }

                    // Duration Badge
                    if (item.durationMs > 0L) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.76f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = formatDuration(item.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val metaText = buildList {
                        if (item.artist.isNotBlank()) add(item.artist)
                        if (item.width > 0 && item.height > 0) add("${item.width}×${item.height}")
                        val sizeStr = formatFileSize(item.fileSizeBytes)
                        if (sizeStr.isNotBlank()) add(sizeStr)
                    }.joinToString(" · ")
                    if (metaText.isNotBlank()) {
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!selectionMode) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "즐겨찾기",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "옵션")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("처음부터 재생") },
                                leadingIcon = { Icon(Icons.Rounded.Replay, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayFromStart()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("다음 재생에 추가") },
                                leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onAddNext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("재생목록에 추가") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onAddToPlaylist()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("목록에서 제거") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (item.durationMs > 0L && item.lastPositionMs > 2000L) {
                val pct = (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
