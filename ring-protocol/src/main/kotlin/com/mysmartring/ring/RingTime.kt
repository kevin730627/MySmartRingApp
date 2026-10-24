package com.mysmartring.ring

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 戒指時間：自 2000-01-01 00:00:00 起算的秒數，為不帶時區的本地時間。
 * 以同步當下手機的時區換算成 UTC 瞬間。
 */
object RingTime {
    private val EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0, 0)

    fun toInstant(ringSeconds: Long, zone: ZoneId): Instant =
        EPOCH.plusSeconds(ringSeconds).atZone(zone).toInstant()
}
