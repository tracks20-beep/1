package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.local.BookmarkEntity
import com.example.data.local.FolderEntity
import com.example.data.local.MediaDao
import com.example.data.local.MediaItemEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistMediaCrossRef
import com.example.data.local.PlaylistWithMedia
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class FolderImportResult(
    val folderName: String,
    val importedCount: Int,
    val firstItem: MediaItemEntity?
)

class MediaRepository(
    private val context: Context,
    private val dao: MediaDao
) {
    val allMedia: Flow<List<MediaItemEntity>> = dao.observeAllMedia()
    val linkedFolders: Flow<List<FolderEntity>> = dao.observeLinkedFolders()
    val playlists: Flow<List<PlaylistWithMedia>> = dao.observePlaylistsWithMedia()

    fun observeBookmarks(mediaId: Long): Flow<List<BookmarkEntity>> =
        dao.observeBookmarksForMedia(mediaId)

    suspend fun getMediaById(id: Long): MediaItemEntity? = dao.getMediaById(id)

    suspend fun getMediaByUri(uriString: String): MediaItemEntity? = dao.getMediaByUri(uriString)

    suspend fun importLocalUris(uris: List<Uri>): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<MediaItemEntity>()
        for (uri in uris) {
            runCatching {
                tryPersistPermission(uri)
                val existing = dao.getMediaByUri(uri.toString())
                if (existing != null) {
                    imported.add(existing)
                    return@runCatching
                }
                val inferredFolder = inferFolderNameFromUri(uri)
                val entity = extractMetadataFromUri(
                    uri = uri,
                    folderName = inferredFolder,
                    folderUriString = null
                )
                val id = dao.insertMedia(entity)
                val saved = if (id > 0L) entity.copy(id = id) else dao.getMediaByUri(uri.toString())
                if (saved != null) imported.add(saved)
            }
        }
        imported
    }

    suspend fun importFolderTree(treeUri: Uri): FolderImportResult = withContext(Dispatchers.IO) {
        tryPersistPermission(treeUri)
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (rootDocId.isNullOrBlank()) {
            val single = importLocalUris(listOf(treeUri))
            val fallbackFolderName = single.firstOrNull()?.folderName ?: inferFolderNameFromUri(treeUri)
            return@withContext FolderImportResult(
                folderName = fallbackFolderName,
                importedCount = single.size,
                firstItem = single.firstOrNull()
            )
        }

        val rootFolderName = runCatching {
            queryDocumentDisplayName(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            )
        }.getOrNull() ?: rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "선택한 폴더" }

        dao.insertFolder(
            FolderEntity(
                treeUriString = treeUri.toString(),
                folderName = rootFolderName,
                lastScannedAt = System.currentTimeMillis()
            )
        )

        val scanResult = scanTreeRecursive(treeUri, rootDocId, rootFolderName)
        FolderImportResult(
            folderName = rootFolderName,
            importedCount = scanResult.size,
            firstItem = scanResult.firstOrNull()
        )
    }

    suspend fun rescanAllLinkedFolders(): Int = withContext(Dispatchers.IO) {
        val folders = dao.getAllLinkedFolders()
        var total = 0
        for (folder in folders) {
            val treeUri = Uri.parse(folder.treeUriString)
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: continue
            val items = scanTreeRecursive(treeUri, rootDocId, folder.folderName)
            dao.updateFolderScannedAt(folder.treeUriString, System.currentTimeMillis())
            total += items.size
        }
        total
    }

    suspend fun removeLinkedFolder(treeUriString: String, removeFiles: Boolean) = withContext(Dispatchers.IO) {
        dao.deleteFolderByTreeUri(treeUriString)
        if (removeFiles) {
            dao.deleteMediaByFolderUri(treeUriString)
        } else {
            dao.detachMediaFromFolderUri(treeUriString)
        }
    }

    suspend fun removeAutoFolder(folderName: String) = withContext(Dispatchers.IO) {
        dao.deleteAutoFolderMedia(folderName)
    }

    private suspend fun scanTreeRecursive(
        treeUri: Uri,
        rootDocId: String,
        rootFolderName: String
    ): List<MediaItemEntity> {
        val resolver = context.contentResolver
        val treeUriString = treeUri.toString()
        val foundItems = mutableListOf<MediaItemEntity>()
        val queue = ArrayDeque<Triple<String, String, Int>>()
        queue.add(Triple(rootDocId, rootFolderName, 0))

        val maxDepth = 3
        val maxFiles = 400
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        while (queue.isNotEmpty() && foundItems.size < maxFiles) {
            val (docId, currentFolderName, depth) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext() && foundItems.size < maxFiles) {
                        val childId = if (idCol >= 0) cursor.getString(idCol) else continue
                        val childName = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                        val childMime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                        val childSize = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L

                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < maxDepth && !childName.startsWith(".")) {
                                val subFolderLabel = if (childName.isNotBlank()) {
                                    "$rootFolderName / $childName"
                                } else {
                                    currentFolderName
                                }
                                queue.add(Triple(childId, subFolderLabel, depth + 1))
                            }
                        } else if (isSupportedMediaFile(childMime, childName)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val existing = dao.getMediaByUri(fileUri.toString())
                            if (existing != null) {
                                if (existing.folderUriString != treeUriString || existing.folderName != currentFolderName) {
                                    dao.updateFolderAssociation(fileUri.toString(), currentFolderName, treeUriString)
                                }
                                foundItems.add(
                                    existing.copy(
                                        folderName = currentFolderName,
                                        folderUriString = treeUriString
                                    )
                                )
                            } else {
                                val entity = extractMetadataFromUri(
                                    uri = fileUri,
                                    folderName = currentFolderName,
                                    folderUriString = treeUriString,
                                    knownName = childName,
                                    knownMime = childMime,
                                    knownSize = childSize
                                )
                                val insertedId = dao.insertMedia(entity)
                                val saved = if (insertedId > 0L) {
                                    entity.copy(id = insertedId)
                                } else {
                                    dao.getMediaByUri(fileUri.toString())
                                }
                                if (saved != null) foundItems.add(saved)
                            }
                        }
                    }
                }
            }
        }
        return foundItems
    }

    private fun isSupportedMediaFile(mime: String, name: String): Boolean {
        if (mime.startsWith("video/") || mime.startsWith("audio/")) return true
        val lower = name.lowercase()
        return MEDIA_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun queryDocumentDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun inferFolderNameFromUri(uri: Uri): String {
        val docId = runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.getDocumentId(uri)
            } else null
        }.getOrNull()

        if (!docId.isNullOrBlank() && docId.contains('/')) {
            val pathPart = docId.substringAfter(':').substringBeforeLast('/', "")
            val parent = pathPart.substringAfterLast('/', "").trim()
            if (parent.isNotBlank()) return parent
        }
        val segments = uri.pathSegments
        if (segments.size >= 2) {
            val candidate = segments[segments.size - 2]
            if (candidate.isNotBlank() && candidate != "document" && candidate != "media") {
                return candidate
            }
        }
        return "개별 추가 파일"
    }

    suspend fun addNetworkStream(
        url: String,
        customTitle: String,
        mediaType: MediaType
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val existing = dao.getMediaByUri(trimmedUrl)
        if (existing != null) return@withContext existing

        val fallbackTitle = Uri.parse(trimmedUrl).lastPathSegment
            ?.substringBefore("?")
            ?.takeIf { it.isNotBlank() } ?: "네트워크 스트림"
        val title = customTitle.trim().ifBlank { fallbackTitle }
        val mime = when {
            trimmedUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            mediaType == MediaType.AUDIO -> "audio/*"
            else -> "video/*"
        }
        val entity = MediaItemEntity(
            uriString = trimmedUrl,
            title = title,
            artist = "네트워크 스트림",
            mediaType = mediaType,
            mimeType = mime,
            folderName = "네트워크 스트림",
            isNetworkStream = true
        )
        val id = dao.insertMedia(entity)
        if (id > 0L) entity.copy(id = id) else dao.getMediaByUri(trimmedUrl) ?: entity
    }

    suspend fun updatePlaybackPosition(id: Long, positionMs: Long) {
        dao.updatePlaybackPosition(id, positionMs.coerceAtLeast(0L), System.currentTimeMillis())
    }

    suspend fun markPlayed(id: Long) {
        dao.incrementPlayCount(id, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(id: Long, current: Boolean) {
        dao.setFavorite(id, !current)
    }

    suspend fun attachSubtitle(id: Long, subtitleUri: Uri?) {
        subtitleUri?.let { tryPersistPermission(it) }
        dao.updateSubtitleUri(id, subtitleUri?.toString())
    }

    suspend fun renameMedia(id: Long, newTitle: String) {
        if (newTitle.isNotBlank()) {
            dao.updateTitle(id, newTitle.trim())
        }
    }

    suspend fun deleteMedia(item: MediaItemEntity) = withContext(Dispatchers.IO) {
        item.thumbnailPath?.let { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteMediaById(item.id)
    }

    suspend fun deleteMediaBatch(items: List<MediaItemEntity>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.forEach { item ->
            item.thumbnailPath?.let { path -> runCatching { File(path).delete() } }
        }
        items.map { it.id }.chunked(500).forEach { chunk ->
            dao.deleteMediaByIds(chunk)
        }
    }

    suspend fun setFavoriteBatch(ids: List<Long>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(500).forEach { chunk ->
            dao.setFavoriteForIds(chunk, isFavorite)
        }
    }

    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearPlaylistItems(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addMediaToPlaylist(playlistId: Long, mediaId: Long, orderIndex: Int = 0) {
        dao.addMediaToPlaylist(PlaylistMediaCrossRef(playlistId, mediaId, orderIndex))
    }

    suspend fun addMediaBatchToPlaylist(playlistId: Long, mediaIds: List<Long>) = withContext(Dispatchers.IO) {
        mediaIds.forEachIndexed { idx, mediaId ->
            dao.addMediaToPlaylist(PlaylistMediaCrossRef(playlistId, mediaId, idx))
        }
    }

    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: Long) {
        dao.removeMediaFromPlaylist(playlistId, mediaId)
    }

    suspend fun addBookmark(mediaId: Long, positionMs: Long, label: String) {
        dao.insertBookmark(
            BookmarkEntity(
                mediaId = mediaId,
                positionMs = positionMs,
                label = label.trim().ifBlank { "북마크" }
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        dao.deleteBookmark(bookmarkId)
    }

    private fun tryPersistPermission(uri: Uri) {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun extractMetadataFromUri(
        uri: Uri,
        folderName: String,
        folderUriString: String?,
        knownName: String? = null,
        knownMime: String? = null,
        knownSize: Long = 0L
    ): MediaItemEntity {
        val resolver = context.contentResolver
        val mimeType = knownMime?.takeIf { it.isNotBlank() } ?: (resolver.getType(uri) ?: "")
        var displayName = knownName.orEmpty()
        var fileSize = knownSize

        if (displayName.isBlank() || fileSize <= 0L) {
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (displayName.isBlank() && nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: ""
                        if (fileSize <= 0L && sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        val retriever = MediaMetadataRetriever()
        var metaTitle: String? = null
        var artist: String? = null
        var durationMs = 0L
        var width = 0
        var height = 0
        var hasVideo = mimeType.startsWith("video/")
        var thumbnailPath: String? = null

        try {
            retriever.setDataSource(context, uri)
            metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val hasVideoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideoMeta.equals("yes", ignoreCase = true) || width > 0) {
                hasVideo = true
            } else if (mimeType.startsWith("audio/")) {
                hasVideo = false
            }

            if (hasVideo) {
                val frameTimeUs = if (durationMs > 4000L) 2_000_000L else (durationMs * 500L)
                val frame = retriever.getFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    thumbnailPath = saveThumbnailBitmap(uri.toString(), frame)
                    frame.recycle()
                }
            } else {
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    thumbnailPath = saveThumbnailBytes(uri.toString(), artBytes)
                }
            }
        } catch (_: Throwable) {
            // Fallback gracefully when OEM MediaMetadataRetriever fails on external/USB URIs
        } finally {
            runCatching { retriever.release() }
        }

        val cleanName = displayName.substringBeforeLast('.').ifBlank {
            uri.lastPathSegment?.substringBeforeLast('.') ?: "미디어 파일"
        }
        val finalTitle = if (!hasVideo && !metaTitle.isNullOrBlank()) {
            metaTitle
        } else {
            cleanName
        }

        val resolvedType = when {
            hasVideo -> MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            displayName.lowercase().let {
                it.endsWith(".mp3") || it.endsWith(".flac") || it.endsWith(".wav") ||
                    it.endsWith(".aac") || it.endsWith(".ogg") || it.endsWith(".m4a") ||
                    it.endsWith(".opus") || it.endsWith(".wma")
            } -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }

        return MediaItemEntity(
            uriString = uri.toString(),
            title = finalTitle,
            artist = artist?.trim().orEmpty(),
            mediaType = resolvedType,
            mimeType = mimeType,
            durationMs = durationMs,
            fileSizeBytes = fileSize,
            width = width,
            height = height,
            thumbnailPath = thumbnailPath,
            folderName = folderName,
            folderUriString = folderUriString
        )
    }

    private fun saveThumbnailBitmap(key: String, bitmap: Bitmap): String? {
        return runCatching {
            val maxDim = 480
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val file = File(dir, "thumb_${key.hashCode()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            if (scaled !== bitmap) scaled.recycle()
            file.absolutePath
        }.getOrNull()
    }

    private fun saveThumbnailBytes(key: String, bytes: ByteArray): String? {
        return runCatching {
            val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val file = File(dir, "art_${key.hashCode()}.jpg")
            FileOutputStream(file).use { out -> out.write(bytes) }
            file.absolutePath
        }.getOrNull()
    }

    companion object {
        private val MEDIA_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".3gp", ".ts",
            ".mp3", ".m4a", ".flac", ".wav", ".aac", ".ogg", ".opus", ".wma"
        )
    }
}
