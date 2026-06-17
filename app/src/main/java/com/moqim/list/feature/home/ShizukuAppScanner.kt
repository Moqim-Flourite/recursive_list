package com.moqim.list.feature.home

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

@Immutable
data class ShizukuInstalledApp(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap? = null,
)

object ShizukuAppScanner {

    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    fun hasPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun loadThirdPartyApps(context: Context): List<ShizukuInstalledApp> {
        if (!isShizukuAvailable() || !hasPermission()) return emptyList()

        return runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "pm list packages -3"), null, null)
            val packages = BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.mapNotNull { line ->
                    line.removePrefix("package:").trim().takeIf { it.isNotBlank() }
                }.toList()
            }

            packages.map { packageName ->
                runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    ShizukuInstalledApp(
                        label = context.packageManager.getApplicationLabel(appInfo).toString(),
                        packageName = packageName,
                        iconBitmap = context.packageManager.getApplicationIcon(appInfo).toBitmapSafe(),
                    )
                }.getOrElse {
                    ShizukuInstalledApp(
                        label = packageName,
                        packageName = packageName,
                        iconBitmap = null,
                    )
                }
            }.sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun Drawable.toBitmapSafe(): Bitmap {
        val width = intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}