package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.data.model.MediaType

@Entity(
    tableName = "media_items",
    indices = [Index(value = ["uriString"], unique = true)]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val title: String,
    val artist: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null,
    val subtitleUriString: String? = null,
    val folderName: String = "기타 미디어",
    val folderUriString: String? = null,
    val lastPositionMs: Long = 0L,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val isNetworkStream: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L
)

@Entity(
    tableName = "folders",
    indices = [Index(value = ["treeUriString"], unique = true)]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUriString: String,
    val folderName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"]
)
data class PlaylistMediaCrossRef(
    val playlistId: Long,
    val mediaId: Long,
    val orderIndex: Int = 0
)

data class PlaylistWithMedia(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistMediaCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "mediaId"
        )
    )
    val items: List<MediaItemEntity>
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
