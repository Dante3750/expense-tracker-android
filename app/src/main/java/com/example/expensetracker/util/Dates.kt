package com.example.expensetracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Half-open range of epoch milliseconds: `[start, endExclusive)`. */
data class EpochRange(val start: Long, val endExclusive: Long)

fun YearMonth.epochRange(zone: ZoneId = ZoneId.systemDefault()): EpochRange = EpochRange(
    start = atDay(1).toEpochMillis(zone),
    endExclusive = plusMonths(1).atDay(1).toEpochMillis(zone),
)

fun LocalDate.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
