package com.justgba.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentsDataStore by preferencesDataStore(name = "justgba_recents")

class RecentRomsManager(private val context: Context) {
    companion object {
        private const val KEY_RECENT_ROMS = "recent_roms"
        private const val MAX_ENTRIES = 20

        private val RECENT_ROMS_KEY = stringPreferencesKey(KEY_RECENT_ROMS)
    }

    val recentRoms: Flow<List<RecentRomEntry>> = context.recentsDataStore.data.map { prefs ->
        val json = prefs[RECENT_ROMS_KEY] ?: return@map emptyList()
        deserialize(json)
    }

    suspend fun addOrUpdateEntry(entry: RecentRomEntry) {
        context.recentsDataStore.edit { prefs ->
            val json = prefs[RECENT_ROMS_KEY] ?: "[]"
            val list = deserialize(json).toMutableList()

            val existing = list.indexOfFirst { it.uri == entry.uri }
            if (existing >= 0) {
                list.removeAt(existing)
            }
            list.add(0, entry)

            val trimmed = list.take(MAX_ENTRIES)
            prefs[RECENT_ROMS_KEY] = serialize(trimmed)
        }
    }

    suspend fun removeEntry(uri: String) {
        context.recentsDataStore.edit { prefs ->
            val json = prefs[RECENT_ROMS_KEY] ?: return@edit
            val list = deserialize(json).toMutableList()
            list.removeAll { it.uri == uri }
            prefs[RECENT_ROMS_KEY] = serialize(list)
        }
    }

    suspend fun clearAll() {
        context.recentsDataStore.edit { prefs ->
            prefs.remove(RECENT_ROMS_KEY)
        }
    }

    private fun serialize(entries: List<RecentRomEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().apply {
                put("name", e.displayName)
                put("uri", e.uri)
                put("lastPlayed", e.lastPlayed)
                put("cachePath", e.cachePath)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<RecentRomEntry> {
        val arr = JSONArray(json)
        val list = mutableListOf<RecentRomEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                RecentRomEntry(
                    displayName = obj.getString("name"),
                    uri = obj.getString("uri"),
                    lastPlayed = obj.getLong("lastPlayed"),
                    cachePath = obj.optString("cachePath", ""),
                )
            )
        }
        return list
    }
}
