package com.moqim.list.system

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import com.moqim.list.wallpaper.MoqimLiveWallpaperService

object LiveWallpaperStatus {
    private fun targetComponent(context: Context): ComponentName {
        return ComponentName(context, MoqimLiveWallpaperService::class.java)
    }

    fun isMoqimLiveWallpaperActive(context: Context): Boolean {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val info: WallpaperInfo = wallpaperManager.wallpaperInfo ?: return false
        val target = targetComponent(context)
        return info.packageName == target.packageName && info.serviceName == target.className
    }

    fun statusLabel(context: Context): String {
        return if (isMoqimLiveWallpaperActive(context)) {
            "已接管主页壁纸（后续会自动同步）"
        } else {
            "未设为当前主页动态壁纸（需手动设置一次）"
        }
    }
}
