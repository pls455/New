package com.nova.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.music.data.MusicLibraryViewModel
import com.nova.music.data.Track
import com.nova.music.player.PlayerController
import com.nova.music.ui.NovaAlbumArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContent { NovaApp() }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

@Composable
private fun NovaApp(libraryViewModel: MusicLibraryViewModel = viewModel()) {
    var accent by remember { mutableStateOf(Color(0xFF8B7CFF)) }
    var settings by remember { mutableStateOf(false) }
    val scheme = darkColorScheme(primary = accent, secondary = Color(0xFF5CE1E6), background = Color(0xFF07080C), surface = Color(0xFF11131A))
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            if (settings) SettingsScreen(onBack = { settings = false }, accent = accent, onAccent = { accent = it }, onRefresh = libraryViewModel::refresh)
            else LibraryHome(libraryViewModel, onSettings = { settings = true })
        }
    }
}

@Composable
private fun LibraryHome(viewModel: MusicLibraryViewModel, onSettings: () -> Unit) {
    val tracks by viewModel.tracks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(context) { PlayerController(context) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var fullPlayer by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : PlayerController.Listener {
            override fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long, currentIndex: Int) {
                playing = isPlaying
                position = positionMs
                if (durationMs > 0) duration = durationMs
                if (currentIndex >= 0) selectedIndex = currentIndex
            }
            override fun onModeChanged(shuffleEnabled: Boolean, repeatMode: Int) {
                shuffle = shuffleEnabled
                repeat = repeatMode
            }
            override fun onPlayerError(message: String) { error = message }
        }
        player.addListener(listener)
        viewModel.refresh()
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(selectedIndex, playing) {
        while (selectedIndex != null && playing) { delay(250); player.refreshState() }
    }

    val filtered = remember(tracks, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) tracks else tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
    }
    val selected = selectedIndex?.let { tracks.getOrNull(it) }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (!fullPlayer) {
            LibraryContent(
                tracks = tracks,
                filtered = filtered,
                loading = loading,
                searchOpen = searchOpen,
                query = search,
                error = error,
                onSearch = { searchOpen = true },
                onCloseSearch = { search = ""; searchOpen = false },
                onQuery = { search = it },
                onSettings = onSettings,
                onRefresh = viewModel::refresh,
                onTrack = { index -> if (index >= 0) { selectedIndex = index; fullPlayer = true; player.playTrack(tracks.map { it.uri }, index) } }
            )
            AnimatedVisibility(
                visible = selected != null,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = .97f, animationSpec = tween(180)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = .97f, animationSpec = tween(140)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().zIndex(5f)
            ) {
                selected?.let { MiniPlayer(it, playing, { fullPlayer = true }, { player.togglePlayPause() }) }
            }
        } else if (selected != null) {
            NowPlayingScreen(
                track = selected, playing = playing, position = position, duration = duration.takeIf { it > 0 } ?: selected.durationMs,
                shuffle = shuffle, repeat = repeat, onBack = { fullPlayer = false }, onPlayPause = { player.togglePlayPause() },
                onSeek = { player.seekTo(it.toLong()) }, onPrevious = { player.previous() }, onNext = { player.next() },
                onShuffle = { player.setShuffle(!shuffle) }, onRepeat = { player.setRepeatMode(if (repeat == 0) 1 else if (repeat == 1) 2 else 0) }
            )
        }
    }
}

@Composable
private fun LibraryContent(
    tracks: List<Track>, filtered: List<Track>, loading: Boolean, searchOpen: Boolean, query: String, error: String?,
    onSearch: () -> Unit, onCloseSearch: () -> Unit, onQuery: (String) -> Unit, onSettings: () -> Unit,
    onRefresh: () -> Unit, onTrack: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF07080C)).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (searchOpen) {
                OutlinedTextField(query, onQuery, Modifier.weight(1f), placeholder = { Text("ابحث عن أغنية أو فنان") }, singleLine = true, shape = RoundedCornerShape(18.dp))
                IconButton(onClick = onCloseSearch) { Icon(Icons.Rounded.Close, "Close") }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("NOVA", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("YOUR MUSIC. YOUR SPACE.", fontSize = 9.sp, color = Color.White.copy(.42f), letterSpacing = 2.sp)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        Spacer(Modifier.height(22.dp))
        when {
            loading && tracks.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            tracks.isEmpty() -> EmptyLibrary(onRefresh)
            else -> {
                Text("Your library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${filtered.size} tracks", color = Color.White.copy(.42f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)) {
                    items(filtered, key = { it.id }) { track ->
                        val index = tracks.indexOfFirst { it.id == track.id }
                        TrackRow(track) { onTrack(index) }
                    }
                }
            }
        }
        error?.let { Text("تعذر تشغيل الملف: $it", color = Color(0xFFFF8A80), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.055f)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        NovaAlbumArt(track.albumId, size = 56.dp, rounded = 16.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.45f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(.58f))
    }
}

@Composable
private fun MiniPlayer(track: Track, playing: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mini")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000), RepeatMode.Restart), label = "rotation")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen), color = Color(0xFF151821).copy(.98f), shadowElevation = 10.dp) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaAlbumArt(track.albumId, Modifier.graphicsLayer { rotationZ = if (playing) rotation else 0f }, 48.dp, 24.dp)
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.48f), fontSize = 11.sp)
            }
            IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause") }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    track: Track, playing: Boolean, position: Long, duration: Long, shuffle: Boolean, repeat: Int,
    onBack: () -> Unit, onPlayPause: () -> Unit, onSeek: (Float) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "player")
    val motion by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "motion")
    val a = androidx.compose.ui.graphics.lerp(Color(0xFF7E6CFF), Color(0xFF31D7E5), motion)
    val b = androidx.compose.ui.graphics.lerp(Color(0xFFE55BFF), Color(0xFF7E6CFF), motion)
    val safeDuration = duration.coerceAtLeast(1L)
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(a.copy(.30f), Color(0xFF07080C), b.copy(.12f)))).pointerInput(Unit) {
            detectVerticalDragGestures(onVerticalDrag = { _, drag -> if (drag > 16f) scope.launch { onBack() } })
        }.padding(horizontal = 20.dp).navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("NOW PLAYING", fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(.5f))
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NovaAlbumArt(track.albumId, size = 280.dp, rounded = 34.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text(track.title, Modifier.fillMaxWidth(), fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, Modifier.fillMaxWidth(), fontSize = 14.sp, color = Color.White.copy(.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        Slider(value = position.coerceIn(0L, safeDuration).toFloat(), onValueChange = onSeek, valueRange = 0f..safeDuration.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), fontSize = 10.sp, color = Color.White.copy(.45f))
            Text(formatTime(duration), fontSize = 10.sp, color = Color.White.copy(.45f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(.6f)) }
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(34.dp)) }
            Surface(Modifier.size(68.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 12.dp) {
                IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp)) }
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = onRepeat) { Icon(Icons.Rounded.Repeat, "Repeat", tint = if (repeat != 0) MaterialTheme.colorScheme.primary else Color.White.copy(.6f)) }
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("لا توجد موسيقى", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("اسمح لـ NOVA بالوصول إلى ملفات الصوت على جهازك", color = Color.White.copy(.5f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRefresh) { Text("إعادة الفحص") }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, accent: Color, onAccent: (Color) -> Unit, onRefresh: () -> Unit) {
    var dark by remember { mutableStateOf(true) }
    var artwork by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Color(0xFF07080C)).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        Text("Appearance", fontWeight = FontWeight.Bold, color = accent)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Dark mode"); Switch(dark, { dark = it }) }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Album artwork"); Switch(artwork, { artwork = it }) }
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Rescan music") }
        Spacer(Modifier.height(22.dp))
        Text("Accent", fontWeight = FontWeight.Bold, color = accent)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Color(0xFF8B7CFF), Color(0xFF31D7E5), Color(0xFFFF6B9D), Color(0xFFFFB74D)).forEach { color ->
                Box(Modifier.size(42.dp).clip(CircleShape).background(color).clickable { onAccent(color) })
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
