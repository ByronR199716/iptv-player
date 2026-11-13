package com.example.iptvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel) {
    val channel by vm.current.collectAsState()
    val epg by vm.currentEpg.collectAsState()
    val isFavorite by vm.currentIsFavorite.collectAsState()
    val error by vm.playerError.collectAsState()
    val player = vm.player

    var overlay by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    BackHandler { vm.closePlayer() }

    // Pantalla completa inmersiva + pantalla siempre encendida mientras se ve TV
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    // Pausar al salir de la app, reanudar al volver
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
            else if (event == Lifecycle.Event.ON_START) player.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Estado de buffering
    DisposableEffect(player) {
        buffering = player.playbackState == Player.STATE_BUFFERING
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(channel?.id) { overlay = true }
    LaunchedEffect(overlay, channel?.id) {
        if (overlay) {
            delay(4000)
            overlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { vm.zap(false); true }
                    Key.DirectionDown, Key.ChannelDown -> { vm.zap(true); true }
                    Key.DirectionRight -> { channel?.let { vm.toggleFavorite(it) }; overlay = true; true }
                    Key.DirectionCenter, Key.Enter -> { overlay = !overlay; true }
                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.player = player }
        )

        // Capa transparente para tocar y mostrar/ocultar la información
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { overlay = !overlay }
        )

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(message, color = Color.White, textAlign = TextAlign.Center)
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { vm.retry() }) { Text("Reintentar") }
                    Button(onClick = { vm.zap(true) }) { Text("Siguiente canal") }
                }
            }
        }

        AnimatedVisibility(
            visible = overlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .systemBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.closePlayer() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Text(
                        channel?.name.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { channel?.let { vm.toggleFavorite(it) } }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }

                // Barra inferior: guía + cambio de canal
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .systemBarsPadding()
                        .padding(16.dp)
                ) {
                    val now = System.currentTimeMillis()
                    val currentProgram = epg.firstOrNull()?.takeIf { it.start <= now }
                    if (currentProgram != null) {
                        Text(
                            currentProgram.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${timeFmt.format(Date(currentProgram.start))} – ${timeFmt.format(Date(currentProgram.stop))}",
                            color = Color(0xFFCCCCCC),
                            style = MaterialTheme.typography.labelMedium
                        )
                        val fraction = ((now - currentProgram.start).toFloat() /
                            (currentProgram.stop - currentProgram.start).toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    }
                    val upcoming = if (currentProgram != null) epg.drop(1) else epg
                    upcoming.take(2).forEach { p ->
                        Text(
                            "${timeFmt.format(Date(p.start))}  ${p.title}",
                            color = Color(0xFFCCCCCC),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "▲▼ cambiar canal   ·   OK info   ·   ► favorito",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF999999),
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(onClick = { vm.zap(false) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Canal anterior", tint = Color.White)
                        }
                        IconButton(onClick = { vm.zap(true) }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Canal siguiente", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
