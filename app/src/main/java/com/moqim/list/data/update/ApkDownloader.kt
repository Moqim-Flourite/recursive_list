package com.moqim.list.data.update

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载器（协程版）
 *
 * 从 TimeTrackerApp 迁移，增强点：
 * - 协程支持（suspend fun）
 * - APK 缓存 + HEAD 请求校验大小
 * - 旧 APK 自动清理
 * - 下载完整性校验
 * - 手动多级重定向
 */
object ApkDownloader {

    private const val TAG = "ApkDownloader"
    private const val MAX_REDIRECTS = 5

    /**
     * 下载 APK 到外部缓存目录
     * @param updateInfo 更新信息
     * @param onProgress 进度回调 (0-100)
     * @return 下载的 APK 文件
     */
    suspend fun download(
        context: Context,
        updateInfo: GitHubUpdateChecker.UpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "updates"
            )
            if (!dir.exists()) dir.mkdirs()

            // 清理旧版本 APK（不同文件名的）
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk") && file.name != updateInfo.apkFileName) {
                    file.delete()
                    Log.i(TAG, "清理旧 APK: ${file.name}")
                }
            }

            val apkFile = File(dir, updateInfo.apkFileName)

            // 如果已下载过同名文件，用 HEAD 请求比对大小
            if (apkFile.exists() && apkFile.length() > 100_000) {
                try {
                    val headConn = openConnection(URL(updateInfo.apkDownloadUrl), "HEAD")
                    val remoteSize = headConn.contentLength.toLong()
                    headConn.disconnect()

                    if (remoteSize > 0 && apkFile.length() == remoteSize) {
                        Log.i(TAG, "APK 已存在且大小匹配 (${apkFile.length()} bytes)，跳过下载")
                        return@withContext Result.success(apkFile)
                    } else {
                        Log.i(TAG, "APK 大小不匹配: 本地=${apkFile.length()}, 远程=$remoteSize，重新下载")
                        apkFile.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "HEAD 请求失败，使用缓存: ${e.message}")
                    return@withContext Result.success(apkFile)
                }
            }

            Log.i(TAG, "开始下载: ${updateInfo.apkDownloadUrl}")
            val conn = openConnection(URL(updateInfo.apkDownloadUrl), "GET")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return@withContext Result.failure(Exception("下载失败: HTTP $responseCode"))
            }

            return@withContext downloadFromConnection(conn, apkFile, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "下载 APK 失败", e)
            Result.failure(e)
        }
    }

    /**
     * 打开 HTTP 连接，支持多级重定向
     */
    private fun openConnection(url: URL, method: String): HttpURLConnection {
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val conn = currentUrl.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "RecursiveList-Android")
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = false

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

    private fun downloadFromConnection(
        conn: HttpURLConnection,
        apkFile: File,
        onProgress: (Int) -> Unit,
    ): Result<File> {
        val totalSize = conn.contentLength
        var downloaded = 0

        conn.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        onProgress((downloaded * 100 / totalSize))
                    }
                }
            }
        }
        conn.disconnect()

        // 验证下载完整性
        if (totalSize > 0 && apkFile.length() != totalSize.toLong()) {
            apkFile.delete()
            return Result.failure(
                Exception("下载不完整: 期望 ${totalSize} 字节，实际 ${apkFile.length()} 字节")
            )
        }

        Log.i(TAG, "下载完成: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
        return Result.success(apkFile)
    }
}
