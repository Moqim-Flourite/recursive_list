package com.moqim.list.widget

import com.moqim.list.data.local.entity.SurfaceConfigEntity

data class WidgetPalette(
    val backgroundColor: Int,
    val titleColor: Int,
    val summaryColor: Int,
    val contentColor: Int,
)

data class WidgetRenderConfig(
    val showCompleted: Boolean,
    val maxItems: Int,
    val showProgress: Boolean,
    val textScale: Float,
    val theme: String,
    val opacity: Float,
    val refreshPolicy: String,
)

object WidgetRenderStyle {

    fun fromSurfaceConfig(surfaceConfig: SurfaceConfigEntity?): WidgetRenderConfig {
        return WidgetRenderConfig(
            showCompleted = surfaceConfig?.showCompleted ?: true,
            maxItems = (surfaceConfig?.maxItems ?: 3).coerceIn(1, 3),
            showProgress = surfaceConfig?.showProgress ?: true,
            textScale = (surfaceConfig?.textScale ?: 1f).coerceIn(0.8f, 1.4f),
            theme = surfaceConfig?.theme ?: "DEFAULT",
            opacity = (surfaceConfig?.opacity ?: 1f).coerceIn(0.35f, 1f),
            refreshPolicy = surfaceConfig?.refreshPolicy ?: "AUTO",
        )
    }

    fun palette(theme: String, opacity: Float): WidgetPalette {
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return when (theme) {
            "MINIMAL" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 18, 18, 18),
                titleColor = android.graphics.Color.WHITE,
                summaryColor = android.graphics.Color.parseColor("#D0D0D0"),
                contentColor = android.graphics.Color.WHITE,
            )
            "MICROSOFT" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 232, 238, 249),
                titleColor = android.graphics.Color.parseColor("#1B2A41"),
                summaryColor = android.graphics.Color.parseColor("#355070"),
                contentColor = android.graphics.Color.parseColor("#122033"),
            )
            "FOCUS" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 23, 32, 43),
                titleColor = android.graphics.Color.parseColor("#8FD3FF"),
                summaryColor = android.graphics.Color.parseColor("#C9E7FF"),
                contentColor = android.graphics.Color.WHITE,
            )
            "PIXEL" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 45, 24, 84),
                titleColor = android.graphics.Color.parseColor("#FFE082"),
                summaryColor = android.graphics.Color.parseColor("#FFD6F5"),
                contentColor = android.graphics.Color.WHITE,
            )
            else -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 31, 31, 31),
                titleColor = android.graphics.Color.WHITE,
                summaryColor = android.graphics.Color.parseColor("#E0E0E0"),
                contentColor = android.graphics.Color.WHITE,
            )
        }
    }

    fun currentSegmentPalette(theme: String, opacity: Float): WidgetPalette {
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return when (theme) {
            "MINIMAL" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 18, 18, 18),
                titleColor = android.graphics.Color.WHITE,
                summaryColor = android.graphics.Color.parseColor("#D0D0D0"),
                contentColor = android.graphics.Color.WHITE,
            )
            "MICROSOFT" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 232, 238, 249),
                titleColor = android.graphics.Color.parseColor("#1B2A41"),
                summaryColor = android.graphics.Color.parseColor("#355070"),
                contentColor = android.graphics.Color.parseColor("#122033"),
            )
            "FOCUS" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 23, 32, 43),
                titleColor = android.graphics.Color.parseColor("#8FD3FF"),
                summaryColor = android.graphics.Color.parseColor("#C9E7FF"),
                contentColor = android.graphics.Color.WHITE,
            )
            "PIXEL" -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 45, 24, 84),
                titleColor = android.graphics.Color.parseColor("#FFE082"),
                summaryColor = android.graphics.Color.parseColor("#FFD6F5"),
                contentColor = android.graphics.Color.WHITE,
            )
            else -> WidgetPalette(
                backgroundColor = android.graphics.Color.argb(alpha, 21, 32, 51),
                titleColor = android.graphics.Color.WHITE,
                summaryColor = android.graphics.Color.parseColor("#D6E4FF"),
                contentColor = android.graphics.Color.WHITE,
            )
        }
    }
}
