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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.MediaItemEntity
import com.example.data.model.MediaType
import com.example.ui.viewmodel.FolderGroup
import com.example.util.formatDuration
import java.io.File

@Composable
fun FoldersScreen(
    folderGroups: List<FolderGroup>,
    currentPlayingId: Long?,
    isScanning: Boolean,
    onPickFolder: () -> Unit,
    onRescanFolders: () -> Unit,
    onRemoveFolder: (FolderGroup, removeFiles: Boolean) -> Unit,
    onRemoveFolders: (List<FolderGroup>, removeFiles: Boolean) -> Unit,
    onPlayFolder: (items: List<MediaItemEntity>, shuffle: Boolean, asPopup: Boolean) -> Unit,
    onPlayMediaInFolder: (item: MediaItemEntity, folderItems: List<MediaItemEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var folderPendingRemoval by remember { mutableStateOf<FolderGroup?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val availableFolderIds = remember(folderGroups) { folderGroups.map { it.id }.toSet() }
    LaunchedEffect(availableFolderIds) {
        if (selectedFolderId != null && selectedFolderId !in availableFolderIds) {
            selectedFolderId = null
        }
        if (availableFolderIds.isEmpty()) {
            selectionMode = false
            selectedIds = emptySet()
        } else {
            selectedIds = selectedIds.intersect(availableFolderIds)
        }
    }

    val selectedFolders = remember(folderGroups, selectedIds) {
        folderGroups.filter { it.id in selectedIds }
    }
    val allSelected = folderGroups.isNotEmpty() && selectedIds.size == folderGroups.size

    val selectedFolder = remember(folderGroups, selectedFolderId) {
        selectedFolderId?.let { id -> folderGroups.find { it.id == id } }
    }

    if (selectedFolder != null) {
        BackHandler { selectedFolderId = null }
        FolderDetailView(
            folder = selectedFolder,
            currentPlayingId = currentPlayingId,
            onBack = { selectedFolderId = null },
            onDeleteFolder = { folderPendingRemoval = selectedFolder },
            onPlayAll = { shuffle, asPopup ->
                onPlayFolder(selectedFolder.items, shuffle, asPopup)
            },
            onPlayItem = { item ->
                onPlayMediaInFolder(item, selectedFolder.items)
            },
            modifier = modifier
        )
    } else {
        BackHandler(enabled = selectionMode) {
            selectionMode = false
            selectedIds = emptySet()
        }

        Column(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(visible = selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folders_selection_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("folders_exit_selection_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "선택 취소")
                            }
                            Text(
                                text = "${selectedFolders.size}개 폴더 선택",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        folderGroups.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.testTag("folders_toggle_select_all_button")
                            ) {
                                Icon(
                                    Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (allSelected) "전체 해제" else "전체 선택")
                            }

                            Button(
                                onClick = { showBatchDeleteConfirm = true },
                                enabled = selectedFolders.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("folders_batch_delete_button")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("삭제")
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("폴더별 재생", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "기기 폴더를 연결해 폴더 단위로 연속/셔플/팝업 재생",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (folderGroups.any { it.isLinkedFolder }) {
                                IconButton(
                                    onClick = onRescanFolders,
                                    enabled = !isScanning,
                                    modifier = Modifier.testTag("rescan_folders_button")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "폴더 새로고침")
                                }
                            }
                            Button(
                                onClick = onPickFolder,
                                enabled = !isScanning,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("connect_folder_button")
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("폴더 연결")
                            }
                        }
                    }
                }

                if (folderGroups.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "폴더 ${folderGroups.size}개",
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
                                        modifier = Modifier.testTag("folders_enter_selection_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Checklist,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("목록 편집")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (!selectionMode || !allSelected) {
                                            selectionMode = true
                                            selectedIds = folderGroups.map { it.id }.toSet()
                                        } else {
                                            selectedIds = emptySet()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("folders_select_all_button")
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

                if (isScanning) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "폴더 내 미디어 파일을 스캔하고 있습니다...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (folderGroups.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
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
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Text("연결된 폴더가 없습니다", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Movies, Music, Download 등 원하는 폴더를 선택하면 하위 폴더까지 자동으로 불러와 폴더별로 바로 재생할 수 있습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onPickFolder,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("empty_connect_folder_button")
                                ) {
                                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("기기 폴더 선택하기")
                                }
                            }
                        }
                    }
                } else {
                    items(folderGroups, key = { it.id }) { folder ->
                        val isSelected = folder.id in selectedIds
                        FolderGroupCard(
                            folder = folder,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (isSelected) {
                                        selectedIds - folder.id
                                    } else {
                                        selectedIds + folder.id
                                    }
                                } else {
                                    selectedFolderId = folder.id
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(folder.id)
                                } else {
                                    selectedIds = if (isSelected) {
                                        selectedIds - folder.id
                                    } else {
                                        selectedIds + folder.id
                                    }
                                }
                            },
                            onPlayAll = { onPlayFolder(folder.items, false, false) },
                            onShuffleAll = { onPlayFolder(folder.items, true, false) },
                            onPopupPlay = { onPlayFolder(folder.items, false, true) },
                            onDelete = { folderPendingRemoval = folder }
                        )
                    }
                }
            }
        }
    }

    folderPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { folderPendingRemoval = null },
            title = { Text("폴더 목록 삭제") },
            text = {
                Text(
                    if (target.isLinkedFolder && target.treeUriString != null) {
                        "'${target.folderName}' 폴더를 폴더 목록에서 삭제할까요?\n포함된 파일도 라이브러리에서 함께 지우거나, 폴더 목록에서만 제거할 수 있습니다. (기기 원본 파일은 유지됩니다.)"
                    } else {
                        "'${target.folderName}' 폴더와 포함된 ${target.items.size}개의 미디어 항목을 앱 목록에서 삭제할까요? (기기 원본 파일은 유지됩니다.)"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveFolder(target, true)
                        if (selectedFolderId == target.id) {
                            selectedFolderId = null
                        }
                        folderPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_folder_button")
                ) {
                    Text(if (target.isLinkedFolder && target.treeUriString != null) "폴더·파일 모두 삭제" else "삭제")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { folderPendingRemoval = null }) {
                        Text("취소")
                    }
                    if (target.isLinkedFolder && target.treeUriString != null) {
                        TextButton(
                            onClick = {
                                onRemoveFolder(target, false)
                                if (selectedFolderId == target.id) {
                                    selectedFolderId = null
                                }
                                folderPendingRemoval = null
                            },
                            modifier = Modifier.testTag("confirm_detach_folder_only_button")
                        ) {
                            Text("폴더만 지우기")
                        }
                    }
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedFolders.isNotEmpty()) {
        val hasLinked = selectedFolders.any { it.isLinkedFolder && it.treeUriString != null }
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("선택한 폴더 삭제") },
            text = {
                Text(
                    if (hasLinked) {
                        "선택한 ${selectedFolders.size}개의 폴더를 목록에서 삭제할까요? 연결된 폴더의 파일까지 함께 지우거나 폴더 목록에서만 제거할 수 있습니다."
                    } else {
                        "선택한 ${selectedFolders.size}개의 폴더와 포함된 항목을 앱 목록에서 삭제할까요? (기기 원본 파일은 유지됩니다.)"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveFolders(selectedFolders, true)
                        showBatchDeleteConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_folders_batch_delete_button")
                ) {
                    Text(if (hasLinked) "폴더·파일 모두 삭제" else "삭제")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("취소")
                    }
                    if (hasLinked) {
                        TextButton(
                            onClick = {
                                onRemoveFolders(selectedFolders, false)
                                showBatchDeleteConfirm = false
                                selectionMode = false
                                selectedIds = emptySet()
                            }
                        ) {
                            Text("폴더만 지우기")
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGroupCard(
    folder: FolderGroup,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPopupPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = if (selectionMode && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selectionMode && isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("folder_card_${folder.folderName}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.testTag("folder_checkbox_${folder.folderName}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (folder.isLinkedFolder) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (folder.isLinkedFolder) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = folder.folderName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (folder.isLinkedFolder) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = if (folder.isLinkedFolder) "연결됨" else "자동",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (folder.isLinkedFolder) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val stats = buildList {
                        if (folder.videoCount > 0) add("동영상 ${folder.videoCount}")
                        if (folder.audioCount > 0) add("오디오 ${folder.audioCount}")
                        if (folder.items.isEmpty()) add("비어 있음")
                        if (folder.totalDurationMs > 0L) add(formatDuration(folder.totalDurationMs))
                    }.joinToString(" · ")
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!selectionMode) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_folder_${folder.folderName}")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "폴더 삭제",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (folder.items.isNotEmpty() && !selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_folder_${folder.folderName}"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("폴더 재생", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onShuffleAll,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "셔플 재생", modifier = Modifier.size(18.dp))
                    }

                    if (folder.hasVideo) {
                        OutlinedButton(
                            onClick = onPopupPlay,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "팝업으로 폴더 재생",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderDetailView(
    folder: FolderGroup,
    currentPlayingId: Long?,
    onBack: () -> Unit,
    onDeleteFolder: () -> Unit,
    onPlayAll: (shuffle: Boolean, asPopup: Boolean) -> Unit,
    onPlayItem: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("folder_detail_back")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "폴더 목록으로")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "총 ${folder.items.size}개 · ${formatDuration(folder.totalDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDeleteFolder,
                    modifier = Modifier.testTag("folder_detail_delete")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "폴더 삭제",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (folder.items.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onPlayAll(false, false) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("전체 재생")
                    }
                    OutlinedButton(
                        onClick = { onPlayAll(true, false) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("셔플")
                    }
                    if (folder.hasVideo) {
                        OutlinedButton(
                            onClick = { onPlayAll(false, true) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "팝업 재생", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("팝업")
                        }
                    }
                }
            }
        }

        items(folder.items, key = { it.id }) { item ->
            val active = item.id == currentPlayingId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayItem(item) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 52.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                append(if (item.mediaType == MediaType.VIDEO) "동영상" else "오디오")
                                if (item.durationMs > 0L) append(" · ${formatDuration(item.durationMs)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { onPlayItem(item) }) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "재생",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
