package com.moqim.list.data.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 GitHub Releases API 检查是否有新版本。
 *
 * 使用方法：
 * 1. 在 GitHub 上为项目创建 Release（tag 如 v1.1）
 * 2. 在 Release 的 Assets 中上传 APK 文件
 * 3. APP 会自动对比 tag_name 中的版本号
 */
object GitHubUpdateChecker {

    private const val GITHUB_REPO = "Moqim-Flourite/recursive_list"
    private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * 检查更新，返回 UpdateInfo 或 null（无更新/出错）。
     * @param currentVersionCode 当前 APP 的 versionCode
     */
    fun checkForUpdate(currentVersionCode: Int): UpdateResult {
        return try {
            val url = URL(RELEASES_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "RecursiveList-Android")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) {
                return UpdateResult.Error("HTTP ${conn.responseCode}")
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")          // e.g. "v1.1"
            val releaseName = json.optString("name", tagName)
            val body_ = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            // 从 tag_name 提取版本号：v1.1 -> 1.1 -> 尝试解析为整数
            val versionStr = tagName.removePrefix("v")
            val remoteVersionCode = versionStr.split(".").firstOrNull()?.toIntOrNull() ?: 0

            // 找到 APK 下载链接
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    apkSize = asset.optLong("size", 0)
                    break
                }
            }

            if (remoteVersionCode > currentVersionCode) {
                UpdateResult.Available(
                    versionName = tagName,
                    releaseName = releaseName,
                    releaseNotes = body_,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkUrl,
                    apkSizeBytes = apkSize,
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class UpdateResult {
    /** 已是最新版本 */
    data object UpToDate : UpdateResult()

    /** 有新版本可用 */
    data class Available(
        val versionName: String,
        val releaseName: String,
        val releaseNotes: String,
        val publishedAt: String,
        val apkDownloadUrl: String?,
        val apkSizeBytes: Long,
    ) : UpdateResult()

    /** 检查失败 */
    data class Error(val message: String) : UpdateResult()
}