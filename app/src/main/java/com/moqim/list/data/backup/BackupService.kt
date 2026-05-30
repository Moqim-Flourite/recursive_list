package com.moqim.list.data.backup

import android.content.Context
import com.moqim.list.data.local.entity.DailyPlanEntity
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import com.moqim.list.data.local.entity.HabitRecordEntity
import com.moqim.list.data.local.entity.HabitTemplateEntity
import com.moqim.list.data.local.entity.MonthlyPlanEntity
import com.moqim.list.data.local.entity.SurfaceConfigEntity
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import com.moqim.list.data.local.entity.WidgetInstanceConfigEntity
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.preferences.SettingsPreferences
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class BackupService(
    private val context: Context,
) {
    private val db by lazy { DatabaseProvider.get(context) }
    private val settingsRepository by lazy { SettingsPreferencesRepository(context) }

    suspend fun exportToDownloads(): File {
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsToJson(settingsRepository.getCurrentSettings()))
            put("monthlyPlans", JSONArray().apply { db.monthlyPlanDao().getAll().forEach { put(monthlyPlanToJson(it)) } })
            put("weeklyPlans", JSONArray().apply { db.weeklyPlanDao().getAll().forEach { put(weeklyPlanToJson(it)) } })
            put("dailyPlans", JSONArray().apply { db.dailyPlanDao().getAll().forEach { put(dailyPlanToJson(it)) } })
            put("executionTasks", JSONArray().apply { db.executionTaskDao().getAll().forEach { put(executionTaskToJson(it)) } })
            put("habitTemplates", JSONArray().apply { db.habitTemplateDao().getAll().forEach { put(habitTemplateToJson(it)) } })
            put("habitRecords", JSONArray().apply { db.habitRecordDao().getAll().forEach { put(habitRecordToJson(it)) } })
            put("surfaceConfigs", JSONArray().apply { db.surfaceConfigDao().getAll().forEach { put(surfaceConfigToJson(it)) } })
            put("widgetInstanceConfigs", JSONArray().apply { db.widgetInstanceConfigDao().getAll().forEach { put(widgetInstanceConfigToJson(it)) } })
        }

        val backupDir = File("/sdcard/Download/moqim_list")
        if (!backupDir.exists()) backupDir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val output = File(backupDir, "moqim_backup_$timestamp.json")
        output.writeText(root.toString(2))
        return output
    }

    suspend fun restoreLatestFromDownloads(): File? {
        val backupDir = File("/sdcard/Download/moqim_list")
        val latest = backupDir.listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith("moqim_backup_") && file.extension == "json" }
            ?.maxByOrNull { file -> file.lastModified() }
            ?: return null
        restoreFromFile(latest)
        return latest
    }

    suspend fun restoreFromFile(file: File) {
        val root = JSONObject(file.readText())
        db.clearAllTables()
        settingsRepository.restoreSettings(jsonToSettings(root.getJSONObject("settings")))

        val monthlyPlans = root.getJSONArray("monthlyPlans")
        for (index in 0 until monthlyPlans.length()) {
            db.monthlyPlanDao().upsert(jsonToMonthlyPlan(monthlyPlans.getJSONObject(index)))
        }

        val weeklyPlans = root.getJSONArray("weeklyPlans")
        for (index in 0 until weeklyPlans.length()) {
            db.weeklyPlanDao().upsert(jsonToWeeklyPlan(weeklyPlans.getJSONObject(index)))
        }

        val dailyPlans = root.getJSONArray("dailyPlans")
        for (index in 0 until dailyPlans.length()) {
            db.dailyPlanDao().upsert(jsonToDailyPlan(dailyPlans.getJSONObject(index)))
        }

        val executionTasks = root.getJSONArray("executionTasks")
        for (index in 0 until executionTasks.length()) {
            db.executionTaskDao().upsert(jsonToExecutionTask(executionTasks.getJSONObject(index)))
        }

        val habitTemplates = root.getJSONArray("habitTemplates")
        for (index in 0 until habitTemplates.length()) {
            db.habitTemplateDao().upsert(jsonToHabitTemplate(habitTemplates.getJSONObject(index)))
        }

        val habitRecords = root.getJSONArray("habitRecords")
        for (index in 0 until habitRecords.length()) {
            db.habitRecordDao().upsert(jsonToHabitRecord(habitRecords.getJSONObject(index)))
        }

        val surfaceConfigs = root.getJSONArray("surfaceConfigs")
        for (index in 0 until surfaceConfigs.length()) {
            db.surfaceConfigDao().upsert(jsonToSurfaceConfig(surfaceConfigs.getJSONObject(index)))
        }

        val widgetInstanceConfigs = root.getJSONArray("widgetInstanceConfigs")
        for (index in 0 until widgetInstanceConfigs.length()) {
            db.widgetInstanceConfigDao().upsert(jsonToWidgetInstanceConfig(widgetInstanceConfigs.getJSONObject(index)))
        }

        MorningWidgetProvider.refreshAll(context)
        CurrentSegmentWidgetProvider.refreshAll(context)
        WallpaperRefreshNotifier.notifyRefresh(context)
    }

    private fun settingsToJson(settings: SettingsPreferences) = JSONObject().apply {
        put("morningViewEnabled", settings.morningViewEnabled)
        put("defaultHomeTab", settings.defaultHomeTab)
        put("widgetDefaultStyle", settings.widgetDefaultStyle)
        put("liveWallpaperSource", settings.liveWallpaperSource)
        put("morningViewDismissedDate", settings.morningViewDismissedDate)
        put("morningStartTime", settings.morningStartTime)
        put("morningTime", settings.morningTime)
        put("noonTime", settings.noonTime)
        put("afternoonTime", settings.afternoonTime)
        put("eveningTime", settings.eveningTime)
    }

    private fun jsonToSettings(json: JSONObject) = SettingsPreferences(
        morningViewEnabled = json.optBoolean("morningViewEnabled", true),
        defaultHomeTab = json.optString("defaultHomeTab", "today"),
        widgetDefaultStyle = json.optString("widgetDefaultStyle", "standard"),
        liveWallpaperSource = json.optString("liveWallpaperSource", "today"),
        morningViewDismissedDate = json.optStringOrNull("morningViewDismissedDate"),
        morningStartTime = json.optString("morningStartTime", "05:00"),
        morningTime = json.optString("morningTime", "08:00"),
        noonTime = json.optString("noonTime", "11:00"),
        afternoonTime = json.optString("afternoonTime", "14:00"),
        eveningTime = json.optString("eveningTime", "18:00"),
    )

    private fun monthlyPlanToJson(entity: MonthlyPlanEntity) = JSONObject().apply {
        put("id", entity.id); put("title", entity.title); put("theme", entity.theme); put("goal", entity.goal)
        put("startDate", entity.startDate); put("endDate", entity.endDate); put("status", entity.status)
        put("createdAt", entity.createdAt); put("updatedAt", entity.updatedAt)
    }

    private fun weeklyPlanToJson(entity: WeeklyPlanEntity) = JSONObject().apply {
        put("id", entity.id); put("monthlyPlanId", entity.monthlyPlanId); put("title", entity.title); put("goal", entity.goal)
        put("weekStartDate", entity.weekStartDate); put("weekEndDate", entity.weekEndDate); put("capacity", entity.capacity)
        put("review", entity.review); put("status", entity.status); put("createdAt", entity.createdAt); put("updatedAt", entity.updatedAt)
    }

    private fun dailyPlanToJson(entity: DailyPlanEntity) = JSONObject().apply {
        put("id", entity.id); put("weeklyPlanId", entity.weeklyPlanId); put("date", entity.date); put("summary", entity.summary)
        put("energyLevel", entity.energyLevel); put("generatedFromPreviousDay", entity.generatedFromPreviousDay)
        put("review", entity.review); put("status", entity.status); put("createdAt", entity.createdAt); put("updatedAt", entity.updatedAt)
    }

    private fun executionTaskToJson(entity: ExecutionTaskEntity) = JSONObject().apply {
        put("id", entity.id); put("title", entity.title); put("note", entity.note); put("type", entity.type); put("status", entity.status)
        put("priority", entity.priority); put("dueAt", entity.dueAt); put("sortOrder", entity.sortOrder)
        put("monthlyPlanId", entity.monthlyPlanId); put("weeklyPlanId", entity.weeklyPlanId); put("dailyPlanId", entity.dailyPlanId)
        put("sourceType", entity.sourceType); put("sourceTaskId", entity.sourceTaskId); put("timeSegment", entity.timeSegment)
        put("isTopFocus", entity.isTopFocus); put("estimatedMinutes", entity.estimatedMinutes); put("createdAt", entity.createdAt); put("updatedAt", entity.updatedAt)
    }

    private fun habitTemplateToJson(entity: HabitTemplateEntity) = JSONObject().apply {
        put("id", entity.id); put("title", entity.title); put("note", entity.note); put("estimatedMinutes", entity.estimatedMinutes)
        put("sortOrder", entity.sortOrder); put("enabled", entity.enabled); put("frequencyType", entity.frequencyType)
        put("weekdaysMask", entity.weekdaysMask); put("preferredTimeSegment", entity.preferredTimeSegment)
        put("targetAppPackageName", entity.targetAppPackageName); put("iconUri", entity.iconUri); put("iconLabel", entity.iconLabel)
        put("dailyTargetCount", entity.dailyTargetCount); put("streakEnabled", entity.streakEnabled); put("baseCompletedDays", entity.baseCompletedDays)
        put("createdAt", entity.createdAt); put("updatedAt", entity.updatedAt)
    }

    private fun habitRecordToJson(entity: HabitRecordEntity) = JSONObject().apply {
        put("id", entity.id); put("habitTemplateId", entity.habitTemplateId); put("date", entity.date); put("status", entity.status)
        put("completedAt", entity.completedAt); put("note", entity.note); put("completedCount", entity.completedCount)
    }

    private fun surfaceConfigToJson(entity: SurfaceConfigEntity) = JSONObject().apply {
        put("id", entity.id); put("targetType", entity.targetType); put("targetId", entity.targetId); put("surfaceType", entity.surfaceType)
        put("theme", entity.theme); put("maxItems", entity.maxItems); put("showCompleted", entity.showCompleted)
        put("showProgress", entity.showProgress); put("opacity", entity.opacity); put("anchorPosition", entity.anchorPosition)
        put("textScale", entity.textScale); put("refreshPolicy", entity.refreshPolicy)
    }

    private fun widgetInstanceConfigToJson(entity: WidgetInstanceConfigEntity) = JSONObject().apply {
        put("appWidgetId", entity.appWidgetId); put("surfaceConfigId", entity.surfaceConfigId)
    }

    private fun jsonToMonthlyPlan(json: JSONObject) = MonthlyPlanEntity(
        id = json.getLong("id"),
        title = json.getString("title"),
        theme = json.optString("theme", ""),
        goal = json.optStringOrNull("goal"),
        startDate = json.getString("startDate"),
        endDate = json.getString("endDate"),
        status = json.optString("status", "ACTIVE"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun jsonToWeeklyPlan(json: JSONObject) = WeeklyPlanEntity(
        id = json.getLong("id"),
        monthlyPlanId = json.getLong("monthlyPlanId"),
        title = json.getString("title"),
        goal = json.optStringOrNull("goal"),
        weekStartDate = json.getString("weekStartDate"),
        weekEndDate = json.getString("weekEndDate"),
        capacity = json.optIntOrNull("capacity"),
        review = json.optStringOrNull("review"),
        status = json.optString("status", "ACTIVE"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun jsonToDailyPlan(json: JSONObject) = DailyPlanEntity(
        id = json.getLong("id"),
        weeklyPlanId = json.optLongOrNull("weeklyPlanId"),
        date = json.getString("date"),
        summary = json.optStringOrNull("summary"),
        energyLevel = json.optStringOrNull("energyLevel"),
        generatedFromPreviousDay = json.optBoolean("generatedFromPreviousDay", false),
        review = json.optStringOrNull("review"),
        status = json.optString("status", "ACTIVE"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun jsonToExecutionTask(json: JSONObject) = ExecutionTaskEntity(
        id = json.getLong("id"),
        title = json.getString("title"),
        note = json.optStringOrNull("note"),
        type = json.optString("type", "PLAN_EXECUTION"),
        status = json.optString("status", "TODO"),
        priority = json.optString("priority", "MEDIUM"),
        dueAt = json.optLongOrNull("dueAt"),
        sortOrder = json.optInt("sortOrder", 0),
        monthlyPlanId = json.optLongOrNull("monthlyPlanId"),
        weeklyPlanId = json.optLongOrNull("weeklyPlanId"),
        dailyPlanId = json.optLongOrNull("dailyPlanId"),
        sourceType = json.optString("sourceType", "MANUAL"),
        sourceTaskId = json.optLongOrNull("sourceTaskId"),
        timeSegment = json.optStringOrNull("timeSegment"),
        isTopFocus = json.optBoolean("isTopFocus", false),
        estimatedMinutes = json.optIntOrNull("estimatedMinutes"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun jsonToHabitTemplate(json: JSONObject) = HabitTemplateEntity(
        id = json.getLong("id"),
        title = json.getString("title"),
        note = json.optStringOrNull("note"),
        estimatedMinutes = json.optIntOrNull("estimatedMinutes"),
        sortOrder = json.optInt("sortOrder", 0),
        enabled = json.optBoolean("enabled", true),
        frequencyType = json.optString("frequencyType", "DAILY"),
        weekdaysMask = json.optIntOrNull("weekdaysMask"),
        preferredTimeSegment = json.optStringOrNull("preferredTimeSegment"),
        targetAppPackageName = json.optStringOrNull("targetAppPackageName"),
        iconUri = json.optStringOrNull("iconUri"),
        iconLabel = json.optStringOrNull("iconLabel"),
        dailyTargetCount = json.optInt("dailyTargetCount", 1),
        streakEnabled = json.optBoolean("streakEnabled", true),
        baseCompletedDays = json.optInt("baseCompletedDays", 0),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
    )

    private fun jsonToHabitRecord(json: JSONObject) = HabitRecordEntity(
        id = json.getLong("id"),
        habitTemplateId = json.getLong("habitTemplateId"),
        date = json.getString("date"),
        status = json.optString("status", "TODO"),
        completedAt = json.optLongOrNull("completedAt"),
        note = json.optStringOrNull("note"),
        completedCount = json.optInt("completedCount", 0),
    )

    private fun jsonToSurfaceConfig(json: JSONObject) = SurfaceConfigEntity(
        id = json.getLong("id"),
        targetType = json.getString("targetType"),
        targetId = json.optLongOrNull("targetId"),
        surfaceType = json.getString("surfaceType"),
        theme = json.optString("theme", "DEFAULT"),
        maxItems = json.optInt("maxItems", 5),
        showCompleted = json.optBoolean("showCompleted", false),
        showProgress = json.optBoolean("showProgress", true),
        opacity = json.optDouble("opacity", 1.0).toFloat(),
        anchorPosition = json.optString("anchorPosition", "TOP"),
        textScale = json.optDouble("textScale", 1.0).toFloat(),
        refreshPolicy = json.optString("refreshPolicy", "AUTO"),
    )

    private fun jsonToWidgetInstanceConfig(json: JSONObject) = WidgetInstanceConfigEntity(
        appWidgetId = json.getInt("appWidgetId"),
        surfaceConfigId = json.getLong("surfaceConfigId"),
    )
}

private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
