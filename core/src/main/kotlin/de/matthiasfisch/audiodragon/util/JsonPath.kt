package de.matthiasfisch.audiodragon.util

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import net.minidev.json.JSONArray

fun DocumentContext.readTextAtPath(jsonPath: JsonPath): String? = readAtPath(jsonPath, String::class.java)

fun DocumentContext.readNumberAtPath(jsonPath: JsonPath): Number? = readAtPath(jsonPath, Number::class.java)

fun DocumentContext.readTextListAtPath(jsonPath: JsonPath): List<String>? = try {
    when (val value = read<Any>(jsonPath)) {
        is String -> listOf(value)
        is JSONArray -> value.filterIsInstance<String>()
        else -> null
    }
} catch (e: PathNotFoundException) {
    null
}

private fun <T> DocumentContext.readAtPath(jsonPath: JsonPath, type: Class<T>): T? = try {
    val value = read<Any>(jsonPath)
    if (type.isInstance(value)) {
        type.cast(value)
    } else if (value is JSONArray && value.isNotEmpty() && type.isInstance(value[0])) {
        type.cast(value[0])
    } else {
        null
    }
} catch (e: PathNotFoundException) {
    null
}