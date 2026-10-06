package com.savr.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(val theme: String = "system", val quality: String = "HD", val wifiOnly: Boolean = false, val autoCopy: Boolean = false,
                    val onboarded: Boolean = false, val disclaimerOk: Boolean = false, val language: String = "English",
                    val statusTrees: Set<String> = emptySet())

private val Context.ds by preferencesDataStore("settings")

class SettingsStore(private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme"); val quality = stringPreferencesKey("quality"); val wifi = booleanPreferencesKey("wifi")
        val auto = booleanPreferencesKey("auto"); val onb = booleanPreferencesKey("onb"); val disc = booleanPreferencesKey("disc")
        val lang = stringPreferencesKey("lang"); val trees = stringSetPreferencesKey("trees")
    }
    val flow: Flow<Settings> = ctx.ds.data.map { Settings(it[K.theme] ?: "system", it[K.quality] ?: "HD", it[K.wifi] ?: false, it[K.auto] ?: false,
        it[K.onb] ?: false, it[K.disc] ?: false, it[K.lang] ?: "English", it[K.trees] ?: emptySet()) }
    suspend fun update(f: (Settings) -> Settings) { ctx.ds.edit { p ->
        val n = f(Settings(p[K.theme] ?: "system", p[K.quality] ?: "HD", p[K.wifi] ?: false, p[K.auto] ?: false, p[K.onb] ?: false,
            p[K.disc] ?: false, p[K.lang] ?: "English", p[K.trees] ?: emptySet()))
        p[K.theme] = n.theme; p[K.quality] = n.quality; p[K.wifi] = n.wifiOnly; p[K.auto] = n.autoCopy; p[K.onb] = n.onboarded
        p[K.disc] = n.disclaimerOk; p[K.lang] = n.language; p[K.trees] = n.statusTrees } }
}
