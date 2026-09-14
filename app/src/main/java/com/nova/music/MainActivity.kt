package com.nova.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.music.data.MusicLibraryViewModel
import com.nova.music.data.Track
import com.nova.music.player.PlayerController
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { setContent { NovaApp() } }

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
    var showSettings by remember { mutableStateOf(false) }
    val colors = darkColorScheme(primary = accent, secondary = Color(0xFF5CE1E6), background = Color(0xFF08090D), surface = Color(0xFF11131A))
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            if (showSettings) SettingsScreen({ showSettings = false }, accent, { accent = it }, libraryViewModel::refresh)
            else LibraryHome(libraryViewModel) { showSettings = true }
        }
    }
}

@Composable
private fun LibraryHome(viewModel: MusicLibraryViewModel, onSettings: () -> Unit) {
    val tracks by viewModel.tracks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = LocalContext.current
    val player = remember(context) { PlayerController(context) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var playerError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : PlayerController.Listener {
            override fun onPlaybackStateChanged(isPlayingValue: Boolean, position: Long, duration: Long, currentIndex: Int) {
                isPlaying = isPlayingValue
                positionMs = position
                if (duration > 0) durationMs = duration
                if (currentIndex >= 0) selectedIndex = currentIndex
            }
            override fun onModeChanged(shuffle: Boolean, repeat: Int) { shuffleEnabled = shuffle; repeatMode = repeat }
            override fun onPlayerError(message: String) { playerError = message }
        }
        player.addListener(listener)
        viewModel.refresh()
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(selectedIndex, isPlaying) {
        while (selectedIndex != null && isPlaying) {
            delay(250)
            player.refreshState()
        }
    }

    val filteredTracks = remember(tracks, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) tracks else tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
    }
    val selected = selectedIndex?.let { tracks.getOrNull(it) }
    if (selected != null) {
        NowPlayingScreen(selected, isPlaying, positionMs, durationMs.takeIf { it > 0 } ?: selected.durationMs, shuffleEnabled, repeatMode,
            { selectedIndex = null }, { player.togglePlayPause() }, { player.seekTo(it.toLong()) }, { player.previous() }, { player.next() },
            { player.setShuffle(!shuffleEnabled) }, { player.setRepeatMode(if (repeatMode == 0) 1 else if (repeatMode == 1) 2 else 0) })
        return
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14152A), Color(0xFF08090D), Color(0xFF050507)))).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            if (searchOpen) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), placeholder = { Text("ابحث عن أغنية أو فنان") }, singleLine = true)
                    IconButton(onClick = { query = ""; searchOpen = false }) { Icon(Icons.Rounded.Close, "Close") }
                }
            } else {
                Column(Modifier.weight(1f)) { Text("NOVA", fontSize = 30.sp, fontWeight = FontWeight.Black); Text("YOUR MUSIC. YOUR SPACE.", fontSize = 10.sp, color = Color.White.copy(.48f)) }
                IconButton(onClick = { searchOpen = true }) { Icon(Icons.Rounded.Search, "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (loading && tracks.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
        else if (tracks.isEmpty()) EmptyLibrary { viewModel.refresh() }
        else {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                Column { Text("Your library", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("${filteredTracks.size} tracks", color = Color.White.copy(.48f), fontSize = 13.sp) }
                if (query.isNotBlank()) Text("Search results", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredTracks, key = { it.id }) { track ->
                    val originalIndex = tracks.indexOfFirst { it.id == track.id }
                    TrackRow(track) { selectedIndex = originalIndex; player.playTrack(tracks.map { it.uri }, originalIndex) }
                }
            }
        }
        playerError?.let { Text("تعذر تشغيل الملف: $it", color = Color(0xFFFF8A80), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.055f)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF25283E)), Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF9B8CFF)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.48f), fontSize = 12.sp) }
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(.65f))
    }
}

@Composable
private fun NowPlayingScreen(track: Track, isPlaying: Boolean, positionMs: Long, durationMs: Long, shuffleEnabled: Boolean, repeatMode: Int, onBack: () -> Unit, onPlayPause: () -> Unit, onSeek: (Float) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF25214A), Color(0xFF0B0B12), Color(0xFF050507)))).padding(horizontal = 22.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }; Text("NOW PLAYING", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold); IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffleEnabled) MaterialTheme.colorScheme.secondary else Color.White) } }
        Spacer(Modifier.height(38.dp))
        Box(Modifier.size(290.dp).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Color(0xFF433D78), Color(0xFF161825)))), Alignment.Center) { Icon(Icons.Rounded.Album, null, Modifier.size(100.dp), tint = Color.White.copy(.82f)) }
        Spacer(Modifier.height(30.dp)); Text(track.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color.White.copy(.52f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(26.dp)); Slider(value = positionMs.coerceIn(0L, safeDuration).toFloat(), onValueChange = onSeek, valueRange = 0f..safeDuration.toFloat(), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(formatDuration(positionMs), fontSize = 11.sp, color = Color.White.copy(.45f)); Text(formatDuration(durationMs), fontSize = 11.sp, color = Color.White.copy(.45f)) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = onRepeat) { Icon(Icons.Rounded.Repeat, "Repeat", tint = if (repeatMode != 0) MaterialTheme.colorScheme.secondary else Color.White) }
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(38.dp)) }
            Surface(Modifier.size(72.dp), CircleShape, color = MaterialTheme.colorScheme.primary) { IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", Modifier.size(38.dp)) } }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(38.dp)) }
            IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffleEnabled) MaterialTheme.colorScheme.secondary else Color.White) }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, accent: Color, onAccentChange: (Color) -> Unit, onRefresh: () -> Unit) {
    var showArtist by remember { mutableStateOf(true) }
    var compactRows by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14152A), Color(0xFF08090D)))).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }; Text("الإعدادات", fontSize = 25.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(22.dp))
        Text("المظهر", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text("Theme Studio", fontWeight = FontWeight.Bold); Text("اختيار لون NOVA الأساسي", color = Color.White.copy(.5f), fontSize = 12.sp); Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(Color(0xFF8B7CFF), Color(0xFF00B8D4), Color(0xFFFF4D8D), Color(0xFFFFB300)).forEach { color -> Box(Modifier.size(38.dp).clip(CircleShape).background(color).clickable { onAccentChange(color) }) } }
            Spacer(Modifier.height(8.dp)); Text("اللون الحالي", color = accent, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp)); Text("المكتبة", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SettingsCard { SettingSwitch("إظهار اسم الفنان", showArtist) { showArtist = it }; SettingSwitch("قائمة مضغوطة", compactRows) { compactRows = it }; Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("تحديث مكتبة الأغاني") } }
        Spacer(Modifier.height(14.dp)); Text("حول NOVA", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SettingsCard { Text("NOVA Music", fontSize = 20.sp, fontWeight = FontWeight.Black); Text("مشغل موسيقى محلي يعمل بدون إنترنت أو خدمات سحابية.", color = Color.White.copy(.55f), fontSize = 12.sp); Spacer(Modifier.height(10.dp)); Text("صنع بواسطة كرم - أبو إبراهيم", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold); Text("© 2026 NOVA", color = Color.White.copy(.35f), fontSize = 11.sp) }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.055f)).padding(16.dp), content = content) }

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(title); Switch(checked = checked, onCheckedChange = onCheckedChange) } }

private fun formatDuration(ms: Long): String { val totalSeconds = (ms / 1000).coerceAtLeast(0); return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}" }

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF191B2A)), Alignment.Center) { Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(42.dp), tint = Color(0xFF8B7CFF)) }
        Spacer(Modifier.height(18.dp)); Text("No music found", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Add audio files to your device and refresh.", color = Color.White.copy(.5f), fontSize = 13.sp); Spacer(Modifier.height(14.dp)); Button(onClick = onRefresh) { Text("Refresh library") }
    }
}