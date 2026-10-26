package com.mysmartring.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 步數的彙總。步數 Reading 是戒指每半小時區間的步數（時間點為區間起點），
 * 要加總才有意義，不能像心率那樣取最新值。
 */
object StepsAggregation {
    fun total(readings: List<ReadingEntity>): Int = readings.sumOf { it.value }

    /** 目標達成比例，0 到 1；超過目標仍算 1（圓環畫滿），[goal] 不是正數時為 0 */
    fun progress(steps: Int, goal: Int): Float = if (goal <= 0) 0f else (steps.toFloat() / goal).coerceIn(0f, 1f)

    /** 一天 24 小時各自的步數；[readings] 應只含當天的 Reading */
    fun byHour(readings: List<ReadingEntity>, zone: ZoneId): List<Int> {
        val hours = IntArray(24)
        for (r in readings) hours[Instant.ofEpochMilli(r.measuredAtMillis).atZone(zone).hour] += r.value
        return hours.toList()
    }

    /** 從 [firstDay] 起連續 [days] 天，每天的步數；沒有資料的日子是 0，範圍外的 Reading 略過 */
    fun byDay(readings: List<ReadingEntity>, zone: ZoneId, firstDay: LocalDate, days: Int): List<Int> {
        val totals = IntArray(days)
        for (r in readings) {
            val index = ChronoUnit.DAYS.between(firstDay, Instant.ofEpochMilli(r.measuredAtMillis).atZone(zone).toLocalDate()).toInt()
            if (index in 0 until days) totals[index] += r.value
        }
        return totals.toList()
    }
}
