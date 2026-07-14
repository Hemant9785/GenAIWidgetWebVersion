package com.hemant.myapplication.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenMeteoResponseMapperTest {
    @Test
    fun mapsForecastFromTheCityCurrentHourAndUsesHourlyDayFlag() {
        val forecast = JSONObject(
            """
            {
              "timezone": "Asia/Kolkata",
              "utc_offset_seconds": 19800,
              "current": {
                "time": "2026-07-14T10:30",
                "temperature_2m": 27.6,
                "relative_humidity_2m": 72,
                "is_day": 1,
                "weather_code": 0,
                "wind_speed_10m": 12.4
              },
              "hourly": {
                "time": ["2026-07-14T10:00", "2026-07-14T11:00", "2026-07-14T12:00"],
                "temperature_2m": [27.0, 28.0, 29.0],
                "weather_code": [0, 0, 2],
                "precipitation_probability": [10, 20, 30],
                "is_day": [1, 0, 0]
              },
              "daily": {
                "time": ["2026-07-14", "2026-07-15"],
                "temperature_2m_max": [30.0, 31.0],
                "temperature_2m_min": [24.0, 25.0],
                "weather_code": [0, 2],
                "precipitation_probability_max": [40, 50]
              }
            }
            """.trimIndent(),
        )

        val weather = OpenMeteoResponseMapper.map(
            OpenMeteoLocation("Bengaluru", "India", 12.9716, 77.5946),
            forecast,
        ).getJSONObject("model").getJSONObject("weather")

        val hourly = weather.getJSONArray("hourlyItemsToday")
        assertEquals("11AM", hourly.getJSONObject(0).getString("title"))
        assertEquals("ms:clear_night", hourly.getJSONObject(0).getString("icon"))
        assertEquals("Today", weather.getJSONArray("dailyItemsWeek").getJSONObject(0).getString("title"))
        assertEquals("Tomorrow", weather.getJSONArray("dailyItemsWeek").getJSONObject(1).getString("title"))
    }
}
