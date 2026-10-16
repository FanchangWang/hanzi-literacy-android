package com.pupil.hanzi.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The time line on a stored entry: today and yesterday are named, anything
 * older carries its date, and every line leads with the weekday.
 */
class HistoryTimeTest {

    // Pinned so the expectations do not depend on where the test runs.
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone)
        .toInstant()
        .toEpochMilli()

    // A Friday, and the "now" every case is measured against.
    private val now = at(2026, 10, 16, 9, 57, 53)

    @Test
    fun `today is named`() {
        assertEquals("周五 今天 10:04:40", formatHistoryTime(at(2026, 10, 16, 10, 4, 40), now, zone))
    }

    @Test
    fun `yesterday is named`() {
        assertEquals("周四 昨天 10:04:40", formatHistoryTime(at(2026, 10, 15, 10, 4, 40), now, zone))
    }

    @Test
    fun `older entries carry their full date`() {
        assertEquals(
            "周六 2026-10-10 10:04:40",
            formatHistoryTime(at(2026, 10, 10, 10, 4, 40), now, zone),
        )
    }

    @Test
    fun `the day before yesterday is not yesterday`() {
        assertEquals(
            "周三 2026-10-14 23:59:59",
            formatHistoryTime(at(2026, 10, 14, 23, 59, 59), now, zone),
        )
    }

    @Test
    fun `today and yesterday are calendar days, not 24 hours`() {
        // 25 hours before 09:57 is 08:57 the day before: more than a day has
        // passed, and it is still 昨天.
        assertEquals(
            "周四 昨天 08:57:53",
            formatHistoryTime(now - 25 * 60 * 60 * 1000, now, zone),
        )
        // 30 minutes before midnight is still today, 30 minutes after is not.
        val justBeforeMidnight = at(2026, 10, 15, 23, 30, 0)
        val justAfterMidnight = at(2026, 10, 16, 0, 30, 0)
        assertEquals("周四 昨天 23:30:00", formatHistoryTime(justBeforeMidnight, justAfterMidnight, zone))
        assertEquals("周五 今天 00:30:00", formatHistoryTime(justAfterMidnight, justAfterMidnight, zone))
    }

    @Test
    fun `a week back crosses into the previous month and still dates itself`() {
        val monday = at(2026, 11, 2, 8, 0, 0)
        assertEquals(
            "周一 2026-10-26 22:00:00",
            formatHistoryTime(at(2026, 10, 26, 22, 0, 0), monday, zone),
        )
    }
}
