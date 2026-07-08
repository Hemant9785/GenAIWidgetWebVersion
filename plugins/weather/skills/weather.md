# Weather Skill

This skill describes weather widget capabilities for the GenUI platform.

## Supported Widget Types

### Current Weather
Display real-time weather conditions for a location.
- Temperature (current, feels-like)
- Weather condition with icon (clear, cloudy, rain, snow, etc.)
- Humidity percentage
- Wind speed and direction
- Pressure
- Visibility
- UV index

### Hourly Forecast
Display hour-by-hour weather predictions.
- Temperature trend over 24-48 hours
- Precipitation probability per hour
- Weather condition icons per hour
- Wind speed changes
- Best shown as horizontal scrollable list or mini chart

### Daily Forecast (7-Day / 14-Day)
Display day-by-day weather summary.
- High and low temperatures per day
- Weather condition and icon per day
- Precipitation probability per day
- Wind speed per day
- Best shown as vertical list with day labels

### Rain Probability
Focused view on precipitation chances.
- Hourly rain probability as progress bars or chart
- Daily precipitation sum
- Snow vs rain distinction
- Precipitation type (rain, showers, drizzle, snow)

### Air Quality (AQI)
Display air quality information.
- US AQI or European AQI index value
- PM2.5 and PM10 concentrations
- Ozone, NO2, SO2 levels
- AQI category label (Good, Moderate, Unhealthy, etc.)

### UV Index
Display ultraviolet radiation levels.
- Current UV index value
- Daily max UV index
- UV category (Low, Moderate, High, Very High, Extreme)

### Wind
Focused wind information display.
- Wind speed (current and max)
- Wind direction with compass indicator
- Wind gusts

### Humidity
Display moisture information.
- Relative humidity percentage
- Dewpoint temperature
- Apparent temperature (feels like)

### Historical Weather
Display past weather data.
- Compare today vs same day last year
- Temperature trends over past weeks/months
- Historical averages

## Weather Code Mapping (WMO)

The Open-Meteo API returns WMO weather codes. Map these to conditions and icons:

| Code | Condition | Icon Key |
|------|-----------|----------|
| 0 | Clear sky | clear_day / clear_night |
| 1 | Mainly clear | mostly_clear_day / mostly_clear_night |
| 2 | Partly cloudy | partly_cloudy_day / partly_cloudy_night |
| 3 | Overcast | cloudy |
| 45 | Fog | fog |
| 48 | Depositing rime fog | fog |
| 51 | Light drizzle | drizzle |
| 53 | Moderate drizzle | drizzle |
| 55 | Dense drizzle | drizzle |
| 56 | Light freezing drizzle | freezing_drizzle |
| 57 | Dense freezing drizzle | freezing_drizzle |
| 61 | Slight rain | rain_light |
| 63 | Moderate rain | rain |
| 65 | Heavy rain | rain_heavy |
| 66 | Light freezing rain | freezing_rain |
| 67 | Heavy freezing rain | freezing_rain |
| 71 | Slight snow | snow_light |
| 73 | Moderate snow | snow |
| 75 | Heavy snow | snow_heavy |
| 77 | Snow grains | snow_grains |
| 80 | Slight rain showers | showers_light |
| 81 | Moderate rain showers | showers |
| 82 | Violent rain showers | showers_heavy |
| 85 | Slight snow showers | snow_showers_light |
| 86 | Heavy snow showers | snow_showers_heavy |
| 95 | Thunderstorm | thunderstorm |
| 96 | Thunderstorm with slight hail | thunderstorm_hail |
| 99 | Thunderstorm with heavy hail | thunderstorm_hail |

## Variable Naming Conventions

When planning variables, use these semantic names:
- `currentWeather` — current conditions object
- `hourlyForecast` — hourly forecast data array
- `dailyForecast` — daily forecast data array
- `location` — resolved location with lat, lon, name, timezone
- `airQuality` — AQI and pollutant data
- `uvIndex` — UV index data
- `historicalWeather` — past weather data

## Data Source

All weather data comes from Open-Meteo (free, no API key required).
Geocoding also uses Open-Meteo Geocoding API.
