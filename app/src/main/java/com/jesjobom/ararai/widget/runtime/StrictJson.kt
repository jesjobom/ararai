package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

internal class StrictJson private constructor(
    private val maxDepth: Int,
    private val maxValues: Int,
    private val maxStringChars: Int,
) {
    private var values = 0

    fun parse(raw: String): JsonElement {
        val reader = JsonReader(StringReader(raw)).apply { strictness = Strictness.STRICT }
        val result = readValue(reader, 0)
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON content" }
        return result
    }

    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= maxDepth) { "JSON is too deep" }
        require(++values <= maxValues) { "JSON has too many values" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readObject(reader, depth)
            JsonToken.BEGIN_ARRAY -> readArray(reader, depth)
            JsonToken.STRING -> JsonPrimitive(reader.nextString().bounded())
            JsonToken.NUMBER -> {
                val rawNumber = reader.nextString()
                val number = rawNumber.toBigDecimalOrNull() ?: error("Invalid JSON number")
                require(number.toDouble().isFinite()) { "Non-finite JSON number" }
                JsonPrimitive(number)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonNull.INSTANCE
            }
            else -> error("Invalid JSON token")
        }
    }

    private fun readObject(reader: JsonReader, depth: Int): JsonObject {
        reader.beginObject()
        val result = JsonObject()
        val names = mutableSetOf<String>()
        while (reader.hasNext()) {
            val name = reader.nextName().bounded()
            require(names.add(name)) { "Duplicate JSON field" }
            result.add(name, readValue(reader, depth + 1))
        }
        reader.endObject()
        return result
    }

    private fun readArray(reader: JsonReader, depth: Int): JsonArray {
        reader.beginArray()
        val result = JsonArray()
        while (reader.hasNext()) result.add(readValue(reader, depth + 1))
        reader.endArray()
        return result
    }

    private fun String.bounded(): String {
        require(length <= maxStringChars) { "JSON string is too large" }
        return this
    }

    companion object {
        fun parse(
            raw: String,
            maxDepth: Int = WidgetRuntimePolicy.MAX_JSON_DEPTH,
            maxValues: Int = WidgetRuntimePolicy.MAX_JSON_VALUES,
            maxStringChars: Int = WidgetRuntimePolicy.MAX_STRING_CHARS,
        ): JsonElement = StrictJson(maxDepth, maxValues, maxStringChars).parse(raw)

        fun canonical(element: JsonElement): String = normalize(element).toString()

        fun normalize(element: JsonElement): JsonElement = when {
            element.isJsonNull -> JsonNull.INSTANCE
            element.isJsonArray -> JsonArray().also { result ->
                element.asJsonArray.forEach { result.add(normalize(it)) }
            }
            element.isJsonObject -> JsonObject().also { result ->
                element.asJsonObject.entrySet().sortedBy { it.key }.forEach { (name, value) ->
                    result.add(name, normalize(value))
                }
            }
            element.asJsonPrimitive.isNumber -> JsonPrimitive(
                BigDecimal(element.asJsonPrimitive.asString).stripTrailingZeros(),
            )
            else -> element.deepCopy()
        }
    }
}
