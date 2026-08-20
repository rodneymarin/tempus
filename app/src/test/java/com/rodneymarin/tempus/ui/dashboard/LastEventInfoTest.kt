package com.rodneymarin.tempus.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LastEventInfoTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 14)
    private fun info(daysAgo: Long?) = lastEventInfo(
        lastEventDay = daysAgo?.let { today.minusDays(it) },
        today = today,
    )

    // ---- classification boundaries ----

    @Test fun noEvents_isNone() {
        assertEquals(LastEventInfo(LastEventKind.NONE, 0), info(null))
    }

    @Test fun today_isToday() {
        assertEquals(LastEventInfo(LastEventKind.TODAY, 0), info(0))
    }

    @Test fun futureEvent_countsAsToday() {
        assertEquals(LastEventInfo(LastEventKind.TODAY, 0), info(-2))
    }

    @Test fun oneDayAgo_isOneDay() {
        assertEquals(LastEventInfo(LastEventKind.DAYS, 1), info(1))
    }

    @Test fun sixDaysAgo_isSixDays() {
        assertEquals(LastEventInfo(LastEventKind.DAYS, 6), info(6))
    }

    @Test fun sevenDaysAgo_isOneWeek() {
        assertEquals(LastEventInfo(LastEventKind.WEEKS, 1), info(7))
    }

    @Test fun thirteenDaysAgo_isOneWeek() {
        assertEquals(LastEventInfo(LastEventKind.WEEKS, 1), info(13))
    }

    @Test fun fourteenDaysAgo_isTwoWeeks() {
        assertEquals(LastEventInfo(LastEventKind.WEEKS, 2), info(14))
    }

    @Test fun hundredDaysAgo_isFourteenWeeks() {
        assertEquals(LastEventInfo(LastEventKind.WEEKS, 14), info(100))
    }
}
