package com.jesjobom.ararai.weather

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.KnowledgeHttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

enum class WeatherPeriod { Now, Today, Tomorrow, NextSevenDays }

data class WeatherCoordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
    }
}

data class WeatherRequest(val location: String?, val period: WeatherPeriod)

enum class WeatherFailure {
    InvalidLocation,
    AmbiguousLocation,
    LocationRequired,
    ProviderUnavailable,
    MalformedResponse,
}

sealed interface WeatherResult {
    data class Success(val payload: JsonObject) : WeatherResult
    data class Failure(val reason: WeatherFailure) : WeatherResult
}

/** Bounded provider adapter. The model and widget runtime never choose provider URLs or variables. */
class OpenMeteoWeatherTool(
    private val transport: KnowledgeHttpTransport = UrlConnectionOpenMeteoTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun fetch(request: WeatherRequest): WeatherResult {
        val place = request.location?.trim()
        return when {
            place.isNullOrEmpty() -> WeatherResult.Failure(WeatherFailure.LocationRequired)
            invalidPlace(place) -> WeatherResult.Failure(WeatherFailure.InvalidLocation)
            else -> fetchNamedPlace(place, request.period)
        }
    }

    private suspend fun fetchNamedPlace(place: String, period: WeatherPeriod): WeatherResult = try {
        withTimeout(15_000L) {
            val location = geocode(place)
            when (location) {
                is LocationOutcome.Failure -> WeatherResult.Failure(location.reason)
                is LocationOutcome.Found -> fetchForecast(location, period)
            }
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) {
            WeatherResult.Failure(WeatherFailure.ProviderUnavailable)
        } else {
            throw cancelled
        }
    } catch (_: Exception) {
        WeatherResult.Failure(WeatherFailure.ProviderUnavailable)
    }

    private fun invalidPlace(place: String): Boolean {
        val invalidText = place.isEmpty() || place.length > 120
        return invalidText || place.any(Char::isISOControl)
    }

    private suspend fun fetchForecast(location: LocationOutcome.Found, period: WeatherPeriod): WeatherResult {
        val response = transport.get(forecastUrl(location.coordinates, period))
        val data = response.json() ?: return WeatherResult.Failure(WeatherFailure.ProviderUnavailable)
        return parseForecast(data, location.name, period)
    }

    private fun parseForecast(data: JsonObject, location: String, period: WeatherPeriod): WeatherResult {
        val current = data.objectField("current")
        val dayCount = if (period == WeatherPeriod.NextSevenDays) 7 else 1
        val dayStart = if (period == WeatherPeriod.Tomorrow) 1 else 0
        val daily = data.objectField("daily")
        val days = daily?.records((dayStart until dayStart + dayCount).toList(), DAILY_FIELDS)
        val dates = daily?.get("time")?.takeIf { it.isJsonArray }?.asJsonArray
        val targetDate = dates?.takeIf { it.size() > dayStart }?.get(dayStart)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val hours = if (period == WeatherPeriod.Today || period == WeatherPeriod.Tomorrow) {
            data.objectField("hourly")?.recordsForDate(targetDate, HOURLY_FIELDS)
        } else {
            null
        }
        val valid = when (period) {
            WeatherPeriod.Now -> current != null
            WeatherPeriod.Today, WeatherPeriod.Tomorrow -> days != null && hours != null
            WeatherPeriod.NextSevenDays -> days != null
        }
        if (!valid) return WeatherResult.Failure(WeatherFailure.MalformedResponse)
        return WeatherResult.Success(buildPayload(data, location, period, current, days, hours))
    }

    private fun buildPayload(
        data: JsonObject,
        location: String,
        period: WeatherPeriod,
        current: JsonObject?,
        days: JsonArray?,
        hours: JsonArray?,
    ): JsonObject = JsonObject().apply {
        addProperty("kind", "success")
        addProperty("source", "Open-Meteo")
        addProperty("sourceUrl", "https://open-meteo.com/")
        addProperty("retrievedAtMillis", clock())
        addProperty("location", location)
        addProperty("timezone", data.stringField("timezone") ?: "GMT")
        addProperty("period", period.wireName())
        current?.let { add("current", it.project(CURRENT_FIELDS)) }
        days?.let { add("days", it) }
        hours?.let { add("hours", it) }
    }

    private fun WeatherPeriod.wireName(): String = when (this) {
        WeatherPeriod.Now -> "now"
        WeatherPeriod.Today -> "today"
        WeatherPeriod.Tomorrow -> "tomorrow"
        WeatherPeriod.NextSevenDays -> "next_7_days"
    }

    @Suppress("ReturnCount")
    private suspend fun geocode(place: String): LocationOutcome {
        val parts = place.split(',').map(String::trim)
        val city = parts.first()
        val qualifiers = parts.drop(1)
        if (parts.any(String::isEmpty)) {
            return LocationOutcome.Failure(WeatherFailure.InvalidLocation)
        }
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${encode(city)}&count=10&format=json"
        val response = transport.get(url)
        val data = response.json() ?: return LocationOutcome.Failure(WeatherFailure.ProviderUnavailable)
        val entries = data.get("results")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return LocationOutcome.Failure(WeatherFailure.InvalidLocation)
        val matches = entries.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val lat = obj.doubleField("latitude") ?: return@mapNotNull null
            val lon = obj.doubleField("longitude") ?: return@mapNotNull null
            val name = obj.stringField("name") ?: return@mapNotNull null
            val country = obj.stringField("country_code") ?: ""
            val found = runCatching {
                LocationOutcome.Found(WeatherCoordinates(lat, lon), "$name, $country")
            }.getOrNull()
            found?.takeIf { matchesQualifiers(obj, qualifiers) }?.let { match ->
                GeocodedLocation(match, obj.longField("population"))
            }
        }
        if (matches.isEmpty()) return LocationOutcome.Failure(WeatherFailure.InvalidLocation)
        if (isAmbiguous(city, matches)) {
            return LocationOutcome.Failure(WeatherFailure.AmbiguousLocation)
        }
        return matches.first().location
    }

    private fun isAmbiguous(place: String, matches: List<GeocodedLocation>): Boolean {
        val sameName = matches.filter { match ->
            normalizedPlace(match.location.name.substringBefore(',')) == normalizedPlace(place)
        }
        val first = matches.first()
        val largestAlternative = sameName.drop(1).maxOfOrNull { it.population ?: 0L } ?: 0L
        // The provider ranks results by relevance. A clearly dominant major city
        // need not be rejected because much smaller namesakes also exist.
        return sameName.size >= 2 && (
            first != sameName.first() || first.population == null || first.population < 100_000L ||
                first.population / 10L < maxOf(1L, largestAlternative)
            )
    }

    private fun matchesQualifiers(candidate: JsonObject, qualifiers: List<String>): Boolean {
        val countryCode = candidate.stringField("country_code")
        val admin1 = candidate.stringField("admin1")?.let(::normalizedPlace)
        val names = listOf(
            countryCode,
            candidate.stringField("country"),
            candidate.stringField("admin1"),
            candidate.stringField("admin2"),
            countryCode?.takeIf { it.length == 2 && it.all(Char::isLetter) }
                ?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(PORTUGUESE_BRAZIL) },
        ).filterNotNull().map(::normalizedPlace)
        return qualifiers.all { qualifier ->
            val normalized = normalizedPlace(qualifier)
            normalized in names || (
                countryCode == "BR" && admin1 != null &&
                    BRAZILIAN_STATE_NAMES[normalized]?.any { normalizedPlace(it) == admin1 } == true
                )
        }
    }

    private fun forecastUrl(coordinates: WeatherCoordinates, period: WeatherPeriod): String {
        val shared = "latitude=${coordinates.latitude}&longitude=${coordinates.longitude}&timezone=auto" +
            "&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"
        val current = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "precipitation,weather_code,wind_speed_10m"
        val daily = "weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_sum,precipitation_probability_max"
        val hourly = "temperature_2m,precipitation_probability,precipitation,weather_code"
        val fields = when (period) {
            WeatherPeriod.Now -> "current=$current&forecast_days=1"
            WeatherPeriod.Today -> "current=$current&daily=$daily&hourly=$hourly&forecast_days=1"
            WeatherPeriod.Tomorrow -> "daily=$daily&hourly=$hourly&forecast_days=2"
            WeatherPeriod.NextSevenDays -> "daily=$daily&forecast_days=7"
        }
        return "https://api.open-meteo.com/v1/forecast?$shared&$fields"
    }

    private sealed interface LocationOutcome {
        data class Found(val coordinates: WeatherCoordinates, val name: String) : LocationOutcome
        data class Failure(val reason: WeatherFailure) : LocationOutcome
    }

    private data class GeocodedLocation(val location: LocationOutcome.Found, val population: Long?)

    private companion object {
        val CURRENT_FIELDS = listOf(
            "time" to "time",
            "temperature_2m" to "temperatureC",
            "relative_humidity_2m" to "humidityPercent",
            "apparent_temperature" to "feelsLikeC",
            "precipitation" to "precipitationMm",
            "weather_code" to "weatherCode",
            "wind_speed_10m" to "windSpeedKmh",
        )
        val DAILY_FIELDS = listOf(
            "time" to "date",
            "weather_code" to "weatherCode",
            "temperature_2m_max" to "temperatureMaxC",
            "temperature_2m_min" to "temperatureMinC",
            "precipitation_sum" to "precipitationSumMm",
            "precipitation_probability_max" to "precipitationProbabilityMaxPercent",
        )
        val HOURLY_FIELDS = listOf(
            "time" to "time",
            "temperature_2m" to "temperatureC",
            "precipitation_probability" to "precipitationProbabilityPercent",
            "precipitation" to "precipitationMm",
            "weather_code" to "weatherCode",
        )
    }
}

private fun com.jesjobom.ararai.knowledge.KnowledgeHttpResponse.json(): JsonObject? = takeIf {
    it.status == 200 && it.body.size <= 128_000 &&
        it.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true
}?.let { response ->
    runCatching { JsonParser.parseString(String(response.body, StandardCharsets.UTF_8)).asJsonObject }.getOrNull()
}

private fun JsonObject.objectField(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.project(fields: List<Pair<String, String>>): JsonObject = JsonObject().also { result ->
    fields.forEach { (from, to) -> get(from)?.let { result.add(to, it) } }
}

@Suppress("ReturnCount")
private fun JsonObject.recordsForDate(date: String?, fields: List<Pair<String, String>>): JsonArray? {
    if (date == null) return null
    val times = get("time")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    val indices = (0 until times.size()).filter { index ->
        val time = times[index]
        time.isJsonPrimitive && time.asJsonPrimitive.isString && time.asString.startsWith("${date}T")
    }
    if (indices.size !in 23..25) return null
    return records(indices, fields)
}

@Suppress("ReturnCount")
private fun JsonObject.records(indices: List<Int>, fields: List<Pair<String, String>>): JsonArray? {
    val time = get("time")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    if (indices.isEmpty() || indices.any { it !in 0 until time.size() }) return null
    if (fields.any { (from, _) ->
            get(from)?.let { field -> !field.isJsonArray || indices.any { it >= field.asJsonArray.size() } } == true
        }
    ) {
        return null
    }
    return JsonArray().also { result ->
        for (index in indices) {
            val item = JsonObject()
            fields.forEach { (from, to) ->
                get(from)?.takeIf { it.isJsonArray }?.asJsonArray?.get(index)?.let { item.add(to, it) }
            }
            result.add(item)
        }
    }
}

private fun JsonObject.stringField(name: String): String? {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    return value?.asString
}

private fun JsonObject.doubleField(name: String): Double? {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    return value?.asDouble
}

private fun JsonObject.longField(name: String): Long? {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    return value?.let { runCatching { it.asLong }.getOrNull() }?.takeIf { it >= 0 }
}

private fun normalizedPlace(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(DIACRITICS, "")
    .lowercase(Locale.ROOT)

private val DIACRITICS = Regex("\\p{M}+")
private val PORTUGUESE_BRAZIL = Locale.forLanguageTag("pt-BR")

// Open-Meteo returns full administrative names, while Brazilian requests often use ISO 3166-2 state codes.
private val BRAZILIAN_STATE_NAMES = mapOf(
    "ac" to listOf("Acre"),
    "al" to listOf("Alagoas"),
    "ap" to listOf("Amapá"),
    "am" to listOf("Amazonas"),
    "ba" to listOf("Bahia"),
    "ce" to listOf("Ceará"),
    "df" to listOf("Federal District", "Distrito Federal"),
    "es" to listOf("Espírito Santo"),
    "go" to listOf("Goiás"),
    "ma" to listOf("Maranhão"),
    "mt" to listOf("Mato Grosso"),
    "ms" to listOf("Mato Grosso do Sul"),
    "mg" to listOf("Minas Gerais"),
    "pa" to listOf("Pará"),
    "pb" to listOf("Paraíba"),
    "pr" to listOf("Paraná"),
    "pe" to listOf("Pernambuco"),
    "pi" to listOf("Piauí"),
    "rj" to listOf("Rio de Janeiro"),
    "rn" to listOf("Rio Grande do Norte"),
    "rs" to listOf("Rio Grande do Sul"),
    "ro" to listOf("Rondônia"),
    "rr" to listOf("Roraima"),
    "sc" to listOf("Santa Catarina"),
    "sp" to listOf("São Paulo"),
    "se" to listOf("Sergipe"),
    "to" to listOf("Tocantins"),
)

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
