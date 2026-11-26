package com.devsstream.cafepos.data.remote

import kotlinx.serialization.json.Json

val ApiJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
