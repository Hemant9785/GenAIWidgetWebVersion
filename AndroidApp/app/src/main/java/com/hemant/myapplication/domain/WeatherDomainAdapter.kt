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
            - If the user explicitly names a city, area, country, or postal code, create an INTERNAL `location` variable using `/tool/weather/geocode` and pass its values to `/tool/weather/forecast`.
            - If the user explicitly provides latitude and longitude, create an INTERNAL static `location` variable containing those exact coordinates and pass them directly to `/tool/weather/forecast`; do not geocode coordinates.
            - If weather is requested without a user-provided location, create an INTERNAL `currentLocation` variable using the global `/tool/default/current-location` tool. Pass `{{currentLocation.latitude}}`, `{{currentLocation.longitude}}`, and `{{currentLocation.label}}` to `/tool/weather/forecast`. Never use geocoding in this case.
            - Location helper variables are support data only: `exposure` must be `internal` and they must never be displayed.
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
            - Use only the supplied runtime weather data. Do not create preview weather values or present unavailable data as live.
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
                    .put("name", JSONObject().put("type", "string").put("description", "Display label for the resolved location"))
                    .put("country", JSONObject().put("type", "string").put("description", "Country for a named location"))
                )
                .put("required", JSONArray(listOf("latitude", "longitude")))
            else -> JSONObject()
        }
    }

    override fun readToolResponseSchema(toolPath: String): JSONObject {
        return when (toolPath) {
            "/tool/weather/geocode" -> JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("latitude", JSONObject().put("type", "number").put("description", "Latitude coordinate"))
                    .put("longitude", JSONObject().put("type", "number").put("description", "Longitude coordinate"))
                    .put("name", JSONObject().put("type", "string").put("description", "City name"))
                    .put("country", JSONObject().put("type", "string").put("description", "Country name"))
                    .put("timezone", JSONObject().put("type", "string").put("description", "IANA timezone"))
                )
            "/tool/weather/forecast" -> JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("model", JSONObject().put("type", "object").put("properties", JSONObject()
                        .put("weather", JSONObject().put("type", "object").put("properties", JSONObject()
                            .put("location", JSONObject().put("type", "string"))
                            .put("country", JSONObject().put("type", "string"))
                            .put("temperatureText", JSONObject().put("type", "string"))
                            .put("condition", JSONObject().put("type", "string"))
                            .put("conditionIcon", JSONObject().put("type", "string"))
                            .put("humidityText", JSONObject().put("type", "string"))
                            .put("windText", JSONObject().put("type", "string"))
                            .put("rainChanceText", JSONObject().put("type", "string"))
                            .put("timezone", JSONObject().put("type", "string"))
                            .put("updatedAt", JSONObject().put("type", "string"))
                            .put("hourlyItemsToday", JSONObject().put("type", "array"))
                            .put("dailyItemsWeek", JSONObject().put("type", "array"))
                        ))
                    ))
                )
            else -> JSONObject()
        }
    }

    override suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject {
        val client = OpenMeteoClient()
        return when (toolPath) {
            "/tool/weather/geocode" -> {
                val locationName = params.getString("location")
                val loc = client.geocode(locationName)
                if (loc != null && loc.hasCoordinates()) {
                    JSONObject()
                        .put("latitude", loc.latitude)
                        .put("longitude", loc.longitude)
                        .put("name", loc.name)
                        .put("country", loc.country)
                        .put("timezone", loc.timezone)
                } else {
                    JSONObject().put("error", "Location not found or has no coordinates")
                }
            }
            "/tool/weather/forecast" -> {
                val lat = params.getDouble("latitude")
                val lon = params.getDouble("longitude")
                val forecastJson = client.forecast(lat, lon)
                if (forecastJson.optBoolean("error", false)) {
                    return JSONObject().put("error", forecastJson.optString("reason", "Open-Meteo forecast request failed"))
                }
                
                // The label is either the geocoded place or the truthful default
                // label returned by the private current-location tool.
                val name = params.optString("name", "Current location")
                val country = params.optString("country", "")
                val location = OpenMeteoLocation(name, country, lat, lon, forecastJson.optString("timezone"))
                
                OpenMeteoResponseMapper.map(location, forecastJson)
            }
            else -> JSONObject().put("error", "Unknown tool path $toolPath")
        }
    }
}
