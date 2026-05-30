package com.moqim.list.system

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.moqim.list.wallpaper.MoqimLiveWallpaperService

object LiveWallpaperLauncher {
    fun open(context: Context) {
        val packageManager = context.packageManager
        val component = ComponentName(context, MoqimLiveWallpaperService::class.java)

        val intents = listOf(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            packageManager.getLaunchIntentForPackage("com.android.thememanager")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        ).filterNotNull()

        val opened = intents.any { intent ->
            if (intent.resolveActivity(packageManager) != null) {
                runCatching { context.startActivity(intent) }.isSuccess
            } else {
                false
            }
        }

        if (!opened) {
            Toast.makeText(
                context,
                "已尝试打开动态壁纸入口。请在 系统壁纸/主题/动态壁纸 中手动查找并设置 Recursive List，一次即可。",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(
                context,
                "已打开系统动态壁纸入口；请手动把 Recursive List 设为壁纸一次，之后内容会自动同步刷新。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
