package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_instance_configs")
data class WidgetInstanceConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int,
    @ColumnInfo(name = "surface_config_id")
    val surfaceConfigId: Long,
)
