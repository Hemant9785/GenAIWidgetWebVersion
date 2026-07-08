# Location Skill

This skill describes location resolution for weather and other location-based queries.

## City Geocoding

When a user mentions a city or place name, resolve it to coordinates using the geocoding tool.

### Resolution Rules
1. If user says a city name (e.g., "Bangalore", "Delhi", "New York"):
   - Use the geocode tool with the city name
   - Pick the first result (highest population/relevance)
   - Extract: latitude, longitude, timezone, country, display name

2. If user says "near me" or "outside" or doesn't specify location:
   - Use the current-location tool to get default coordinates
   - This returns a configured default location

3. If location is ambiguous (e.g., "Springfield" exists in many states):
   - The geocode tool returns multiple results
   - Pick the one with highest population unless user provides more context
   - Store the full location name for display (e.g., "Springfield, Illinois, US")

### Location Variable Structure
The resolved location should be stored as:
```json
{
  "variable_name": "location",
  "variable_type": "object",
  "source": {
    "type": "tool",
    "tool_path": "/tool/weather/geocode",
    "parameters": {
      "name": "Bangalore",
      "count": 1
    }
  }
}
```

## Timezone Handling

- Open-Meteo Forecast API requires timezone for daily data
- The geocode result includes timezone (IANA format like "Asia/Kolkata")
- Always pass the location's timezone to weather API calls
- Use "auto" as fallback if timezone is unknown

## Coordinate Precision

- Latitude: -90 to 90
- Longitude: -180 to 180
- 2 decimal places is sufficient for city-level weather
- Coordinates from geocoding API are already at appropriate precision
