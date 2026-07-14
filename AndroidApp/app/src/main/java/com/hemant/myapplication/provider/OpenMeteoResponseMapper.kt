package com.hemant.myapplication.provider

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Maps Open-Meteo's location-local response into renderer-friendly weather data. */
object OpenMeteoResponseMapper {
    private const val MAX_HOURLY_ITEMS = 8
    private const val MAX_DAILY_ITEMS = 7

    fun map(location: OpenMeteoLocation, forecast: JSONObject): JSONObject {
        val current = forecast.optJSONObject("current")
        val hourly = forecast.optJSONObject("hourly")
        val daily = forecast.optJSONObject("daily")
        val timeZone = resolveTimeZone(forecast.optString("timezone"))
        val currentTime = parseDateTime(current?.optString("time").orEmpty(), timeZone) ?: Date()

        val temperature = current?.optDouble("temperature_2m", 0.0) ?: 0.0
        val humidity = current?.optInt("relative_humidity_2m", 0) ?: 0
        val currentIsDay = current?.optInt("is_day", 1) == 1
        val weatherCode = current?.optInt("weather_code", 0) ?: 0
        val windSpeed = current?.optDouble("wind_speed_10m", 0.0) ?: 0.0
        val dailyIndex = currentDailyIndex(daily?.optJSONArray("time"), currentTime, timeZone)
        val rainChanceMax = daily
            ?.optJSONArray("precipitation_probability_max")
            ?.optInt(dailyIndex, 0)
            ?: 0

        val weatherOutput = JSONObject()
            .put("location", location.name.ifBlank { "Current location" })
            .put("country", location.country)
            .put("timezone", forecast.optString("timezone"))
            .put("updatedAt", current?.optString("time"))
            .put("temperatureText", "${Math.round(temperature)}°C")
            .put("condition", condition(weatherCode))
            .put("conditionIcon", iconRef(weatherCode, currentIsDay))
            .put("humidityText", "$humidity%")
            .put("windText", "${Math.round(windSpeed)} km/h")
            .put("rainChanceText", "$rainChanceMax%")

        weatherOutput.put("hourlyItemsToday", mapHourly(hourly, currentTime, timeZone, currentIsDay))
        weatherOutput.put("dailyItemsWeek", mapDaily(daily, currentTime, timeZone))
        return JSONObject().put("model", JSONObject().put("weather", weatherOutput))
    }

    private fun mapHourly(
        hourly: JSONObject?,
        currentTime: Date,
        timeZone: TimeZone,
        fallbackIsDay: Boolean,
    ): JSONArray {
        val output = JSONArray()
        val times = hourly?.optJSONArray("time") ?: return output
        val temperatures = hourly.optJSONArray("temperature_2m")
        val weatherCodes = hourly.optJSONArray("weather_code")
        val rainProbabilities = hourly.optJSONArray("precipitation_probability")
        val dayFlags = hourly.optJSONArray("is_day")
        val startIndex = firstCurrentOrFutureHour(times, currentTime, timeZone)
        val endExclusive = minOf(times.length(), startIndex + MAX_HOURLY_ITEMS)

        for (index in startIndex until endExclusive) {
            val rawTime = times.optString(index)
            val isDay = if (dayFlags == null) fallbackIsDay else dayFlags.optInt(index, if (fallbackIsDay) 1 else 0) == 1
            output.put(
                JSONObject()
                    .put("title", formatHour(rawTime))
                    .put("value", "${Math.round(temperatures?.optDouble(index, 0.0) ?: 0.0)}°C")
                    .put("icon", iconRef(weatherCodes?.optInt(index, 0) ?: 0, isDay))
                    .put("subtitle", "Rain ${rainProbabilities?.optInt(index, 0) ?: 0}%"),
            )
        }
        return output
    }

    private fun mapDaily(daily: JSONObject?, currentTime: Date, timeZone: TimeZone): JSONArray {
        val output = JSONArray()
        val times = daily?.optJSONArray("time") ?: return output
        val maxTemperatures = daily.optJSONArray("temperature_2m_max")
        val minTemperatures = daily.optJSONArray("temperature_2m_min")
        val weatherCodes = daily.optJSONArray("weather_code")
        val rainProbabilities = daily.optJSONArray("precipitation_probability_max")

        for (index in 0 until minOf(times.length(), MAX_DAILY_ITEMS)) {
            output.put(
                JSONObject()
                    .put("title", formatDay(times.optString(index), currentTime, timeZone))
                    .put("value", "${Math.round(maxTemperatures?.optDouble(index, 0.0) ?: 0.0)}°C")
                    .put("icon", iconRef(weatherCodes?.optInt(index, 0) ?: 0, true))
                    .put(
                        "subtitle",
                        "L ${Math.round(minTemperatures?.optDouble(index, 0.0) ?: 0.0)}°C  Rain ${rainProbabilities?.optInt(index, 0) ?: 0}%",
                    ),
            )
        }
        return output
    }

    private fun firstCurrentOrFutureHour(times: JSONArray, currentTime: Date, timeZone: TimeZone): Int {
        for (index in 0 until times.length()) {
            val hourlyTime = parseDateTime(times.optString(index), timeZone) ?: continue
            if (!hourlyTime.before(currentTime)) return index
        }
        return 0
    }

    private fun currentDailyIndex(times: JSONArray?, currentTime: Date, timeZone: TimeZone): Int {
        if (times == null) return 0
        val currentDay = dateKey(currentTime, timeZone)
        for (index in 0 until times.length()) {
            if (times.optString(index) == currentDay) return index
        }
        return 0
    }

    private fun condition(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67, in 80..82 -> "Rain"
        in 71..77 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Clear"
    }

    private fun iconRef(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "ms:sunny" else "ms:clear_night"
        1, 2 -> if (day) "ms:partly_cloudy_day" else "ms:partly_cloudy_night"
        3 -> "ms:cloud"
        45, 48 -> "ms:foggy"
        in 51..67, in 80..82 -> "ms:rainy"
        in 71..77 -> "ms:weather_snowy"
        in 95..99 -> "ms:thunderstorm"
        else -> if (day) "ms:sunny" else "ms:clear_night"
    }

    private fun formatHour(time: String): String {
        val hour = time.substringAfter('T', "").substringBefore(':').toIntOrNull() ?: return time
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = (hour % 12).let { if (it == 0) 12 else it }
        return "$hour12$suffix"
    }

    private fun formatDay(date: String, currentTime: Date, timeZone: TimeZone): String {
        val today = dateKey(currentTime, timeZone)
        if (date == today) return "Today"
        val tomorrow = Calendar.getInstance(timeZone).apply {
            time = currentTime
            add(Calendar.DATE, 1)
        }
        if (date == dateKey(tomorrow.time, timeZone)) return "Tomorrow"
        val parsedDate = parseDate(date, timeZone) ?: return date
        return SimpleDateFormat("EEE", Locale.getDefault()).apply { this.timeZone = timeZone }.format(parsedDate)
    }

    private fun resolveTimeZone(identifier: String): TimeZone =
        TimeZone.getTimeZone(identifier.ifBlank { "GMT" })

    private fun parseDateTime(value: String, timeZone: TimeZone): Date? =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                isLenient = false
                this.timeZone = timeZone
            }.parse(value)
        }.getOrNull()

    private fun parseDate(value: String, timeZone: TimeZone): Date? =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                this.timeZone = timeZone
            }.parse(value)
        }.getOrNull()

    private fun dateKey(date: Date, timeZone: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }.format(date)
}
