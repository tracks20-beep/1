package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AspectRatioMode
import com.example.data.model.RepeatModeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "veloplay_settings"
)

data class PlayerPreferencesState(
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val shuffleEnabled: Boolean = false,
    val backgroundVideoAudio: Boolean = true,
    val autoPipOnLeave: Boolean = true,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val popupWidthDp: Float = 260f,
    val darkThemeMode: Boolean = true,
    val dynamicColorEnabled: Boolean = false,
    val resumePlaybackPromptless: Boolean = true
)

class PlayerPreferences(private val context: Context) {

    private object Keys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val BACKGROUND_VIDEO_AUDIO = booleanPreferencesKey("background_video_audio")
        val AUTO_PIP_ON_LEAVE = booleanPreferencesKey("auto_pip_on_leave")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val POPUP_WIDTH_DP = floatPreferencesKey("popup_width_dp")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RESUME_PROMPTLESS = booleanPreferencesKey("resume_promptless")
    }

    val state: Flow<PlayerPreferencesState> = context.playerDataStore.data.map { prefs ->
        PlayerPreferencesState(
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            repeatMode = prefs[Keys.REPEAT_MODE]?.let {
                runCatching { RepeatModeOption.valueOf(it) }.getOrNull()
            } ?: RepeatModeOption.OFF,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            backgroundVideoAudio = prefs[Keys.BACKGROUND_VIDEO_AUDIO] ?: true,
            autoPipOnLeave = prefs[Keys.AUTO_PIP_ON_LEAVE] ?: true,
            defaultAspectRatio = prefs[Keys.DEFAULT_ASPECT_RATIO]?.let {
                runCatching { AspectRatioMode.valueOf(it) }.getOrNull()
            } ?: AspectRatioMode.FIT,
            popupWidthDp = (prefs[Keys.POPUP_WIDTH_DP] ?: 260f).coerceIn(150f, 420f),
            darkThemeMode = prefs[Keys.DARK_THEME] ?: true,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
            resumePlaybackPromptless = prefs[Keys.RESUME_PROMPTLESS] ?: true
        )
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.25f, 3.0f) }
    }

    suspend fun setRepeatMode(mode: RepeatModeOption) {
        context.playerDataStore.edit { it[Keys.REPEAT_MODE] = mode.name }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setBackgroundVideoAudio(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.BACKGROUND_VIDEO_AUDIO] = enabled }
    }

    suspend fun setAutoPipOnLeave(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.AUTO_PIP_ON_LEAVE] = enabled }
    }

    suspend fun setDefaultAspectRatio(mode: AspectRatioMode) {
        context.playerDataStore.edit { it[Keys.DEFAULT_ASPECT_RATIO] = mode.name }
    }

    suspend fun setPopupWidthDp(widthDp: Float) {
        context.playerDataStore.edit { it[Keys.POPUP_WIDTH_DP] = widthDp.coerceIn(150f, 420f) }
    }

    suspend fun setDarkThemeMode(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setResumePlaybackPromptless(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.RESUME_PROMPTLESS] = enabled }
    }
}
