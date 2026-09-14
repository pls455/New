package com.nova.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("nova_playlists", Context.MODE_PRIVATE)

    data class Playlist(val name: String, val trackIds: List<Long>)

    fun load(): List<Playlist> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ids = item.optJSONArray("ids") ?: JSONArray()
                add(Playlist(item.optString("name", "Playlist"), buildList {
                    for (j in 0 until ids.length()) add(ids.optLong(j))
                }))
            }
        }
    }

    fun save(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val item = JSONObject().put("name", playlist.name)
            val ids = JSONArray()
            playlist.trackIds.forEach(ids::put)
            item.put("ids", ids)
            array.put(item)
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun addPlaylist(name: String): List<Playlist> {
        val updated = load() + Playlist(name.trim(), emptyList())
        save(updated)
        return updated
    }

    fun addTrack(name: String, trackId: Long): List<Playlist> {
        val updated = load().map { if (it.name == name && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it }
        save(updated)
        return updated
    }
}
