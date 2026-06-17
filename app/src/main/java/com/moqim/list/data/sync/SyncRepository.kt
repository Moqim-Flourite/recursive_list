package com.moqim.list.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.moqim.list.data.local.database.AppDatabase
import com.moqim.list.data.local.provider.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 同步仓库
 *
 * 核心同步逻辑：
 * 1. 发现服务器（mDNS → 缓存地址 → 手动配置）
 * 2. 从 Room 数据库读取全部计划数据
 * 3. 打包为层级 JSON（月→周→日→任务 + 习惯）
 * 4. POST 到电脑端
 * 5. 更新同步日志
 */
class SyncRepository(private val context: Context) {

    companion object {
        private const val TAG = "SyncRepository"
        private const val SYNC_ENDPOINT = "/sync"
        private const val HEALTH_ENDPOINT = "/health"
    }

    val config = SyncConfig(context)
    private val logStore = SyncLogStore(context)
    private val nsdDiscovery = NsdDiscovery(context)

    /** 清除同步日志（解除配对时调用） */
    fun clearSyncLog() = logStore.clearAll()

    /**
     * 同步结果
     */
    sealed class SyncResult {
        data class Success(val recordCount: Int, val summary: String) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object Disabled : SyncResult()
        object NotPaired : SyncResult()
    }

    /**
     * 发现服务器地址
     *
     * 优先级：
     * 1. 缓存地址（上次成功连接的）→ 健康检查
     * 2. mDNS 发现 → 健康检查 → 缓存
     * 3. 手动配置地址 → 健康检查
     * 4. 全部失败 → null
     */
    suspend fun discoverServer(): Pair<String, Int>? {
        // 1. 尝试缓存地址
        val cachedHost = config.lastConnectedHost
        val cachedPort = config.lastConnectedPort
        if (cachedHost.isNotBlank() && cachedPort > 0) {
            if (nsdDiscovery.checkHealth(cachedHost, cachedPort)) {
                Log.i(TAG, "使用缓存地址: $cachedHost:$cachedPort")
                return cachedHost to cachedPort
            }
        }

        // 2. mDNS 发现
        val discovered = nsdDiscovery.discover()
        if (discovered != null) {
            val (host, port) = discovered
            if (nsdDiscovery.checkHealth(host, port)) {
                config.cacheConnectedAddress(host, port)
                Log.i(TAG, "mDNS 发现成功: $host:$port")
                return discovered
            }
        }

        // 3. 手动配置地址
        val manualHost = config.serverHost
        val manualPort = config.serverPort
        if (manualHost.isNotBlank()) {
            if (nsdDiscovery.checkHealth(manualHost, manualPort)) {
                config.cacheConnectedAddress(manualHost, manualPort)
                Log.i(TAG, "使用手动配置地址: $manualHost:$manualPort")
                return manualHost to manualPort
            }
        }

        Log.w(TAG, "未找到可用服务器")
        return null
    }

    /**
     * 执行完整同步流程
     *
     * 读取全部计划数据，打包为 JSON，上传到电脑端。
     *
     * @param onProgress 进度回调
     */
    suspend fun syncAll(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext SyncResult.Disabled
        if (!config.isPaired) return@withContext SyncResult.NotPaired

        // 发现服务器
        val server = discoverServer()
        if (server == null) {
            return@withContext SyncResult.Error("未找到同步服务器，请检查电脑端是否已启动")
        }
        val (host, port) = server
        val baseUrl = "http://$host:$port"

        try {
            onProgress?.invoke("正在读取数据...")
            // 读取全部数据并打包
            val jsonData = packageAllData()
            val recordCount = countRecords(jsonData)
            Log.i(TAG, "打包完成: $recordCount 条记录")
            onProgress?.invoke("正在上传 $recordCount 条记录...")

            // 上传
            val success = uploadData(baseUrl, jsonData)
            if (success) {
                logStore.markSuccess(recordCount)
                val summary = "$recordCount 条记录"
                Log.i(TAG, "同步成功: $summary")
                return@withContext SyncResult.Success(recordCount, summary)
            } else {
                logStore.markFailed("上传失败")
                return@withContext SyncResult.Error("上传失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步异常", e)
            logStore.markFailed(e.message ?: "未知错误")
            return@withContext SyncResult.Error(e.message ?: "同步异常")
        }
    }

    /**
     * 读取全部计划数据并打包为层级 JSON
     *
     * 结构：
     * {
     *   "app_id": "recursive_list",
     *   "date": "2026-06-17",
     *   "syncedAt": 1718611200000,
     *   "monthly_plans": [...],
     *   "weekly_plans": [...],
     *   "daily_plans": [...],
     *   "execution_tasks": [...],
     *   "habit_templates": [...],
     *   "habit_records": [...]
     * }
     */
    private suspend fun packageAllData(): JSONObject = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)

        val monthlyPlans = db.monthlyPlanDao().getAll()
        val weeklyPlans = db.weeklyPlanDao().getAll()
        val dailyPlans = db.dailyPlanDao().getAll()
        val executionTasks = db.executionTaskDao().getAll()
        val habitTemplates = db.habitTemplateDao().getAll()
        val habitRecords = db.habitRecordDao().getAll()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        JSONObject().apply {
            put("app_id", "recursive_list")
            put("date", today)
            put("syncedAt", System.currentTimeMillis())

            // 月计划
            put("monthly_plans", JSONArray().apply {
                monthlyPlans.forEach { plan ->
                    put(JSONObject().apply {
                        put("id", plan.id)
                        put("title", plan.title)
                        put("theme", plan.theme)
                        put("goal", plan.goal ?: JSONObject.NULL)
                        put("start_date", plan.startDate)
                        put("end_date", plan.endDate)
                        put("status", plan.status)
                        put("created_at", plan.createdAt)
                        put("updated_at", plan.updatedAt)
                    })
                }
            })

            // 周计划
            put("weekly_plans", JSONArray().apply {
                weeklyPlans.forEach { plan ->
                    put(JSONObject().apply {
                        put("id", plan.id)
                        put("monthly_plan_id", plan.monthlyPlanId)
                        put("title", plan.title)
                        put("goal", plan.goal ?: JSONObject.NULL)
                        put("week_start_date", plan.weekStartDate)
                        put("week_end_date", plan.weekEndDate)
                        put("capacity", plan.capacity ?: JSONObject.NULL)
                        put("review", plan.review ?: JSONObject.NULL)
                        put("status", plan.status)
                        put("created_at", plan.createdAt)
                        put("updated_at", plan.updatedAt)
                    })
                }
            })

            // 日计划
            put("daily_plans", JSONArray().apply {
                dailyPlans.forEach { plan ->
                    put(JSONObject().apply {
                        put("id", plan.id)
                        put("weekly_plan_id", plan.weeklyPlanId ?: JSONObject.NULL)
                        put("date", plan.date)
                        put("summary", plan.summary ?: JSONObject.NULL)
                        put("energy_level", plan.energyLevel ?: JSONObject.NULL)
                        put("generated_from_previous_day", plan.generatedFromPreviousDay)
                        put("review", plan.review ?: JSONObject.NULL)
                        put("status", plan.status)
                        put("created_at", plan.createdAt)
                        put("updated_at", plan.updatedAt)
                    })
                }
            })

            // 执行任务
            put("execution_tasks", JSONArray().apply {
                executionTasks.forEach { task ->
                    put(JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("note", task.note ?: JSONObject.NULL)
                        put("type", task.type)
                        put("status", task.status)
                        put("priority", task.priority)
                        put("due_at", task.dueAt ?: JSONObject.NULL)
                        put("sort_order", task.sortOrder)
                        put("monthly_plan_id", task.monthlyPlanId ?: JSONObject.NULL)
                        put("weekly_plan_id", task.weeklyPlanId ?: JSONObject.NULL)
                        put("daily_plan_id", task.dailyPlanId ?: JSONObject.NULL)
                        put("source_type", task.sourceType)
                        put("source_task_id", task.sourceTaskId ?: JSONObject.NULL)
                        put("time_segment", task.timeSegment ?: JSONObject.NULL)
                        put("specific_time", task.specificTime ?: JSONObject.NULL)
                        put("is_top_focus", task.isTopFocus)
                        put("estimated_minutes", task.estimatedMinutes ?: JSONObject.NULL)
                        put("created_at", task.createdAt)
                        put("updated_at", task.updatedAt)
                    })
                }
            })

            // 习惯模板
            put("habit_templates", JSONArray().apply {
                habitTemplates.forEach { template ->
                    put(JSONObject().apply {
                        put("id", template.id)
                        put("title", template.title)
                        put("note", template.note ?: JSONObject.NULL)
                        put("estimated_minutes", template.estimatedMinutes ?: JSONObject.NULL)
                        put("sort_order", template.sortOrder)
                        put("enabled", template.enabled)
                        put("frequency_type", template.frequencyType)
                        put("weekdays_mask", template.weekdaysMask ?: JSONObject.NULL)
                        put("preferred_time_segment", template.preferredTimeSegment ?: JSONObject.NULL)
                        put("target_app_package_name", template.targetAppPackageName ?: JSONObject.NULL)
                        put("icon_uri", template.iconUri ?: JSONObject.NULL)
                        put("icon_label", template.iconLabel ?: JSONObject.NULL)
                        put("daily_target_count", template.dailyTargetCount)
                        put("streak_enabled", template.streakEnabled)
                        put("base_completed_days", template.baseCompletedDays)
                        put("created_at", template.createdAt)
                        put("updated_at", template.updatedAt)
                    })
                }
            })

            // 习惯记录
            put("habit_records", JSONArray().apply {
                habitRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id)
                        put("habit_template_id", record.habitTemplateId)
                        put("date", record.date)
                        put("status", record.status)
                        put("completed_at", record.completedAt ?: JSONObject.NULL)
                        put("note", record.note ?: JSONObject.NULL)
                        put("completed_count", record.completedCount)
                    })
                }
            })
        }
    }

    /**
     * 统计记录总数
     */
    private fun countRecords(data: JSONObject): Int {
        var count = 0
        count += data.getJSONArray("monthly_plans").length()
        count += data.getJSONArray("weekly_plans").length()
        count += data.getJSONArray("daily_plans").length()
        count += data.getJSONArray("execution_tasks").length()
        count += data.getJSONArray("habit_templates").length()
        count += data.getJSONArray("habit_records").length()
        return count
    }

    /**
     * 上传数据到服务器
     */
    private fun uploadData(baseUrl: String, data: JSONObject): Boolean {
        val url = URL("$baseUrl$SYNC_ENDPOINT")
        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${config.authToken}")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val writer = OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(data.toString(2))
            writer.flush()
            writer.close()

            val success = conn.responseCode in 200..299
            if (!success) {
                val errorBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream)).readText()
                } catch (_: Exception) { "" }
                Log.w(TAG, "上传失败 HTTP ${conn.responseCode}: $errorBody")
            }
            success
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 检查是否在 WiFi 网络下
     */
    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 配对（设置 token）
     */
    fun pair(token: String, host: String, port: Int) {
        config.authToken = token
        config.serverHost = host
        config.serverPort = port
        config.enabled = true
        config.cacheConnectedAddress(host, port)
    }

    /**
     * 解除配对
     */
    fun unpair() {
        config.clear()
        logStore.clearAll()
    }

    /**
     * 获取同步状态摘要（用于 UI 显示）
     */
    fun getSyncStatusSummary(): String {
        if (!config.enabled) return "同步未启用"
        if (!config.isPaired) return "未配对"

        val lastSync = logStore.lastSyncTimestamp
        val lastResult = logStore.lastSyncResult
        val lastCount = logStore.lastSyncedCount

        return if (lastSync > 0) {
            val timeAgo = formatTimeAgo(lastSync)
            "上次同步: $timeAgo（$lastCount 条记录，$lastResult）"
        } else {
            "已配对，等待首次同步"
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            else -> "${days}天前"
        }
    }
}
