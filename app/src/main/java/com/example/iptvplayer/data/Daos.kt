package com.example.iptvplayer.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Update
    suspend fun update(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Query(
        """
        SELECT c.*, CASE WHEN f.streamUrl IS NULL THEN 0 ELSE 1 END AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.playlistId = c.playlistId AND f.streamUrl = c.streamUrl
        WHERE c.playlistId = :playlistId
          AND (:grp = '' OR c.groupTitle = :grp)
          AND (:q = '' OR c.name LIKE '%' || :q || '%')
          AND (:onlyFav = 0 OR f.streamUrl IS NOT NULL)
        ORDER BY c.sortIndex
        """
    )
    fun paging(playlistId: Long, grp: String, q: String, onlyFav: Int): PagingSource<Int, ChannelItem>

    @Query("SELECT groupTitle FROM channels WHERE playlistId = :pid GROUP BY groupTitle ORDER BY MIN(sortIndex)")
    fun observeGroups(pid: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :pid")
    fun observeCount(pid: Long): Flow<Int>

    @Insert
    suspend fun insertAll(list: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :pid")
    suspend fun deleteByPlaylist(pid: Long)

    // Cambio de canal (arriba/abajo) respetando el grupo activo
    @Query(
        "SELECT * FROM channels WHERE playlistId = :pid AND sortIndex > :idx " +
            "AND (:grp = '' OR groupTitle = :grp) ORDER BY sortIndex LIMIT 1"
    )
    suspend fun next(pid: Long, idx: Int, grp: String): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :pid AND sortIndex < :idx " +
            "AND (:grp = '' OR groupTitle = :grp) ORDER BY sortIndex DESC LIMIT 1"
    )
    suspend fun prev(pid: Long, idx: Int, grp: String): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :pid " +
            "AND (:grp = '' OR groupTitle = :grp) ORDER BY sortIndex LIMIT 1"
    )
    suspend fun first(pid: Long, grp: String): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :pid " +
            "AND (:grp = '' OR groupTitle = :grp) ORDER BY sortIndex DESC LIMIT 1"
    )
    suspend fun last(pid: Long, grp: String): ChannelEntity?

    // Favoritos
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :pid AND streamUrl = :url")
    suspend fun removeFavorite(pid: Long, url: String)

    @Query("DELETE FROM favorites WHERE playlistId = :pid")
    suspend fun deleteFavorites(pid: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :pid AND streamUrl = :url)")
    suspend fun isFavorite(pid: Long, url: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :pid AND streamUrl = :url)")
    fun observeFavorite(pid: Long, url: String): Flow<Boolean>
}

@Dao
interface EpgDao {
    @Query(
        "SELECT * FROM epg WHERE playlistId = :pid AND channelKey = :key AND stop > :now " +
            "ORDER BY start LIMIT :limit"
    )
    suspend fun upcoming(pid: Long, key: String, now: Long, limit: Int): List<EpgEntity>

    @Query("DELETE FROM epg WHERE playlistId = :pid")
    suspend fun deleteByPlaylist(pid: Long)

    /** Bloqueante a propósito: se llama desde el parser XML en un hilo de IO. */
    @Insert
    fun insertBatch(list: List<EpgEntity>)
}
