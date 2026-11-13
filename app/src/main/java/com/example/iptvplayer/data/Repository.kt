package com.example.iptvplayer.data

import android.content.Context
import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

class Repository(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    private val playlistDao = db.playlists()
    private val channelDao = db.channels()
    private val epgDao = db.epg()

    // ---------- Consultas ----------

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()

    fun pagingSource(
        playlistId: Long,
        group: String,
        query: String,
        onlyFavorites: Boolean
    ): PagingSource<Int, ChannelItem> =
        channelDao.paging(playlistId, group, query, if (onlyFavorites) 1 else 0)

    fun observeGroups(playlistId: Long): Flow<List<String>> = channelDao.observeGroups(playlistId)
    fun observeCount(playlistId: Long): Flow<Int> = channelDao.observeCount(playlistId)
    fun observeFavorite(playlistId: Long, url: String): Flow<Boolean> =
        channelDao.observeFavorite(playlistId, url)

    // ---------- Listas ----------

    suspend fun addPlaylist(p: PlaylistEntity): Long = playlistDao.insert(p)

    suspend fun deletePlaylist(id: Long) {
        db.withTransaction {
            channelDao.deleteByPlaylist(id)
            channelDao.deleteFavorites(id)
            epgDao.deleteByPlaylist(id)
            playlistDao.delete(id)
        }
    }

    /** Descarga y reemplaza los canales de la lista. Devuelve la cantidad de canales. */
    suspend fun refreshPlaylist(id: Long, onStatus: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val p = playlistDao.get(id) ?: throw IOException("Lista no encontrada")
            onStatus("Descargando canales…")
            val loaded = if (p.type == TYPE_XTREAM) loadXtream(p) else loadM3u(p)
            if (loaded.channels.isEmpty()) throw IOException("La lista no contiene canales")

            onStatus("Guardando ${loaded.channels.size} canales…")
            val entities = loaded.channels.mapIndexed { i, c ->
                ChannelEntity(
                    playlistId = id,
                    sortIndex = i,
                    name = c.name,
                    streamUrl = c.url,
                    logo = c.logo,
                    groupTitle = c.group,
                    tvgId = c.tvgId
                )
            }
            db.withTransaction {
                channelDao.deleteByPlaylist(id)
                entities.chunked(1000).forEach { channelDao.insertAll(it) }
            }
            playlistDao.update(
                p.copy(
                    epgUrl = p.epgUrl.ifBlank { loaded.epgUrl },
                    updatedAt = System.currentTimeMillis()
                )
            )
            entities.size
        }

    /** Descarga la guía XMLTV (puede venir en .gz). Devuelve la cantidad de programas guardados. */
    suspend fun refreshEpg(id: Long, onStatus: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val p = playlistDao.get(id) ?: return@withContext 0
            if (p.epgUrl.isBlank()) return@withContext 0
            onStatus("Descargando guía de programación…")

            val now = System.currentTimeMillis()
            val request = Request.Builder().url(p.epgUrl).build()
            Net.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Guía: HTTP ${resp.code}")
                val raw = BufferedInputStream(resp.body!!.byteStream())
                raw.mark(2)
                val b1 = raw.read()
                val b2 = raw.read()
                raw.reset()
                val stream: InputStream =
                    if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw

                epgDao.deleteByPlaylist(id)
                XmltvParser().parse(
                    input = stream,
                    playlistId = id,
                    minStop = now - 2 * HOUR,
                    maxStart = now + 48 * HOUR
                ) { batch -> epgDao.insertBatch(batch) }
            }
        }

    // ---------- Favoritos ----------

    suspend fun toggleFavorite(ch: ChannelEntity) {
        if (channelDao.isFavorite(ch.playlistId, ch.streamUrl)) {
            channelDao.removeFavorite(ch.playlistId, ch.streamUrl)
        } else {
            channelDao.addFavorite(FavoriteEntity(ch.playlistId, ch.streamUrl))
        }
    }

    // ---------- Guía y navegación entre canales ----------

    suspend fun nowProgram(ch: ChannelEntity): EpgEntity? {
        if (ch.tvgId.isBlank()) return null
        val now = System.currentTimeMillis()
        return epgDao.upcoming(ch.playlistId, ch.tvgId.lowercase(), now, 1)
            .firstOrNull()?.takeIf { it.start <= now }
    }

    suspend fun epgFor(ch: ChannelEntity, limit: Int = 4): List<EpgEntity> {
        if (ch.tvgId.isBlank()) return emptyList()
        return epgDao.upcoming(ch.playlistId, ch.tvgId.lowercase(), System.currentTimeMillis(), limit)
    }

    /** Canal siguiente/anterior dentro del grupo activo, dando la vuelta al llegar al final. */
    suspend fun neighbor(ch: ChannelEntity, group: String, forward: Boolean): ChannelEntity? =
        if (forward) {
            channelDao.next(ch.playlistId, ch.sortIndex, group) ?: channelDao.first(ch.playlistId, group)
        } else {
            channelDao.prev(ch.playlistId, ch.sortIndex, group) ?: channelDao.last(ch.playlistId, group)
        }

    // ---------- Carga de listas ----------

    private fun loadM3u(p: PlaylistEntity): M3uResult {
        openM3u(p.source).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                return M3uParser.parse(reader)
            }
        }
    }

    private fun openM3u(source: String): InputStream {
        val s = source.trim()
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
            val resp = Net.client.newCall(Request.Builder().url(s).build()).execute()
            if (!resp.isSuccessful) {
                val code = resp.code
                resp.close()
                throw IOException("HTTP $code al descargar la lista")
            }
            resp.body?.byteStream() ?: throw IOException("Respuesta vacía")
        } else {
            ctx.contentResolver.openInputStream(Uri.parse(s))
                ?: throw IOException("No se pudo abrir el archivo")
        }
    }

    private fun loadXtream(p: PlaylistEntity): M3uResult {
        var base = p.source.trim().trimEnd('/')
        if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) base = "http://$base"
        val user = URLEncoder.encode(p.username, "UTF-8")
        val pass = URLEncoder.encode(p.password, "UTF-8")
        val api = "$base/player_api.php?username=$user&password=$pass"

        val auth = JSONObject(httpGetString(api))
        val info = auth.optJSONObject("user_info")
        if (info == null || info.optString("auth") != "1") {
            throw IOException("Usuario o contraseña incorrectos")
        }

        val categories = HashMap<String, String>()
        val cats = JSONArray(httpGetString("$api&action=get_live_categories"))
        for (i in 0 until cats.length()) {
            val c = cats.getJSONObject(i)
            categories[c.str("category_id")] = c.str("category_name")
        }

        val streams = JSONArray(httpGetString("$api&action=get_live_streams"))
        val pathUser = Uri.encode(p.username)
        val pathPass = Uri.encode(p.password)
        val out = ArrayList<ParsedChannel>(streams.length())
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            val streamId = s.str("stream_id")
            if (streamId.isEmpty()) continue
            out.add(
                ParsedChannel(
                    name = s.str("name").ifBlank { "Canal ${i + 1}" },
                    url = "$base/live/$pathUser/$pathPass/$streamId.m3u8",
                    logo = s.str("stream_icon"),
                    group = categories[s.str("category_id")]?.ifBlank { null } ?: DEFAULT_GROUP,
                    tvgId = s.str("epg_channel_id")
                )
            )
        }
        return M3uResult(out, "$base/xmltv.php?username=$user&password=$pass")
    }

    private fun httpGetString(url: String): String =
        Net.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

    /** org.json devuelve "null" como texto para valores JSON null; esto lo evita. */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

    private companion object {
        const val HOUR = 3_600_000L
    }
}
