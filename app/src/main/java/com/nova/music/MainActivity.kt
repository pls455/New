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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.music.data.MusicLibraryViewModel
import com.nova.music.data.Track
import com.nova.music.player.PlayerController

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) setContent { NovaApp() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMusicPermissionIfNeeded()
        setContent { NovaApp() }
    }

    private fun requestMusicPermissionIfNeeded() {
        val musicPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, musicPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(musicPermission))
        }
    }
}

@Composable
private fun NovaApp(libraryViewModel: MusicLibraryViewModel = viewModel()) {
    val colors = darkColorScheme(
        primary = Color(0xFF8B7CFF),
        secondary = Color(0xFF5CE1E6),
        background = Color(0xFF08090D),
        surface = Color(0xFF11131A)
    )
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            LibraryHome(libraryViewModel)
        }
    }
}

@Composable
private fun LibraryHome(viewModel: MusicLibraryViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val player = remember { PlayerController(androidx.compose.ui.platform.LocalContext.current) }
    var selected by remember { mutableStateOf<Track?>(null) }

    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose { player.release() }
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF14152A), Color(0xFF08090D), Color(0xFF050507)))
        ).padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("NOVA", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("YOUR MUSIC. YOUR SPACE.", fontSize = 10.sp, color = Color.White.copy(.48f))
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.Search, "Search") }
        }
        Spacer(Modifier.height(20.dp))

        if (loading && tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
        } else if (tracks.isEmpty()) {
            EmptyLibrary()
        } else {
            Text("Your library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${tracks.size} tracks", color = Color.White.copy(.48f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tracks) { index, track ->
                    TrackRow(track) {
                        selected = track
                        player.playTrack(tracks.map { it.uri }, index)
                    }
                }
            }
        }

        selected?.let { MiniPlayer(it) }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.055f))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF25283E)), Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF9B8CFF))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.48f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(.65f))
    }
}

@Composable
private fun MiniPlayer(track: Track) {
    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(22.dp), color = Color(0xFF191B27)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF2B2E4A)), Alignment.Center) {
                Icon(Icons.Rounded.Album, null, tint = Color(0xFF8B7CFF))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(.48f), fontSize = 11.sp)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipPrevious, null) }
            IconButton(onClick = {}) {
                Surface(Modifier.size(42.dp), CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(9.dp))
                }
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, null) }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF191B2A)), Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(42.dp), tint = Color(0xFF8B7CFF))
        }
        Spacer(Modifier.height(18.dp))
        Text("No music found", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Add audio files to your device and refresh.", color = Color.White.copy(.5f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = {}) { Text("Refresh library") }
    }
}
