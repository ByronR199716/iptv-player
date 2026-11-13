package com.example.iptvplayer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val TYPE_M3U = "m3u"
const val TYPE_XTREAM = "xtream"
const val DEFAULT_GROUP = "Sin grupo"

/** Una lista del usuario: M3U (URL o archivo) o servidor Xtream Codes. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    /** M3U: URL o content:// del archivo. Xtream: URL del servidor (http://host:puerto). */
    val source: String,
    val username: String = "",
    val password: String = "",
    /** URL del XMLTV (guía). Opcional; se autodetecta en Xtream y en cabeceras M3U. */
    val epgUrl: String = "",
    val updatedAt: Long = 0
)

@Entity(
    tableName = "channels",
    indices = [
        Index("playlistId", "groupTitle"),
        Index("playlistId", "sortIndex"),
        Index("playlistId", "streamUrl")
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val sortIndex: Int,
    val name: String,
    val streamUrl: String,
    val logo: String,
    val groupTitle: String,
    val tvgId: String
)

@Entity(tableName = "favorites", primaryKeys = ["playlistId", "streamUrl"])
data class FavoriteEntity(
    val playlistId: Long,
    val streamUrl: String
)

@Entity(
    tableName = "epg",
    indices = [Index("playlistId", "channelKey", "stop")]
)
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** id de canal XMLTV en minúsculas (coincide con tvg-id / epg_channel_id). */
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String
)

/** Canal + si es favorito (resultado del JOIN). */
data class ChannelItem(
    @Embedded val channel: ChannelEntity,
    val isFavorite: Boolean
)
