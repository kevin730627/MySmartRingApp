package com.mysmartring.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StepsAggregationTest {
    private val zone = ZoneId.of("Asia/Taipei")

    private fun steps(date: String, time: String, value: Int) = ReadingEntity(
        metric = "STEPS",
        measuredAtMillis = java.time.ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), zone).toInstant().toEpochMilli(),
        value = value,
        syncId = 1,
    )

    @Test
    fun `當日總步數是所有區間加總`() {
        assertEquals(713 + 614 + 1613, StepsAggregation.total(listOf(steps("2026-10-24", "17:00", 713), steps("2026-10-24", "17:30", 614), steps("2026-10-24", "18:00", 1613))))
        assertEquals(0, StepsAggregation.total(emptyList()))
    }

    @Test
    fun `同一小時的兩個半小時區間合併`() {
        val hours = StepsAggregation.byHour(
            listOf(steps("2026-10-24", "17:00", 713), steps("2026-10-24", "17:30", 614), steps("2026-10-24", "20:00", 2892)),
            zone,
        )
        assertEquals(24, hours.size)
        assertEquals(713 + 614, hours[17])
        assertEquals(2892, hours[20])
        assertEquals(0, hours[3])
    }

    @Test
    fun `每日步數補零，範圍外的 Reading 不計入`() {
        val readings = listOf(
            steps("2026-10-22", "10:00", 999), // 範圍前
            steps("2026-10-24", "17:00", 713),
            steps("2026-10-24", "23:30", 100),
            steps("2026-10-26", "09:00", 1007),
            steps("2026-10-28", "09:00", 500), // 範圍後
        )
        val days = StepsAggregation.byDay(readings, zone, LocalDate.parse("2026-10-24"), 3)
        assertEquals(listOf(813, 0, 1007), days)
    }

    @Test
    fun `日期以手機時區的當地日期為準，不是 UTC`() {
        // 台北 10/25 00:30 在 UTC 是 10/24 16:30
        val days = StepsAggregation.byDay(listOf(steps("2026-10-25", "00:30", 42)), zone, LocalDate.parse("2026-10-24"), 2)
        assertEquals(listOf(0, 42), days)
    }
}
