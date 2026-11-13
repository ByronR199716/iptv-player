package com.example.iptvplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.iptvplayer.R
import com.example.iptvplayer.data.ChannelEntity
import com.example.iptvplayer.data.ChannelItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: MainViewModel, listState: LazyListState) {
    val playlists by vm.playlists.collectAsState()
    val selected by vm.selectedPlaylist.collectAsState()
    val groups by vm.groups.collectAsState()
    val count by vm.channelCount.collectAsState()
    val group by vm.group.collectAsState()
    val query by vm.query.collectAsState()
    val onlyFav by vm.onlyFavorites.collectAsState()
    val status by vm.status.collectAsState()
    val items = vm.channelPaging.collectAsLazyPagingItems()

    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (!status.busy && status.message.isNotEmpty()) snackbar.showSnackbar(status.message)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showManage = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selected?.name ?: "mundoNET", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (selected != null) {
                                Text("$count canales", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar lista")
                    }
                },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { vm.toggleOnlyFavorites() }) {
                            Icon(
                                if (onlyFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Solo favoritos",
                                tint = if (onlyFav) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            enabled = !status.busy,
                            onClick = { selected?.let { vm.refresh(it.id) } }
                        ) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar lista") }
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Agregar lista")
                    }
                }
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                busy = status.busy,
                message = status.message,
                onAdd = { showAdd = true }
            )
        } else {
            Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Buscar canal") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                            }
                        }
                    }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = group.isEmpty(),
                            onClick = { vm.setGroup("") },
                            label = { Text("Todos") }
                        )
                    }
                    items(groups) { g ->
                        FilterChip(
                            selected = g == group,
                            onClick = { vm.setGroup(g) },
                            label = { Text(g, maxLines = 1) }
                        )
                    }
                }

                if (status.busy) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Text(
                        status.message,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            count = items.itemCount,
                            key = items.itemKey { it.channel.id }
                        ) { index ->
                            val item = items[index]
                            if (item != null) {
                                ChannelRow(
                                    item = item,
                                    loadNow = vm::nowTitle,
                                    onPlay = { vm.play(item.channel) },
                                    onToggleFavorite = { vm.toggleFavorite(item.channel) }
                                )
                            }
                        }
                    }
                    if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading && !status.busy) {
                        Text(
                            if (onlyFav) "Aún no tienes favoritos en esta lista"
                            else "Sin resultados",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showManage) {
        ManagePlaylistsDialog(
            playlists = playlists,
            selectedId = selected?.id,
            busy = status.busy,
            onSelect = {
                vm.selectPlaylist(it)
                showManage = false
            },
            onRefresh = {
                vm.refresh(it)
                showManage = false
            },
            onDelete = vm::deletePlaylist,
            onAdd = {
                showManage = false
                showAdd = true
            },
            onDismiss = { showManage = false }
        )
    }
    if (showAdd) {
        AddPlaylistDialog(
            onDismiss = { showAdd = false },
            onConfirm = {
                showAdd = false
                vm.addPlaylist(it)
            }
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    busy: Boolean,
    message: String,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mundonet),
            contentDescription = "Logo de mundoNET",
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Text("Bienvenido a mundoNET", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Agrega tu lista M3U o tu servidor Xtream Codes para empezar. " +
                "Esta app no incluye canales.",
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, modifier = Modifier.padding(top = 8.dp))
        } else {
            Button(onClick = onAdd) { Text("Agregar lista") }
        }
    }
}

@Composable
private fun ChannelRow(
    item: ChannelItem,
    loadNow: suspend (ChannelEntity) -> String?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val channel = item.channel
    var focused by remember { mutableStateOf(false) }
    val now by produceState<String?>(null, channel.id) {
        value = loadNow(channel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo.isBlank()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
            } else {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                now ?: channel.groupTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (now != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (item.isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
