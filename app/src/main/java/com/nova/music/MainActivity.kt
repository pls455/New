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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

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
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

@Composable
private fun NovaApp(libraryViewModel: MusicLibraryViewModel = viewModel()) {
    var accent by remember { mutableStateOf(Color(0xFF8B7CFF)) }
    var showSettings by remember { mutableStateOf(false) }
    val colors = darkColorScheme(
        primary = accent,
        secondary = Color(0xFF5CE1E6),
        background = Color(0xFF07080C),
        surface = Color(0xFF11131A)
    )

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    accent = accent,
                    onAccentChange = { accent = it },
                    onRefresh = libraryViewModel::refresh
                )
            } else {
                LibraryHome(libraryViewModel, onSettings = { showSettings = true })
            }
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
    var nowPlayingOpen by remember { mutableStateOf(false) }
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

            override fun onModeChanged(shuffle: Boolean, repeat: Int) {
                shuffleEnabled = shuffle
                repeatMode = repeat
            }

            override fun onPlayerError(message: String) {
                playerError = message
            }
        }
        player.addListener(listener)
        viewModel.refresh()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(selectedIndex, isPlaying) {
        while (selectedIndex != null && isPlaying) {
            delay(250)
            player.refreshState()
        }
    }

    val filteredTracks = remember(tracks, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) tracks else tracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }
    val selected = selectedIndex?.let { tracks.getOrNull(it) }

    Box(Modifier.fillMaxSize()) {
        if (!nowPlayingOpen) {
            LibraryContent(
                filteredTracks = filteredTracks,
                tracks = tracks,
                loading = loading,
                searchOpen = searchOpen,
                query = query,
                playerError = playerError,
                onSearchOpen = { searchOpen = true },
                onSearchClose = { query = ""; searchOpen = false },
                onQueryChange = { query = it },
                onSettings = onSettings,
                onRefresh = viewModel::refresh,
                onTrackClick = { index ->
                    if (index >= 0) {
                        selectedIndex = index
                        nowPlayingOpen = true
                        player.playTrack(tracks.map { it.uri }, index)
                    }
                }
            )

            AnimatedVisibility(
                visible = selected != null,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = .96f, animationSpec = tween(220)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = .96f, animationSpec = tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(5f)
            ) {
                selected?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = isPlaying,
                        onOpen = { nowPlayingOpen = true },
                        onPlayPause = { player.togglePlayPause() }
                    )
                }
            }
        } else if (selected != null) {
            NowPlayingScreen(
                track = selected,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0 } ?: selected.durationMs,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onBack = { nowPlayingOpen = false },
                onPlayPause = { player.togglePlayPause() },
                onSeek = { player.seekTo(it.toLong()) },
                onPrevious = { player.previous() },
                onNext = { player.next() },
                onShuffle = { player.setShuffle(!shuffleEnabled) },
                onRepeat = {
                    player.setRepeatMode(when (repeatMode) { 0 -> 1; 1 -> 2; else -> 0 })
                }
            )
        }
    }
}

@Composable
private fun LibraryContent(
    filteredTracks: List<Track>,
    tracks: List<Track>,
    loading: Boolean,
    searchOpen: Boolean,
    query: String,
    playerError: String?,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(Color(0xFF07080C))
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            if (searchOpen) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("ابحث عن أغنية أو فنان") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    IconButton(onClick = onSearchClose) { Icon(Icons.Rounded.Close, "Close") }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("NOVA", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("YOUR MUSIC. YOUR SPACE.", fontSize = 9.sp, color = Color.White.copy(.42f), letterSpacing = 2.sp)
                }
                IconButton(onClick = onSearchOpen) { Icon(Icons.Rounded.Search, "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }

        Spacer(Modifier.height(22.dp))
        when {
            loading && tracks.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
            tracks.isEmpty() -> EmptyLibrary(onRefresh)
            else -> {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                    Column {
                        Text("Your library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${filteredTracks.size} tracks", color = Color.White.copy(.42f), fontSize = 12.sp)
                    }
                    if (query.isNotBlank()) Text("SEARCH", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    Modifier.weight(1f).padding(bottom = 82.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val originalIndex = tracks.indexOfFirst { it.id == track.id }
                        TrackRow(track) { onTrackClick(originalIndex) }
                    }
                }
            }
        }
        playerError?.let {
            Text("تعذر تشغيل الملف: $it", color = Color(0xFFFF8A80), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "miniPlayer")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000), RepeatMode.Restart),
        label = "miniRotation"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen),
        color = Color(0xFF151821).copy(.96f),
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8B7CFF), Color(0xFF31D7E5)))), Alignment.Center) {
                Icon(Icons.Rounded.Album, null, tint = Color.White.copy(.9f), modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.48f), fontSize = 11.sp)
            }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause") }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.055f)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF332F62), Color(0xFF151722)))), Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFB8AEFF))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.45f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(.58f))
    }
}

@Composable
private fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "novaColor")
    val motion by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "gradientMotion")
    val base = Color(0xFF7E6CFF)
    val cyan = Color(0xFF31D7E5)
    val pink = Color(0xFFE55BFF)
    val movingA = lerp(base, cyan, motion)
    val movingB = lerp(pink, base, motion)
    val safeDuration = durationMs.coerceAtLeast(1L)
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(movingA.copy(.24f), movingB.copy(.10f), Color(0xFF05060A)), radius = 900f))) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .graphicsLayer { translationY = dragOffset.value; alpha = 1f - (dragOffset.value / 700f).coerceIn(0f, .18f) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                change.consume()
                                scope.launch { dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f)) }
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (dragOffset.value > 140f) {
                                    onBack()
                                    dragOffset.snapTo(0f)
                                } else dragOffset.animateTo(0f, tween(220))
                            }
                        },
                        onDragCancel = { scope.launch { dragOffset.animateTo(0f, tween(180)) } }
                    )
                }
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("NOVA", fontSize = 9.sp, color = movingA.copy(.75f), letterSpacing = 2.sp)
                }
                IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffleEnabled) movingA else Color.White.copy(.7f)) }
            }

            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(286.dp).clip(RoundedCornerShape(38.dp)).background(Brush.linearGradient(listOf(movingA, movingB, Color(0xFF11131C)))), Alignment.Center) {
                Box(Modifier.size(244.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFF0B0C12).copy(.78f)), Alignment.Center) {
                    Icon(Icons.Rounded.Album, null, Modifier.size(92.dp), tint = movingA.copy(.9f))
                    if (isPlaying) Visualizer(movingA, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
                }
            }

            Spacer(Modifier.height(25.dp))
            Text(track.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color.White.copy(.50f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Slider(value = positionMs.coerceIn(0L, safeDuration).toFloat(), onValueChange = onSeek, valueRange = 0f..safeDuration.toFloat(), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatDuration(positionMs), fontSize = 10.sp, color = Color.White.copy(.42f))
                Text(formatDuration(durationMs), fontSize = 10.sp, color = Color.White.copy(.42f))
            }

            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                IconButton(onClick = onRepeat) { Icon(Icons.Rounded.Repeat, "Repeat", tint = if (repeatMode != 0) movingA else Color.White.copy(.75f)) }
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(40.dp)) }
                Surface(Modifier.size(76.dp), CircleShape, color = movingA) {
                    IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", Modifier.size(40.dp), tint = Color.Black.copy(.85f)) }
                }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(40.dp)) }
                IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffleEnabled) movingA else Color.White.copy(.75f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Visualizer(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val phase by transition.animateFloat(0f, (PI * 2).toFloat(), infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "bars")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(9) { index ->
            val wave = ((sin(phase + index * .72f) + 1f) / 2f)
            Box(Modifier.size(width = 4.dp, height = (6f + wave * 18f).dp).clip(RoundedCornerShape(4.dp)).background(color.copy(.82f)))
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, accent: Color, onAccentChange: (Color) -> Unit, onRefresh: () -> Unit) {
    var showArtist by remember { mutableStateOf(true) }
    var compactRows by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).background(Color(0xFF08090D)).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("الإعدادات", fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
        Text("المظهر", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text("Theme Studio", fontWeight = FontWeight.Bold)
            Text("اختيار لون NOVA الأساسي", color = Color.White.copy(.5f), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Color(0xFF8B7CFF), Color(0xFF00B8D4), Color(0xFFFF4D8D), Color(0xFFFFB300)).forEach { color ->
                    Box(Modifier.size(38.dp).clip(CircleShape).background(color).clickable { onAccentChange(color) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("اللون الحالي", color = accent, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("المكتبة", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingSwitch("إظهار اسم الفنان", showArtist) { showArtist = it }
            SettingSwitch("قائمة مضغوطة", compactRows) { compactRows = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("تحديث مكتبة الأغاني") }
        }
        Spacer(Modifier.height(14.dp))
        Text("حول NOVA", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text("NOVA Music", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("مشغل موسيقى محلي يعمل بدون إنترنت أو خدمات سحابية.", color = Color.White.copy(.55f), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("صنع بواسطة كرم - أبو إبراهيم", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text("© 2026 NOVA", color = Color.White.copy(.35f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.055f)).padding(16.dp), content = content)
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text("لا توجد موسيقى", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("ضع ملفات الصوت على الجهاز ثم حدّث المكتبة.", color = Color.White.copy(.5f), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) { Text("تحديث المكتبة") }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
