package com.moqim.list.feature.plans.components

internal fun validateDate(value: String, fieldName: String): String? {
    if (value.isBlank()) return "${fieldName}不能为空"
    return try {
        java.time.LocalDate.parse(value)
        null
    } catch (_: Exception) {
        "${fieldName}格式必须为 YYYY-MM-DD"
    }
}
