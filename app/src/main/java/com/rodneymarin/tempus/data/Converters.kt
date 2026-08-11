package com.rodneymarin.tempus.data

import androidx.room.TypeConverter
import com.rodneymarin.tempus.domain.FrequencyPeriod

class Converters {
    @TypeConverter fun periodToString(p: FrequencyPeriod): String = p.name
    @TypeConverter fun stringToPeriod(s: String): FrequencyPeriod = FrequencyPeriod.valueOf(s)
}
