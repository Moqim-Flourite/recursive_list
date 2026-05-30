package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "surface_configs")
data class SurfaceConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "target_type")
    val targetType: String,
    @ColumnInfo(name = "target_id")
    val targetId: Long? = null,
    @ColumnInfo(name = "surface_type")
    val surfaceType: String,
    val theme: String = "DEFAULT",
    @ColumnInfo(name = "max_items")
    val maxItems: Int = 5,
    @ColumnInfo(name = "show_completed")
    val showCompleted: Boolean = false,
    @ColumnInfo(name = "show_progress")
    val showProgress: Boolean = true,
    val opacity: Float = 1f,
    @ColumnInfo(name = "anchor_position")
    val anchorPosition: String = "TOP",
    @ColumnInfo(name = "text_scale")
    val textScale: Float = 1f,
    @ColumnInfo(name = "refresh_policy")
    val refreshPolicy: String = "AUTO",
)
