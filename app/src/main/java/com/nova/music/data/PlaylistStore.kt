package com.nova.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("nova_playlists", Context.MODE_PRIVATE)

    data class Playlist(val name: String, val trackIds: List<Long>)

    fun load(): List<Playlist> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ids = item.optJSONArray("ids") ?: JSONArray()
                add(Playlist(item.optString("name", "Playlist"), buildList {
                    for (j in 0 until ids.length()) add(ids.optLong(j))
                }))
            }
        }
    }.getOrDefault(emptyList())

    fun save(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.filter { it.name.isNotBlank() }.forEach { playlist ->
            val ids = JSONArray()
            playlist.trackIds.distinct().forEach(ids::put)
            array.put(JSONObject().put("name", playlist.name.trim()).put("ids", ids))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun addPlaylist(name: String): List<Playlist> {
        val clean = name.trim()
        if (clean.isBlank() || load().any { it.name.equals(clean, true) }) return load()
        return load().plus(Playlist(clean, emptyList())).also(::save)
    }

    fun addTrack(name: String, trackId: Long): List<Playlist> = load().map {
        if (it.name == name && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
    }.also(::save)

    fun removeTrack(name: String, trackId: Long): List<Playlist> = load().map {
        if (it.name == name) it.copy(trackIds = it.trackIds - trackId) else it
    }.also(::save)

    fun deletePlaylist(name: String): List<Playlist> = load().filterNot { it.name == name }.also(::save)
}
