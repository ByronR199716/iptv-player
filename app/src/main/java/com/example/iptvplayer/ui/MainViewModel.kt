package com.example.iptvplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.iptvplayer.data.ChannelEntity
import com.example.iptvplayer.data.ChannelItem
import com.example.iptvplayer.data.EpgEntity
import com.example.iptvplayer.data.Net
import com.example.iptvplayer.data.PlaylistEntity
import com.example.iptvplayer.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Status(val busy: Boolean = false, val message: String = "")

private data class BrowseFilter(
    val playlistId: Long?,
    val group: String,
    val query: String,
    val onlyFavorites: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // ---------- Listas ----------

    val playlists: StateFlow<List<PlaylistEntity>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedId = MutableStateFlow(prefs.getLong("selected", -1L))

    /** Lista activa: la elegida, o la primera si la elegida ya no existe. */
    val selectedPlaylist: StateFlow<PlaylistEntity?> =
        combine(playlists, selectedId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val status = MutableStateFlow(Status())

    fun selectPlaylist(id: Long) {
        selectedId.value = id
        prefs.edit().putLong("selected", id).apply()
        group.value = ""
        query.value = ""
        onlyFavorites.value = false
    }

    fun addPlaylist(p: PlaylistEntity) {
        viewModelScope.launch {
            val id = repo.addPlaylist(p)
            selectPlaylist(id)
            refresh(id)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repo.deletePlaylist(id) }
    }

    fun refresh(id: Long) {
        if (status.value.busy) return
        viewModelScope.launch {
            status.value = Status(true, "Cargando…")
            try {
                val n = repo.refreshPlaylist(id) { status.value = Status(true, it) }
                val epgNote = try {
                    val programs = repo.refreshEpg(id) { status.value = Status(true, it) }
                    if (programs > 0) " · guía: $programs programas" else ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    " · guía no disponible (${e.message ?: e.javaClass.simpleName})"
                }
                status.value = Status(false, "$n canales cargados$epgNote")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = Status(false, "Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ---------- Explorar canales ----------

    val group = MutableStateFlow("")
    val query = MutableStateFlow("")
    val onlyFavorites = MutableStateFlow(false)

    val channelPaging: Flow<PagingData<ChannelItem>> =
        combine(
            selectedPlaylist.map { it?.id }.distinctUntilChanged(),
            group,
            query.debounce(250),
            onlyFavorites
        ) { pid, g, q, fav -> BrowseFilter(pid, g, q, fav) }
            .distinctUntilChanged()
            .flatMapLatest { f ->
                val pid = f.playlistId
                if (pid == null) {
                    flowOf(PagingData.empty<ChannelItem>())
                } else {
                    Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) {
                        repo.pagingSource(pid, f.group, f.query.trim(), f.onlyFavorites)
                    }.flow
                }
            }
            .cachedIn(viewModelScope)

    val groups: StateFlow<List<String>> = selectedPlaylist.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { pid -> if (pid == null) flowOf(emptyList()) else repo.observeGroups(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channelCount: StateFlow<Int> = selectedPlaylist.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { pid -> if (pid == null) flowOf(0) else repo.observeCount(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setGroup(g: String) {
        group.value = g
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun toggleOnlyFavorites() {
        onlyFavorites.value = !onlyFavorites.value
    }

    fun toggleFavorite(ch: ChannelEntity) {
        viewModelScope.launch { repo.toggleFavorite(ch) }
    }

    suspend fun nowTitle(ch: ChannelEntity): String? = repo.nowProgram(ch)?.title?.ifBlank { null }

    // ---------- Reproducción ----------

    private val playerDelegate = lazy { buildPlayer() }
    val player: ExoPlayer by playerDelegate

    /** Canal en reproducción; si no es null se muestra la pantalla del reproductor. */
    val current = MutableStateFlow<ChannelEntity?>(null)
    val playerError = MutableStateFlow<String?>(null)
    private var retriedAsHls = false

    val currentEpg: StateFlow<List<EpgEntity>> = current
        .flatMapLatest { ch ->
            if (ch == null) {
                flowOf(emptyList())
            } else {
                flow {
                    while (true) {
                        emit(repo.epgFor(ch))
                        delay(60_000)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentIsFavorite: StateFlow<Boolean> = current
        .flatMapLatest { ch ->
            if (ch == null) flowOf(false) else repo.observeFavorite(ch.playlistId, ch.streamUrl)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        val ctx = getApplication<Application>()
        val mediaSourceFactory = DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(OkHttpDataSource.Factory(Net.client))
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlayerError(error)
                    }
                })
            }
    }

    private fun handlePlayerError(error: PlaybackException) {
        val ch = current.value ?: return
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }

            // URL sin extensión que en realidad es HLS: reintentar indicando el tipo
            !retriedAsHls && (
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                ) -> {
                retriedAsHls = true
                setMedia(ch.streamUrl, MimeTypes.APPLICATION_M3U8)
            }

            else -> playerError.value = "No se pudo reproducir este canal (${error.errorCodeName})"
        }
    }

    private fun setMedia(url: String, mimeType: String?) {
        val item = MediaItem.Builder().setUri(url).apply {
            if (mimeType != null) setMimeType(mimeType)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun play(ch: ChannelEntity) {
        current.value = ch
        playerError.value = null
        retriedAsHls = false
        setMedia(ch.streamUrl, null)
    }

    fun retry() {
        current.value?.let { play(it) }
    }

    /** Cambia al canal siguiente (true) o anterior (false) dentro del grupo activo. */
    fun zap(forward: Boolean) {
        val ch = current.value ?: return
        viewModelScope.launch {
            repo.neighbor(ch, group.value, forward)?.let { play(it) }
        }
    }

    fun closePlayer() {
        if (playerDelegate.isInitialized()) {
            player.stop()
            player.clearMediaItems()
        }
        current.value = null
        playerError.value = null
    }

    override fun onCleared() {
        if (playerDelegate.isInitialized()) player.release()
        super.onCleared()
    }
}
