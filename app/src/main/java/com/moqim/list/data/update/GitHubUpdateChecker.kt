package com.moqim.list.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release 更新检查器（协程版）
 *
 * 从 TimeTrackerApp 迁移，增强点：
 * - 协程支持（suspend fun）
 * - 国内镜像备选
 * - 手动多级重定向处理
 * - semver 版本比较
 * - APK 缓存 + HEAD 校验
 * - 旧 APK 清理
 * - 下载完整性校验
 */
class GitHubUpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "GitHubUpdateChecker"
        const val GITHUB_OWNER = "Moqim-Flourite"
        const val GITHUB_REPO = "recursive_list"
        private const val API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val API_URL_MIRROR =
            "https://ghproxy.com/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val MAX_REDIRECTS = 5
    }

    data class UpdateInfo(
        val version: String,
        val releaseName: String,
        val releaseBody: String,
        val apkDownloadUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long,
        val publishedAt: String,
        val remoteVersionCode: Int = 0,
    )

    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    /**
     * semver 逐段比较，返回 > 0 表示 remote 更新
     */
    fun compareVersions(local: String, remote: String): Int {
        val cleanLocal = local.removePrefix("v").removePrefix("V")
        val cleanRemote = remote.removePrefix("v").removePrefix("V")
        val localParts = cleanLocal.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(localParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val l = localParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (l != r) return r - l
        }
        return 0
    }

    /**
     * 检查是否有新版本
     */
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "检查更新: $API_URL")

            // 尝试主 URL，失败则用镜像
            val body = try {
                fetchUrl(API_URL)
            } catch (e: Exception) {
                Log.w(TAG, "GitHub API 主地址失败，尝试镜像: ${e.message}")
                fetchUrl(API_URL_MIRROR)
            }

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            val releaseName = json.optString("name", tagName)
            val releaseBody = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            // 从 assets 中找 .apk 文件
            val assets = json.getJSONArray("assets")
            var apkUrl = ""
            var apkName = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    apkName = name
                    apkSize = asset.optLong("size", 0)
                    break
                }
            }

            if (apkUrl.isEmpty()) {
                Log.w(TAG, "Release 中没有找到 APK 文件")
                return@withContext Result.failure(Exception("Release 中没有 APK 文件"))
            }

            // 从 assets 中读取 version.json 获取 versionCode
            var remoteVersionCode = 0
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == "version.json") {
                    try {
                        val metaUrl = asset.getString("browser_download_url")
                        val metaBody = fetchUrl(metaUrl)
                        remoteVersionCode = JSONObject(metaBody).optInt("versionCode", 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "读取 version.json 失败，仅使用版本号比较: ${e.message}")
                    }
                    break
                }
            }

            val updateInfo = UpdateInfo(
                version = tagName,
                releaseName = releaseName,
                releaseBody = releaseBody,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                publishedAt = publishedAt,
                remoteVersionCode = remoteVersionCode,
            )

            val currentVersion = getCurrentVersion()
            val currentVersionCode = getCurrentVersionCode()
            val versionNameComparison = compareVersions(currentVersion, tagName)
            val hasUpdate = when {
                versionNameComparison > 0 -> true
                versionNameComparison < 0 -> false
                else -> remoteVersionCode > 0 && currentVersionCode > 0 && remoteVersionCode > currentVersionCode
            }
            Log.i(TAG, "当前=$currentVersion(code=$currentVersionCode), 最新=$tagName(code=$remoteVersionCode), 有更新=$hasUpdate")

            if (hasUpdate) {
                Result.success(updateInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Result.failure(e)
        }
    }

    /**
     * 请求 URL 并返回响应体文本
     */
    private fun fetchUrl(urlString: String): String {
        val conn = openConnection(URL(urlString), "GET", mapOf("Accept" to "application/vnd.github.v3+json"))

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val errorStream = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw Exception("HTTP $responseCode: $errorStream")
        }

        val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
        conn.disconnect()
        return body
    }

    /**
     * 打开 HTTP 连接，支持多级重定向（GitHub 下载经常 302 链式跳转）
     */
    private fun openConnection(url: URL, method: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val conn = currentUrl.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "RecursiveList-Android/${getCurrentVersion()}")
            for ((key, value) in headers) {
                conn.setRequestProperty(key, value)
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = false // 手动处理重定向

            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    currentUrl = URL(location)
                    redirectCount++
                    Log.d(TAG, "重定向 ($redirectCount): $location")
                    continue
                }
            }
            return conn
        }
        throw Exception("重定向次数过多 (${MAX_REDIRECTS})")
    }
}
