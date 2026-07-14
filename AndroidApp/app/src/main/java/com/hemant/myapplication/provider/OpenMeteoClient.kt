package com.hemant.myapplication.provider

import com.hemant.myapplication.util.HttpUtil
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class OpenMeteoLocation(
    val name: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String? = null,
) {
    fun hasCoordinates(): Boolean = latitude != null && longitude != null
}

class OpenMeteoClient {
    @Throws(Exception::class)
    fun geocode(locationName: String): OpenMeteoLocation? {
        val query = URLEncoder.encode(locationName.trim(), "UTF-8")
        val language = Locale.getDefault().language.ifBlank { "en" }.lowercase(Locale.US)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=$language&format=json"
        val responseStr = HttpUtil.get(url)
        val json = JSONObject(responseStr)
        val results = json.optJSONArray("results")
        if (results == null || results.length() == 0) {
            return null
        }
        val first = results.optJSONObject(0) ?: return null
        return OpenMeteoLocation(
            name = first.optString("name", locationName),
            country = first.optString("country", ""),
            latitude = if (first.has("latitude")) first.optDouble("latitude") else null,
            longitude = if (first.has("longitude")) first.optDouble("longitude") else null,
            timezone = first.optString("timezone").ifBlank { null },
        )
    }

    @Throws(Exception::class)
    fun forecast(latitude: Double, longitude: Double): JSONObject {
        val latStr = String.format(Locale.US, "%.6f", latitude)
        val lonStr = String.format(Locale.US, "%.6f", longitude)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latStr&longitude=$lonStr" +
                "&current=temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_hours=24&forecast_days=7&timezone=auto"
        val responseStr = HttpUtil.get(url)
        return JSONObject(responseStr)
    }
}
