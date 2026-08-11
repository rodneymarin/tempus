package com.rodneymarin.tempus.domain

object FrequencyRange {

    sealed interface Result {
        data class Valid(val min: Int?, val max: Int?) : Result
        data object InvalidNumber : Result
        data object MinGreaterThanMax : Result
    }

    /** Empty text → null bound. Bounds must be integers ≥ 1; min ≤ max when both present. */
    fun parse(minText: String, maxText: String): Result {
        val min = minText.trim().ifEmpty { null }?.toIntOrNull()
        val max = maxText.trim().ifEmpty { null }?.toIntOrNull()
        if (minText.trim().isNotEmpty() && min == null) return Result.InvalidNumber
        if (maxText.trim().isNotEmpty() && max == null) return Result.InvalidNumber
        if (min != null && min < 1) return Result.InvalidNumber
        if (max != null && max < 1) return Result.InvalidNumber
        if (min != null && max != null && min > max) return Result.MinGreaterThanMax
        return Result.Valid(min, max)
    }

    /** Human summary, e.g. "3–5", "al menos 3", "máximo 5", "—" (no range). */
    fun summary(min: Int?, max: Int?): String = when {
        min != null && max != null -> "$min–$max"
        min != null -> "al menos $min"
        max != null -> "máximo $max"
        else -> "—"
    }
}
