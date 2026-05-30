package com.moqim.list.core.time

import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.preferences.SettingsPreferences
import java.time.LocalTime

object TimeSegmentResolver {
    fun resolveNow(settings: SettingsPreferences, now: LocalTime = LocalTime.now()): TimeSegment {
        val morningStart = settings.morningStartTime.toLocalTimeOrDefault(LocalTime.of(5, 0))
        val morning = settings.morningTime.toLocalTimeOrDefault(LocalTime.of(8, 0))
        val noon = settings.noonTime.toLocalTimeOrDefault(LocalTime.of(11, 0))
        val afternoon = settings.afternoonTime.toLocalTimeOrDefault(LocalTime.of(14, 0))
        val evening = settings.eveningTime.toLocalTimeOrDefault(LocalTime.of(18, 0))

        return when {
            now >= morningStart && now < morning -> TimeSegment.MORNING_START
            now >= morning && now < noon -> TimeSegment.MORNING
            now >= noon && now < afternoon -> TimeSegment.NOON
            now >= afternoon && now < evening -> TimeSegment.AFTERNOON
            else -> TimeSegment.EVENING
        }
    }

    private fun String.toLocalTimeOrDefault(default: LocalTime): LocalTime {
        return runCatching { LocalTime.parse(this) }.getOrElse { default }
    }
}