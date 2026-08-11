package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.domain.FrequencyRange.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyRangeTest {

    private fun valid(min: Int?, max: Int?) =
        FrequencyRange.Result.Valid(min, max)

    @Test fun bothEmpty_isValidNullRange() {
        assertEquals(valid(null, null), FrequencyRange.parse("", ""))
        assertEquals(valid(null, null), FrequencyRange.parse("  ", "  "))
    }

    @Test fun onlyMin_isValid() {
        assertEquals(valid(3, null), FrequencyRange.parse("3", ""))
    }

    @Test fun onlyMax_isValid() {
        assertEquals(valid(null, 5), FrequencyRange.parse("", "5"))
    }

    @Test fun minAndMax_isValid() {
        assertEquals(valid(3, 5), FrequencyRange.parse("3", "5"))
    }

    @Test fun minGreaterThanMax_invalid() {
        assertTrue(FrequencyRange.parse("5", "3") is Result.MinGreaterThanMax)
    }

    @Test fun zeroOrNegative_invalid() {
        assertTrue(FrequencyRange.parse("0", "") is Result.InvalidNumber)
        assertTrue(FrequencyRange.parse("", "-1") is Result.InvalidNumber)
    }

    @Test fun nonNumeric_invalid() {
        assertTrue(FrequencyRange.parse("abc", "") is Result.InvalidNumber)
    }

    @Test fun summary_noRange_returnsDash() {
        assertEquals("—", FrequencyRange.summary(null, null))
    }

    @Test fun summary_minAndMax_returnsRange() {
        assertEquals("3–5", FrequencyRange.summary(3, 5))
        assertEquals("al menos 3", FrequencyRange.summary(3, null))
        assertEquals("máximo 5", FrequencyRange.summary(null, 5))
    }
}
