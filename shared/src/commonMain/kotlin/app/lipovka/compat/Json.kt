package app.lipovka.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * The org.json calls the backup and the weather use, on kotlinx.serialization, for code shared with the iPhone.
 * Same names and the same leniency (opt… gives a default, get… throws).
 */

class JSONException(msg: String) : RuntimeException(msg)

private fun wrap(v: Any?): Any? = when (v) {
    null, JsonNull -> null
    is JsonObject -> JSONObject(v)
    is JsonArray -> JSONArray(v)
    is JsonPrimitive -> if (v.isString) v.content else v.booleanOrNull ?: v.longOrNull ?: v.doubleOrNull ?: v.content
    else -> v
}

private fun unwrap(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JSONObject -> JsonObject(v.map.mapValues { unwrap(it.value) })
    is JSONArray -> JsonArray(v.list.map { unwrap(it) })
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to unwrap(it.value) })
    is Collection<*> -> JsonArray(v.map { unwrap(it) })
    else -> JsonPrimitive(v.toString())
}

private fun num(v: Any?, key: Any): Number = when (v) {
    is Number -> v
    is String -> v.toDoubleOrNull() ?: throw JSONException("$key is not a number")
    else -> throw JSONException("$key is not a number")
}

private fun parse(text: String): JsonElement =
    try { Json.parseToJsonElement(text) } catch (e: Exception) { throw JSONException("not JSON: ${e.message}") }

private fun parseObject(text: String): LinkedHashMap<String, Any?> {
    val o = parse(text) as? JsonObject ?: throw JSONException("not a JSON object")
    return LinkedHashMap(o.mapValues { wrap(it.value) })
}

private fun parseArray(text: String): MutableList<Any?> {
    val a = parse(text) as? JsonArray ?: throw JSONException("not a JSON array")
    return a.map { wrap(it) }.toMutableList()
}

class JSONObject private constructor(internal val map: LinkedHashMap<String, Any?>) {
    constructor() : this(LinkedHashMap())
    constructor(m: Map<String, *>) : this(LinkedHashMap(m.mapValues { it.value }))
    internal constructor(o: JsonObject) : this(LinkedHashMap(o.mapValues { wrap(it.value) }))
    constructor(text: String) : this(parseObject(text))

    fun put(key: String, value: Any?): JSONObject { map[key] = value; return this }
    fun has(key: String) = map.containsKey(key)
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length() = map.size
    fun opt(key: String): Any? = map[key]

    private fun need(key: String): Any = map[key] ?: throw JSONException("no $key")

    fun getJSONObject(key: String): JSONObject = need(key) as? JSONObject ?: throw JSONException("$key is not an object")
    fun getJSONArray(key: String): JSONArray = need(key) as? JSONArray ?: throw JSONException("$key is not an array")
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun getString(key: String): String = need(key).let { if (it is String) it else it.toString() }
    fun optString(key: String, default: String = ""): String = map[key]?.let { if (it is String) it else it.toString() } ?: default
    fun getInt(key: String): Int = num(need(key), key).toInt()
    fun getLong(key: String): Long = num(need(key), key).toLong()
    fun getDouble(key: String): Double = num(need(key), key).toDouble()
    fun getBoolean(key: String): Boolean = need(key) as? Boolean ?: throw JSONException("$key is not a boolean")
    fun optInt(key: String, default: Int = 0): Int = runCatching { num(map[key], key).toInt() }.getOrDefault(default)
    fun optLong(key: String, default: Long = 0): Long = runCatching { num(map[key], key).toLong() }.getOrDefault(default)
    fun optDouble(key: String, default: Double = Double.NaN): Double = runCatching { num(map[key], key).toDouble() }.getOrDefault(default)
    fun optBoolean(key: String, default: Boolean = false): Boolean = map[key] as? Boolean ?: default

    override fun toString(): String = unwrap(this).toString()
}

class JSONArray private constructor(internal val list: MutableList<Any?>) {
    constructor() : this(mutableListOf())
    constructor(c: Collection<*>) : this(c.toMutableList())
    internal constructor(a: JsonArray) : this(a.map { wrap(it) }.toMutableList())
    constructor(text: String) : this(parseArray(text))

    fun length() = list.size
    fun put(v: Any?): JSONArray { list += v; return this }
    fun opt(i: Int): Any? = list.getOrNull(i)
    fun getJSONObject(i: Int): JSONObject = list.getOrNull(i) as? JSONObject ?: throw JSONException("[$i] is not an object")
    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject
    fun getString(i: Int): String = list.getOrNull(i)?.toString() ?: throw JSONException("no [$i]")
    fun getInt(i: Int): Int = num(list.getOrNull(i), i).toInt()
    fun getLong(i: Int): Long = num(list.getOrNull(i), i).toLong()
    fun getDouble(i: Int): Double = num(list.getOrNull(i), i).toDouble()

    override fun toString(): String = unwrap(this).toString()
}

/** Keeps JsonPrimitive's contentOrNull import used on every target. */
internal fun JsonPrimitive.text(): String? = contentOrNull

/** A GET request with a timeout of a few seconds; the body as text, or an exception on any failure or non-2xx answer. */
expect fun httpGet(url: String, userAgent: String): String
