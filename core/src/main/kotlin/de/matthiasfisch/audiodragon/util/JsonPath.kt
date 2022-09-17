package de.matthiasfisch.audiodragon.util

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import net.minidev.json.JSONArray

fun DocumentContext.readTextAtPath(jsonPath: JsonPath): String? = try {
    when (val value = read<Any>(jsonPath)) {
        is String -> value
        is JSONArray -> if (value.isNotEmpty() && value[0] is String) {
            value[0] as String
        } else {
            null
        }

        else -> null
    }
} catch (e: PathNotFoundException) {
    null
}

fun DocumentContext.readTextListAtPath(jsonPath: JsonPath): List<String>? = try {
    when (val value = read<Any>(jsonPath)) {
        is String -> listOf(value)
        is JSONArray -> value.filterIsInstance<String>()
        else -> null
    }
} catch (e: PathNotFoundException) {
    null
}