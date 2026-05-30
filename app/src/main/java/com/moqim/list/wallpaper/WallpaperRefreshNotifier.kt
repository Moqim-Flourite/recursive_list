package com.moqim.list.wallpaper

import android.content.Context
import android.content.Intent

object WallpaperRefreshNotifier {
    const val ACTION_REFRESH_WALLPAPER = "com.moqim.list.action.REFRESH_WALLPAPER"

    fun notifyRefresh(context: Context) {
        context.sendBroadcast(Intent(ACTION_REFRESH_WALLPAPER).setPackage(context.packageName))
    }
}
