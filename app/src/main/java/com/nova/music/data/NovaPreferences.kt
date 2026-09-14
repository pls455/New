package com.nova.music.data

import android.content.Context
import androidx.core.content.edit

class NovaPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("nova_preferences", Context.MODE_PRIVATE)

    var themeSeed: Int
        get() = prefs.getInt("theme_seed", 0xFF8B7CFF.toInt())
        set(value) = prefs.edit { putInt("theme_seed", value) }

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit { putBoolean("dark_mode", value) }

    var showArtwork: Boolean
        get() = prefs.getBoolean("show_artwork", true)
        set(value) = prefs.edit { putBoolean("show_artwork", value) }

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless", true)
        set(value) = prefs.edit { putBoolean("gapless", value) }
}
