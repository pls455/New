package com.nova.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NovaApp() }
    }
}

@Composable
private fun NovaApp() {
    val colors = darkColorScheme(
        primary = Color(0xFF8B7CFF),
        secondary = Color(0xFF5CE1E6),
        background = Color(0xFF08090D),
        surface = Color(0xFF11131A)
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            HomeScreen()
        }
    }
}

@Composable
private fun HomeScreen() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111326), Color(0xFF08090D), Color(0xFF050507))
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("NOVA", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Your music. Your space.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(270.dp)
                    .alpha(glow)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF7C6CFF).copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .align(Alignment.Center)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF262A4B), Color(0xFF11131A))),
                            RoundedCornerShape(34.dp)
                        )
                )
                Text(
                    "N",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(Modifier.height(22.dp))
            Text("No music playing", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Your library will appear here", color = Color.White.copy(alpha = 0.5f))

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = {}) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.padding(14.dp).size(34.dp))
                    }
                }
                IconButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(32.dp)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library")
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favorites")
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}
