package com.devsstream.cafepos.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

// The two small maps are stored as JSON text, e.g. {"oat":1,"extra_shot":1}
class Converters {
    @TypeConverter
    fun amountsToJson(map: Map<String, Long>): String = Json.encodeToString(map)

    @TypeConverter
    fun amountsFromJson(json: String): Map<String, Long> = Json.decodeFromString(json)

    @TypeConverter
    fun quantitiesToJson(map: Map<String, Int>): String = Json.encodeToString(map)

    @TypeConverter
    fun quantitiesFromJson(json: String): Map<String, Int> = Json.decodeFromString(json)
}
