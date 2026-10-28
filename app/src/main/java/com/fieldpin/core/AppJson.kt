package com.fieldpin.core

import kotlinx.serialization.json.Json

object AppJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
