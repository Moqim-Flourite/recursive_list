package com.moqim.list.data.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Release 下载 APK 到本地缓存目录。
 */
object ApkDownloader {

    /**
     * 下载 APK 到 app 内部缓存目录，完成后回调文件路径。
     * @param onProgress 进度回调 (bytesRead, totalBytes)
     * @param onComplete 下载完成后回调文件路径，失败则为 null
     */
    fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onComplete: (File?) -> Unit,
    ) {
        Thread {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "RecursiveList-Android")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true

                // GitHub browser_download_url 会 302 到实际 CDN
                val actualConn = if (conn.responseCode in 301..308) {
                    conn.disconnect()
                    val redirect = URL(conn.getHeaderField("Location"))
                    (redirect.openConnection() as HttpURLConnection).apply {
                        setRequestProperty("User-Agent", "RecursiveList-Android")
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                } else conn

                val totalBytes = actualConn.contentLength.toLong()
                val outputFile = File(context.cacheDir, "update.apk")
                actualConn.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                }
                actualConn.disconnect()
                onComplete(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            }
        }.start()
    }
}