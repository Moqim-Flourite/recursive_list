package com.moqim.list.widget

import android.content.Context
import com.moqim.list.data.local.entity.SurfaceConfigEntity
import com.moqim.list.data.local.entity.WidgetInstanceConfigEntity
import com.moqim.list.data.local.provider.DatabaseProvider
import kotlinx.coroutines.runBlocking

object WidgetSurfaceConfigStore {

    fun ensureWidgetInstanceConfig(context: Context, appWidgetId: Int, targetType: String) {
        runBlocking {
            val db = DatabaseProvider.get(context)
            val widgetDao = db.widgetInstanceConfigDao()
            val surfaceDao = db.surfaceConfigDao()
            val existing = widgetDao.getByAppWidgetId(appWidgetId)
            if (existing == null) {
                val surfaceId = surfaceDao.upsert(
                    SurfaceConfigEntity(
                        targetType = targetType,
                        surfaceType = "APP_WIDGET",
                        theme = "DEFAULT",
                        showCompleted = true,
                    ),
                )
                widgetDao.upsert(
                    WidgetInstanceConfigEntity(
                        appWidgetId = appWidgetId,
                        surfaceConfigId = surfaceId,
                    ),
                )
            }
        }
    }

    fun loadSurfaceConfig(context: Context, appWidgetId: Int): SurfaceConfigEntity? = runBlocking {
        val db = DatabaseProvider.get(context)
        val widget = db.widgetInstanceConfigDao().getByAppWidgetId(appWidgetId)
        widget?.surfaceConfigId?.let { db.surfaceConfigDao().getById(it) }
    }

    fun isAggressiveRefreshPolicy(context: Context, appWidgetId: Int): Boolean {
        return loadSurfaceConfig(context, appWidgetId)?.refreshPolicy == "AGGRESSIVE"
    }

    fun deleteWidgetInstanceConfig(context: Context, appWidgetId: Int) {
        runBlocking {
            val db = DatabaseProvider.get(context)
            val widgetDao = db.widgetInstanceConfigDao()
            val surfaceDao = db.surfaceConfigDao()
            val existing = widgetDao.getByAppWidgetId(appWidgetId)
            widgetDao.deleteByAppWidgetId(appWidgetId)
            existing?.surfaceConfigId?.let { surfaceDao.deleteById(it) }
        }
    }
}
