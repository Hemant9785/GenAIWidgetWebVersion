package com.hemant.myapplication.domain

import com.hemant.myapplication.model.WidgetDocument
import com.hemant.myapplication.provider.OpenMeteoClient
import com.hemant.myapplication.provider.OpenMeteoLocation
import com.hemant.myapplication.provider.OpenMeteoResponseMapper
import org.json.JSONArray
import org.json.JSONObject

class WeatherDomainAdapter : DomainAdapter {
    override fun domainName(): String = "weather"
    override fun categoryName(): String = "weather"

    override fun getSystemPromptGuidance(): String {
        return """
            WEATHER WIDGET SPECIFICS:
            - The widget must display weather forecast details.
            - Resolve weather data using these exact paths:
              * Location Name: { "path": "/model/weather/location" }
              * Temp: { "path": "/model/weather/temperatureText" }
              * Condition: { "path": "/model/weather/condition" }
              * Condition Icon: { "ref": "sunny" } or a path: { "path": "/model/weather/conditionIcon", "fallbackRef": "ms:cloud" }
              * Humidity: { "path": "/model/weather/humidityText" }
              * Wind Speed: { "path": "/model/weather/windText" }
              * Rain Chance: { "path": "/model/weather/rainChanceText" }
              * Hourly Forecast Items: /model/weather/hourlyItemsToday (An array of objects containing 'title', 'value', 'icon')
              * Daily Forecast Items: /model/weather/dailyItemsWeek (An array of objects containing 'title', 'value', 'icon', 'subtitle')
            - Design a beautiful layout showing the current temperature, location, a weather icon representing the condition, and a row of hourly or daily forecast chips.
            - Make sure to populate the 'preview.mockData' object with realistic mock weather values for each of the paths so that it can be displayed during setup.
        """.trimIndent()
    }

    override fun getSkills(): List<SkillSummary> {
        return listOf(
            SkillSummary(
                path = "/skill/weather",
                name = "Weather Skill",
                description = "Generates weather forecast widget layouts displaying temperatures, wind, rain chance, and climate forecasts."
            )
        )
    }

    override fun getTools(): List<ToolSummary> {
        return listOf(
            ToolSummary(
                path = "/tool/weather/geocode",
                name = "Geocoding API",
                description = "Resolves a location name (city/country) to its latitude, longitude, and timezone details."
            ),
            ToolSummary(
                path = "/tool/weather/forecast",
                name = "Weather Forecast API",
                description = "Fetches the 7-day weather forecast (hourly temperatures, condition codes, rain probabilities) for coordinates."
            )
        )
    }

    override fun getAssets(): List<AssetSummary> {
        return listOf(
            AssetSummary(
                path = "/asset/weather/icons",
                name = "Weather Icons",
                description = "Standard dynamic icons representing weather conditions (sunny, rainy, partly cloudy, etc.)."
            )
        )
    }

    override fun readTool(toolPath: String): JSONObject {
        return when (toolPath) {
            "/tool/weather/geocode" -> JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("location", JSONObject().put("type", "string").put("description", "The city or location name"))
                )
                .put("required", JSONArray(listOf("location")))
            "/tool/weather/forecast" -> JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("latitude", JSONObject().put("type", "number").put("description", "Latitude coordinates"))
                    .put("longitude", JSONObject().put("type", "number").put("description", "Longitude coordinates"))
                )
                .put("required", JSONArray(listOf("latitude", "longitude")))
            else -> JSONObject()
        }
    }

    override suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject {
        val client = OpenMeteoClient()
        return when (toolPath) {
            "/tool/weather/geocode" -> {
                val locationName = params.getString("location")
                val loc = client.geocode(locationName)
                if (loc != null) {
                    JSONObject()
                        .put("latitude", loc.latitude ?: 48.8566)
                        .put("longitude", loc.longitude ?: 2.3522)
                        .put("name", loc.name)
                        .put("country", loc.country)
                } else {
                    JSONObject().put("error", "Location not found")
                }
            }
            "/tool/weather/forecast" -> {
                val lat = params.getDouble("latitude")
                val lon = params.getDouble("longitude")
                val forecastJson = client.forecast(lat, lon)
                
                // Construct location reference dynamically
                val name = params.optString("name", "")
                val country = params.optString("country", "")
                val location = OpenMeteoLocation(name, country, lat, lon)
                
                OpenMeteoResponseMapper.map(location, forecastJson)
            }
            else -> JSONObject().put("error", "Unknown tool path $toolPath")
        }
    }
}
