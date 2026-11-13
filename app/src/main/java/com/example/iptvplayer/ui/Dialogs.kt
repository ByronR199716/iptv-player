package com.example.iptvplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iptvplayer.data.PlaylistEntity
import com.example.iptvplayer.data.TYPE_M3U
import com.example.iptvplayer.data.TYPE_XTREAM

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (PlaylistEntity) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var m3uSource by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Algunos proveedores no permiten permisos persistentes; se usa mientras dure.
            }
            m3uSource = uri.toString()
            if (name.isBlank()) {
                name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
            }
        }
    }

    val valid = if (tab == 0) m3uSource.isNotBlank()
    else server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("M3U") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Xtream") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (opcional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                if (tab == 0) {
                    OutlinedTextField(
                        value = m3uSource,
                        onValueChange = { m3uSource = it },
                        label = { Text("URL de la lista (.m3u / .m3u8)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text("O elegir archivo del dispositivo")
                    }
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Servidor (http://host:puerto)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Usuario") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it },
                    label = { Text("URL de guía XMLTV (opcional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        if (tab == 0) {
                            PlaylistEntity(
                                name = name.trim().ifBlank { "Lista M3U" },
                                type = TYPE_M3U,
                                source = m3uSource.trim(),
                                epgUrl = epg.trim()
                            )
                        } else {
                            PlaylistEntity(
                                name = name.trim().ifBlank { "Xtream" },
                                type = TYPE_XTREAM,
                                source = server.trim(),
                                username = user.trim(),
                                password = pass.trim(),
                                epgUrl = epg.trim()
                            )
                        }
                    )
                }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ManagePlaylistsDialog(
    playlists: List<PlaylistEntity>,
    selectedId: Long?,
    busy: Boolean,
    onSelect: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    var toDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mis listas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (p.id == selectedId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (p.type == TYPE_XTREAM) "Xtream" else "M3U",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(enabled = !busy, onClick = { onRefresh(p.id) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                        IconButton(onClick = { toDelete = p }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Agregar lista") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar \"${p.name}\" con sus canales y favoritos?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(p.id)
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
