package com.moqim.list.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * 同步日志存储
 *
 * 记录每次同步的时间戳和状态，用于判断是否需要重新同步。
 */
class SyncLogStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次成功同步的时间戳 */
    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** 上次同步的记录数 */
    var lastSyncedCount: Int
        get() = prefs.getInt(KEY_LAST_SYNCED_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_SYNCED_COUNT, value).apply()

    /** 上次同步结果描述 */
    var lastSyncResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    /** 标记同步成功 */
    fun markSuccess(count: Int) {
        lastSyncTimestamp = System.currentTimeMillis()
        lastSyncedCount = count
        lastSyncResult = "成功"
    }

    /** 标记同步失败 */
    fun markFailed(reason: String) {
        lastSyncTimestamp = System.currentTimeMillis()
        lastSyncResult = "失败: $reason"
    }

    /** 清除所有记录 */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "recursive_list_sync_log"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LAST_SYNCED_COUNT = "last_synced_count"
        private const val KEY_LAST_RESULT = "last_sync_result"
    }
}
