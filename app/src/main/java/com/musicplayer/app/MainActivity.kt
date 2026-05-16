package com.musicplayer.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.musicplayer.app.data.db.SongEntity
import com.musicplayer.app.ui.PlayerViewModel
import com.musicplayer.app.ui.screens.*
import com.musicplayer.app.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
        
        setContent {
            var currentTheme by remember { 
                mutableStateOf(com.musicplayer.app.ui.theme.AppTheme.valueOf(prefs.getString("theme", "NAVY") ?: "NAVY"))
            }

            MusicPlayerTheme(theme = currentTheme) {
                MusicPlayerApp(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                        prefs.edit().putString("theme", newTheme.name).apply()
                    }
                )
            }
        }
    }
}

// ─── Nav destinations ─────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Library        : Screen("library")
    object NowPlaying     : Screen("now_playing")
    object Playlists      : Screen("playlists")
    object Queue          : Screen("queue")
    object Settings       : Screen("settings")
    object Ringtone       : Screen("ringtone/{songId}") {
        fun route(id: Long) = "ringtone/$id"
    }
    object PlaylistDetail : Screen("playlist/{id}") {
        fun route(id: Long) = "playlist/$id"
    }
}

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun MusicPlayerApp(
    currentTheme: com.musicplayer.app.ui.theme.AppTheme,
    onThemeChange: (com.musicplayer.app.ui.theme.AppTheme) -> Unit
) {
    val navController = rememberNavController()
    val vm: PlayerViewModel = hiltViewModel()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val bottomItems = listOf(
        Triple(Screen.Library.route,    "Library",   Icons.Default.LibraryMusic),
        Triple(Screen.NowPlaying.route, "Playing",   Icons.Default.PlayCircleOutline),
        Triple(Screen.Playlists.route,  "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
        Triple(Screen.Queue.route,      "Queue",     Icons.AutoMirrored.Filled.List),
        Triple(Screen.Settings.route,   "Settings",  Icons.Default.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                bottomItems.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(Screen.Library.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(vm) { }
            }
            composable(Screen.NowPlaying.route) {
                NowPlayingScreen(vm)
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(vm) { playlist ->
                    navController.navigate(Screen.PlaylistDetail.route(playlist.id))
                }
            }
            composable(Screen.Queue.route) {
                QueueScreen(vm)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(vm, currentTheme, onThemeChange)
            }
            composable(Screen.Ringtone.route) { backStack ->
                val songId = backStack.arguments?.getString("songId")?.toLongOrNull()
                val song = vm.allSongs.collectAsState().value.find { it.id == songId }
                song?.let { RingtoneScreen(it) }
            }
            composable(Screen.PlaylistDetail.route) { backStack ->
                val playlistId = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                PlaylistDetailScreen(vm, playlistId) { _, idx, songs ->
                    vm.playSongs(songs, idx)
                }
            }
        }
    }
}

// ─── Playlist detail ──────────────────────────────────────────────────────────

@Composable
fun PlaylistDetailScreen(
    vm: PlayerViewModel,
    playlistId: Long,
    onSongClick: (SongEntity, Int, List<SongEntity>) -> Unit
) {
    val playlistFlow = remember(playlistId) { vm.getPlaylistWithSongs(playlistId) }
    val data by playlistFlow.collectAsState(initial = null)
    val allSongs by vm.allSongs.collectAsState()
    var showAddSongs by remember { mutableStateOf(false) }

    if (showAddSongs) {
        val existingIds = data?.songs?.map { it.id }?.toSet() ?: emptySet()
        val available = allSongs.filter { it.id !in existingIds }
        AlertDialog(
            onDismissRequest = { showAddSongs = false },
            title = { Text("Add Songs") },
            text = {
                if (available.isEmpty()) {
                    Text("All songs are already in this playlist.")
                } else {
                    LazyColumn {
                        itemsIndexed(available, key = { _, s -> s.id }) { _, song ->
                            ListItem(
                                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable {
                                    vm.addSongToPlaylist(playlistId, song.id)
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSongs = false }) { Text("Done") }
            }
        )
    }

    data?.let { pw ->
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddSongs = true }) {
                    Icon(Icons.Default.Add, "Add Songs")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    pw.playlist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
                if (pw.songs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tap + to add songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${pw.songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onSongClick(pw.songs[0], 0, pw.songs) }) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play All")
                        }
                    }
                    LazyColumn {
                        itemsIndexed(pw.songs, key = { _, s -> s.id }) { idx, song ->
                            SongRow(
                                song = song,
                                isPlaying = false,
                                vm = vm,
                                onClick = { onSongClick(song, idx, pw.songs) },
                                onAddToQueue = { vm.addToQueue(song) }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}