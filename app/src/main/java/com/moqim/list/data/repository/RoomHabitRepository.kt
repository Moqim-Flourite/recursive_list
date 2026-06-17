package com.moqim.list.data.repository

import com.moqim.list.data.local.dao.HabitRecordDao
import com.moqim.list.data.local.dao.HabitTemplateDao
import com.moqim.list.data.local.entity.HabitRecordEntity
import com.moqim.list.data.local.entity.HabitTemplateEntity
import com.moqim.list.domain.model.HabitSummary
import com.moqim.list.domain.model.HabitSummaryItem
import com.moqim.list.domain.repository.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomHabitRepository(
    private val habitTemplateDao: HabitTemplateDao,
    private val habitRecordDao: HabitRecordDao,
) : HabitRepository {

    override suspend fun seedDefaultsIfNeeded() {
        // seed 逻辑已移除
        return

        @Suppress("UNREACHABLE_CODE")
        if (habitTemplateDao.countAll() > 0) return

        val now = System.currentTimeMillis()
        val defaults = listOf(
            HabitTemplateEntity(
                title = "多邻国",
                estimatedMinutes = 10,
                sortOrder = 0,
                preferredTimeSegment = "EVENING",
                iconLabel = "◉",
                dailyTargetCount = 1,
                createdAt = now,
                updatedAt = now,
            ),
            HabitTemplateEntity(
                title = "记账",
                estimatedMinutes = 5,
                sortOrder = 1,
                preferredTimeSegment = "EVENING",
                iconLabel = "✦",
                dailyTargetCount = 1,
                createdAt = now,
                updatedAt = now,
            ),
            HabitTemplateEntity(
                title = "拉伸",
                estimatedMinutes = 10,
                sortOrder = 2,
                preferredTimeSegment = "MORNING_START",
                iconLabel = "△",
                dailyTargetCount = 1,
                createdAt = now,
                updatedAt = now,
            ),
            HabitTemplateEntity(
                title = "Pokémon Sleep 做饭",
                estimatedMinutes = 5,
                sortOrder = 3,
                preferredTimeSegment = "MORNING",
                iconLabel = "🍲",
                dailyTargetCount = 3,
                createdAt = now,
                updatedAt = now,
            ),
        )

        defaults.forEach { habitTemplateDao.upsert(it) }
    }

    suspend fun createHabitTemplate(
        title: String,
        iconLabel: String,
        iconUri: String?,
        dailyTargetCount: Int,
        targetAppPackageName: String?,
        streakEnabled: Boolean,
        baseCompletedDays: Int,
    ): Long {
        val now = System.currentTimeMillis()
        val nextSortOrder = habitTemplateDao.getMaxSortOrder() + 1
        return habitTemplateDao.upsert(
            HabitTemplateEntity(
                title = title.trim().ifBlank { "新打卡" },
                sortOrder = nextSortOrder,
                iconLabel = iconLabel.trim().ifBlank { "◉" },
                iconUri = iconUri,
                targetAppPackageName = targetAppPackageName?.trim()?.ifBlank { null },
                dailyTargetCount = dailyTargetCount.coerceAtLeast(1),
                streakEnabled = streakEnabled,
                baseCompletedDays = baseCompletedDays.coerceAtLeast(0),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun updateHabitTemplate(
        templateId: Long,
        title: String,
        iconLabel: String,
        iconUri: String?,
        dailyTargetCount: Int,
        targetAppPackageName: String?,
        streakEnabled: Boolean,
        baseCompletedDays: Int,
    ) {
        val current = habitTemplateDao.getById(templateId) ?: return
        habitTemplateDao.upsert(
            current.copy(
                title = title.trim().ifBlank { current.title },
                iconLabel = iconLabel.trim().ifBlank { "◉" },
                iconUri = iconUri,
                targetAppPackageName = targetAppPackageName?.trim()?.ifBlank { null },
                dailyTargetCount = dailyTargetCount.coerceAtLeast(1),
                streakEnabled = streakEnabled,
                baseCompletedDays = baseCompletedDays.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateHabitCompletedCount(
        templateId: Long,
        date: String,
        completedCount: Int,
    ) {
        val record = habitRecordDao.getByTemplateIdAndDate(templateId, date) ?: return
        habitRecordDao.upsert(
            record.copy(
                completedCount = completedCount.coerceAtLeast(0),
                status = if (completedCount > 0) "IN_PROGRESS" else "TODO",
            )
        )
    }

    override suspend fun ensureTodayRecords(date: String) {
        val templates = habitTemplateDao.getEnabledList()
        templates.forEach { template ->
            val existingCount = habitRecordDao.countByTemplateIdAndDate(template.id, date)
            if (existingCount == 0) {
                habitRecordDao.upsert(
                    HabitRecordEntity(
                        habitTemplateId = template.id,
                        date = date,
                        status = "TODO",
                        completedCount = 0,
                    )
                )
            }
        }
    }

    override suspend fun incrementHabitProgress(templateId: Long, date: String) {
        val template = habitTemplateDao.getById(templateId) ?: return
        val record = habitRecordDao.getByTemplateIdAndDate(templateId, date) ?: return
        val nextCount = (record.completedCount + 1).coerceAtMost(template.dailyTargetCount)
        val finished = nextCount >= template.dailyTargetCount
        habitRecordDao.upsert(
            record.copy(
                completedCount = nextCount,
                status = when {
                    finished -> "DONE"
                    nextCount > 0 -> "IN_PROGRESS"
                    else -> "TODO"
                },
                completedAt = if (finished) System.currentTimeMillis() else record.completedAt,
            )
        )
    }

    override suspend fun decrementHabitProgress(templateId: Long, date: String) {
        val record = habitRecordDao.getByTemplateIdAndDate(templateId, date) ?: return
        val nextCount = (record.completedCount - 1).coerceAtLeast(0)
        habitRecordDao.upsert(
            record.copy(
                completedCount = nextCount,
                status = when {
                    nextCount > 0 -> "IN_PROGRESS"
                    else -> "TODO"
                },
                completedAt = if (nextCount == 0) null else record.completedAt,
            )
        )
    }

    override suspend fun skipHabit(templateId: Long, date: String) {
        val record = habitRecordDao.getByTemplateIdAndDate(templateId, date) ?: return
        habitRecordDao.upsert(
            record.copy(
                status = "SKIPPED",
                completedCount = 0,
                completedAt = null,
            )
        )
    }

    override suspend fun deleteHabit(templateId: Long) {
        habitRecordDao.deleteByTemplateId(templateId)
        habitTemplateDao.deleteById(templateId)
    }

    suspend fun replaceHabitOrder(templateIdsInOrder: List<Long>) {
        habitTemplateDao.replaceEnabledOrder(templateIdsInOrder)
    }

    override fun observeTodayHabitSummary(date: String): Flow<HabitSummary> {
        return combine(
            habitTemplateDao.observeEnabled(),
            habitRecordDao.observeByDate(date),
        ) { templates, records ->
            val total = templates.sumOf { it.dailyTargetCount.coerceAtLeast(1) }
            val doneCount = records.sumOf { it.completedCount }

            val items = templates.map { template ->
                val record = records.firstOrNull { it.habitTemplateId == template.id }
                val totalCompletedDays = habitRecordDao.countCompletedDaysByTemplateId(template.id)
                val currentStreakDays = calculateCurrentStreakDays(template.id, LocalDate.parse(date))
                HabitSummaryItem(
                    templateId = template.id,
                    title = template.title,
                    status = record?.status ?: "TODO",
                    estimatedMinutes = template.estimatedMinutes,
                    iconLabel = template.iconLabel,
                    iconUri = template.iconUri,
                    targetAppPackageName = template.targetAppPackageName,
                    dailyTargetCount = template.dailyTargetCount.coerceAtLeast(1),
                    completedCount = record?.completedCount ?: 0,
                    preferredTimeSegment = template.preferredTimeSegment,
                    totalCompletedDays = totalCompletedDays,
                    currentStreakDays = currentStreakDays,
                    showTotalCompletedDays = template.streakEnabled,
                    baseCompletedDays = template.baseCompletedDays,
                )
            }

            HabitSummary(
                totalCount = total,
                completedCount = doneCount,
                summaryText = if (total == 0) {
                    "今日打卡：暂无打卡模板"
                } else {
                    "今日打卡：$doneCount/$total"
                },
                items = items,
            )
        }
    }

    private suspend fun calculateCurrentStreakDays(
        templateId: Long,
        today: LocalDate,
    ): Int {
        var cursor = today
        var streak = 0
        while (true) {
            val record = habitRecordDao.getByTemplateIdAndDate(templateId, cursor.toString()) ?: break
            if (record.status != "DONE") break
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
