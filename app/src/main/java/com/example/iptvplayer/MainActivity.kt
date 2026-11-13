package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptvplayer.ui.BrowseScreen
import com.example.iptvplayer.ui.MainViewModel
import com.example.iptvplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9FA8FF),
                    primaryContainer = Color(0xFF3F3FA8),
                    background = Color(0xFF121212),
                    surface = Color(0xFF121212)
                )
            ) {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val current by vm.current.collectAsState()
    val listState = rememberLazyListState()
    if (current != null) {
        PlayerScreen(vm)
    } else {
        BrowseScreen(vm, listState)
    }
}
