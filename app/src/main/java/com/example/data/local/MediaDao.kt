package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    fun observeAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE uriString = :uriString LIMIT 1")
    suspend fun getMediaByUri(uriString: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedia(item: MediaItemEntity): Long

    @Query("UPDATE media_items SET folderName = :folderName, folderUriString = :folderUriString WHERE uriString = :uriString")
    suspend fun updateFolderAssociation(uriString: String, folderName: String, folderUriString: String?)

    @Query("UPDATE media_items SET lastPositionMs = :positionMs, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long, playedAt: Long)

    @Query("UPDATE media_items SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, playedAt: Long)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media_items SET subtitleUriString = :subtitleUri WHERE id = :id")
    suspend fun updateSubtitleUri(id: Long, subtitleUri: String?)

    @Query("UPDATE media_items SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: Long)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<Long>)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteForIds(ids: List<Long>, isFavorite: Boolean)

    // Folders
    @Query("SELECT * FROM folders ORDER BY addedAt DESC")
    fun observeLinkedFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllLinkedFolders(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET lastScannedAt = :scannedAt WHERE treeUriString = :treeUriString")
    suspend fun updateFolderScannedAt(treeUriString: String, scannedAt: Long)

    @Query("DELETE FROM folders WHERE treeUriString = :treeUriString")
    suspend fun deleteFolderByTreeUri(treeUriString: String)

    @Query("DELETE FROM media_items WHERE folderUriString = :treeUriString")
    suspend fun deleteMediaByFolderUri(treeUriString: String)

    @Query("UPDATE media_items SET folderUriString = NULL, folderName = '개별 추가 파일' WHERE folderUriString = :treeUriString")
    suspend fun detachMediaFromFolderUri(treeUriString: String)

    @Query("DELETE FROM media_items WHERE folderUriString IS NULL AND folderName = :folderName")
    suspend fun deleteAutoFolderMedia(folderName: String)

    // Playlists
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithMedia(): Flow<List<PlaylistWithMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE mediaId = :mediaId ORDER BY positionMs ASC")
    fun observeBookmarksForMedia(mediaId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)
}
