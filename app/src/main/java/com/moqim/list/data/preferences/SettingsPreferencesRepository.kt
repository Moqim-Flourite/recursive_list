package com.moqim.list.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings_preferences")

class SettingsPreferencesRepository(
    private val context: Context,
) {
    val settingsFlow: Flow<SettingsPreferences> = context.dataStore.data.map { preferences ->
        SettingsPreferences(
            morningViewEnabled = preferences[MORNING_VIEW_ENABLED] ?: true,
            defaultHomeTab = preferences[DEFAULT_HOME_TAB] ?: "today",
            widgetDefaultStyle = preferences[WIDGET_DEFAULT_STYLE] ?: "standard",
            liveWallpaperSource = preferences[LIVE_WALLPAPER_SOURCE] ?: "today",
            wallpaperFontScale = preferences[WALLPAPER_FONT_SCALE] ?: 18f,
            morningViewDismissedDate = preferences[MORNING_VIEW_DISMISSED_DATE],
            morningStartTime = preferences[MORNING_START_TIME] ?: "05:00",
            morningTime = preferences[MORNING_TIME] ?: "08:00",
            noonTime = preferences[NOON_TIME] ?: "11:00",
            afternoonTime = preferences[AFTERNOON_TIME] ?: "14:00",
            eveningTime = preferences[EVENING_TIME] ?: "18:00",
            appTheme = preferences[APP_THEME] ?: "system",
            batteryTipDismissed = preferences[BATTERY_TIP_DISMISSED] ?: false,
        )
    }

    suspend fun setMorningViewEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MORNING_VIEW_ENABLED] = enabled
        }
    }

    suspend fun dismissMorningViewForToday() {
        context.dataStore.edit { preferences ->
            preferences[MORNING_VIEW_DISMISSED_DATE] = LocalDate.now().toString()
        }
    }

    suspend fun clearMorningViewDismissedDate() {
        context.dataStore.edit { preferences ->
            preferences.remove(MORNING_VIEW_DISMISSED_DATE)
        }
    }

    suspend fun setDefaultHomeTab(value: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_HOME_TAB] = value
        }
    }

    suspend fun setWidgetDefaultStyle(value: String) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_DEFAULT_STYLE] = value
        }
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    suspend fun setLiveWallpaperSource(value: String) {
        context.dataStore.edit { preferences ->
            preferences[LIVE_WALLPAPER_SOURCE] = value
        }
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    suspend fun setWallpaperFontScale(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[WALLPAPER_FONT_SCALE] = value
        }
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    suspend fun setSegmentBoundaryTimes(
        morningStartTime: String,
        morningTime: String,
        noonTime: String,
        afternoonTime: String,
        eveningTime: String,
    ) {
        context.dataStore.edit { preferences ->
            preferences[MORNING_START_TIME] = morningStartTime
            preferences[MORNING_TIME] = morningTime
            preferences[NOON_TIME] = noonTime
            preferences[AFTERNOON_TIME] = afternoonTime
            preferences[EVENING_TIME] = eveningTime
        }
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    suspend fun setAppTheme(value: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = value
        }
    }

    suspend fun setBatteryTipDismissed(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BATTERY_TIP_DISMISSED] = value
        }
    }

    suspend fun getCurrentSettings(): SettingsPreferences = settingsFlow.first()

    suspend fun getTaskReminderSentState(): TaskReminderSentState {
        val preferences = context.dataStore.data.first()
        val today = LocalDate.now().toString()
        val date = preferences[TASK_REMINDER_SENT_DATE] ?: today
        val sentKeys = preferences[TASK_REMINDER_SENT_KEYS] ?: emptySet()
        return if (date == today) {
            TaskReminderSentState(date = date, sentKeys = sentKeys)
        } else {
            TaskReminderSentState(date = today, sentKeys = emptySet())
        }
    }

    suspend fun markTaskReminderSent(key: String) {
        val today = LocalDate.now().toString()
        context.dataStore.edit { preferences ->
            val storedDate = preferences[TASK_REMINDER_SENT_DATE]
            val existing = if (storedDate == today) {
                preferences[TASK_REMINDER_SENT_KEYS] ?: emptySet()
            } else {
                emptySet()
            }
            preferences[TASK_REMINDER_SENT_DATE] = today
            preferences[TASK_REMINDER_SENT_KEYS] = existing + key
        }
    }

    suspend fun clearExpiredTaskReminderSentState() {
        val today = LocalDate.now().toString()
        context.dataStore.edit { preferences ->
            if (preferences[TASK_REMINDER_SENT_DATE] != today) {
                preferences[TASK_REMINDER_SENT_DATE] = today
                preferences[TASK_REMINDER_SENT_KEYS] = emptySet()
            }
        }
    }

    suspend fun restoreSettings(settings: SettingsPreferences) {
        context.dataStore.edit { preferences ->
            preferences[MORNING_VIEW_ENABLED] = settings.morningViewEnabled
            settings.morningViewDismissedDate?.let {
                preferences[MORNING_VIEW_DISMISSED_DATE] = it
            } ?: preferences.remove(MORNING_VIEW_DISMISSED_DATE)
            preferences[DEFAULT_HOME_TAB] = settings.defaultHomeTab
            preferences[WIDGET_DEFAULT_STYLE] = settings.widgetDefaultStyle
            preferences[LIVE_WALLPAPER_SOURCE] = settings.liveWallpaperSource
            preferences[WALLPAPER_FONT_SCALE] = settings.wallpaperFontScale
            preferences[MORNING_START_TIME] = settings.morningStartTime
            preferences[MORNING_TIME] = settings.morningTime
            preferences[NOON_TIME] = settings.noonTime
            preferences[AFTERNOON_TIME] = settings.afternoonTime
            preferences[EVENING_TIME] = settings.eveningTime
            preferences[APP_THEME] = settings.appTheme
            preferences[BATTERY_TIP_DISMISSED] = settings.batteryTipDismissed
        }
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    companion object {
        private val MORNING_VIEW_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("morning_view_enabled")
        private val MORNING_VIEW_DISMISSED_DATE: Preferences.Key<String> =
            stringPreferencesKey("morning_view_dismissed_date")
        private val DEFAULT_HOME_TAB: Preferences.Key<String> =
            stringPreferencesKey("default_home_tab")
        private val WIDGET_DEFAULT_STYLE: Preferences.Key<String> =
            stringPreferencesKey("widget_default_style")
        private val LIVE_WALLPAPER_SOURCE: Preferences.Key<String> =
            stringPreferencesKey("live_wallpaper_source")
        private val WALLPAPER_FONT_SCALE: Preferences.Key<Float> =
            floatPreferencesKey("wallpaper_font_scale")
        private val MORNING_START_TIME: Preferences.Key<String> =
            stringPreferencesKey("morning_start_time")
        private val MORNING_TIME: Preferences.Key<String> =
            stringPreferencesKey("morning_time")
        private val NOON_TIME: Preferences.Key<String> =
            stringPreferencesKey("noon_time")
        private val AFTERNOON_TIME: Preferences.Key<String> =
            stringPreferencesKey("afternoon_time")
        private val EVENING_TIME: Preferences.Key<String> =
            stringPreferencesKey("evening_time")
        private val APP_THEME: Preferences.Key<String> =
            stringPreferencesKey("app_theme")
        private val BATTERY_TIP_DISMISSED: Preferences.Key<Boolean> =
            booleanPreferencesKey("battery_tip_dismissed")
        private val TASK_REMINDER_SENT_DATE: Preferences.Key<String> =
            stringPreferencesKey("task_reminder_sent_date")
        private val TASK_REMINDER_SENT_KEYS: Preferences.Key<Set<String>> =
            stringSetPreferencesKey("task_reminder_sent_keys")
    }
}

data class SettingsPreferences(
    val morningViewEnabled: Boolean = true,
    val defaultHomeTab: String = "today",
    val widgetDefaultStyle: String = "standard",
    val liveWallpaperSource: String = "today",
    val wallpaperFontScale: Float = 18f,
    val morningViewDismissedDate: String? = null,
    val morningStartTime: String = "05:00",
    val morningTime: String = "08:00",
    val noonTime: String = "11:00",
    val afternoonTime: String = "14:00",
    val eveningTime: String = "18:00",
    val appTheme: String = "system",
    val batteryTipDismissed: Boolean = false,
)

data class TaskReminderSentState(
    val date: String,
    val sentKeys: Set<String>,
)
