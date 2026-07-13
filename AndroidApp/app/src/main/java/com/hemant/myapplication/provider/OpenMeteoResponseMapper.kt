package com.hemant.myapplication.provider

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object OpenMeteoResponseMapper {
    fun map(location: OpenMeteoLocation, forecast: JSONObject): JSONObject {
        val current = forecast.optJSONObject("current")
        val hourly = forecast.optJSONObject("hourly")
        val daily = forecast.optJSONObject("daily")
        
        val temp = current?.optDouble("temperature_2m", 0.0) ?: 0.0
        val humidity = current?.optInt("relative_humidity_2m", 0) ?: 0
        val isDay = current?.optInt("is_day", 1) == 1
        val code = current?.optInt("weather_code", 0) ?: 0
        val windSpeed = current?.optDouble("wind_speed_10m", 0.0) ?: 0.0
        
        val condition = condition(code)
        val conditionIcon = iconRef(code, isDay)
        
        // Find rain chance max for today from daily
        val rainChanceMax = if (daily != null) {
            val probabilities = daily.optJSONArray("precipitation_probability_max")
            if (probabilities != null && probabilities.length() > 0) probabilities.optInt(0, 0) else 0
        } else {
            0
        }

        val output = JSONObject()
        val weatherOutput = JSONObject()
        
        weatherOutput.put("location", location.name)
        weatherOutput.put("country", location.country)
        weatherOutput.put("temperatureText", "${Math.round(temp)}°C")
        weatherOutput.put("condition", condition)
        weatherOutput.put("conditionIcon", conditionIcon)
        weatherOutput.put("humidityText", "$humidity%")
        weatherOutput.put("windText", "${Math.round(windSpeed)} km/h")
        weatherOutput.put("rainChanceText", "$rainChanceMax%")
        
        // Map hourly items
        if (hourly != null) {
            val hourlyItems = JSONArray()
            val times = hourly.optJSONArray("time")
            val temps = hourly.optJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val rainProbs = hourly.optJSONArray("precipitation_probability")
            
            val limit = minOf(times?.length() ?: 0, 8) // Limit to first 8 hours
            for (i in 0 until limit) {
                val rawTime = times?.optString(i).orEmpty()
                val hourLabel = formatHour(rawTime)
                val hourlyTemp = temps?.optDouble(i, 0.0) ?: 0.0
                val hourlyCode = codes?.optInt(i, 0) ?: 0
                val hourlyRain = rainProbs?.optInt(i, 0) ?: 0
                
                val item = JSONObject()
                item.put("title", hourLabel)
                item.put("value", "${Math.round(hourlyTemp)}°C")
                item.put("icon", iconRef(hourlyCode, true))
                item.put("subtitle", "Rain $hourlyRain%")
                hourlyItems.put(item)
            }
            weatherOutput.put("hourlyItemsToday", hourlyItems)
        }
        
        // Map daily items
        if (daily != null) {
            val dailyItems = JSONArray()
            val times = daily.optJSONArray("time")
            val maxTemps = daily.optJSONArray("temperature_2m_max")
            val minTemps = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            val rainProbs = daily.optJSONArray("precipitation_probability_max")
            
            val limit = minOf(times?.length() ?: 0, 7)
            for (i in 0 until limit) {
                val rawDate = times?.optString(i).orEmpty()
                val dayLabel = formatDay(rawDate, i)
                val maxT = maxTemps?.optDouble(i, 0.0) ?: 0.0
                val minT = minTemps?.optDouble(i, 0.0) ?: 0.0
                val dailyCode = codes?.optInt(i, 0) ?: 0
                val dailyRain = rainProbs?.optInt(i, 0) ?: 0
                
                val item = JSONObject()
                item.put("title", dayLabel)
                item.put("value", "${Math.round(maxT)}°C")
                item.put("icon", iconRef(dailyCode, true))
                item.put("subtitle", "L ${Math.round(minT)}°C  Rain $dailyRain%")
                dailyItems.put(item)
            }
            weatherOutput.put("dailyItemsWeek", dailyItems)
        }
        
        output.put("model", JSONObject().put("weather", weatherOutput))
        return output
    }
    
    private fun condition(code: Int): String {
        return when (code) {
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
    }
    
    private fun iconRef(code: Int, day: Boolean): String {
        return when (code) {
            0 -> if (day) "ms:sunny" else "ms:clear_night"
            1, 2 -> if (day) "ms:partly_cloudy_day" else "ms:partly_cloudy_night"
            3 -> "ms:cloud"
            45, 48 -> "ms:foggy"
            in 51..67, in 80..82 -> "ms:rainy"
            in 71..77 -> "ms:weather_snowy"
            in 95..99 -> "ms:thunderstorm"
            else -> "ms:sunny"
        }
    }
    
    private fun formatHour(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        val hourPart = timeStr.substringAfter('T', "").take(5)
        if (hourPart.isEmpty()) return timeStr
        val hour = hourPart.substringBefore(':').toIntOrNull() ?: return timeStr
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = (hour % 12).let { if (it == 0) 12 else it }
        return "$hour12$suffix"
    }
    
    private fun formatDay(dateStr: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr) ?: return dateStr
            val outSdf = java.text.SimpleDateFormat("EEE", Locale.US)
            outSdf.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }
}
