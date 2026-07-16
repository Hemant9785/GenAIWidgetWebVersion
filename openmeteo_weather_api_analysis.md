# Open-Meteo Weather Forecast API — detailed analysis

**Scope.** This document covers Open-Meteo's public Weather Forecast API, the usual meaning of “Open-Meteo weather API”: `GET https://api.open-meteo.com/v1/forecast`. It is a coordinate-based JSON API; it does not accept a city name directly. Use coordinates (or Open-Meteo's separate Geocoding API) first. The default output is a seven-day forecast beginning at local midnight; `forecast_days=16` extends it to 16 days. The API automatically selects the best applicable weather model unless a model is requested explicitly. [Official Forecast API documentation](https://open-meteo.com/en/docs)

## Request parameters

| Parameter | Type / default | Purpose |
|---|---|---|
| `latitude`, `longitude` | float; **required** | WGS84 position. Comma-separated lists request multiple locations and yield a JSON array. West longitudes are negative. |
| `elevation` | float; optional | Overrides elevation used for downscaling. `nan` disables downscaling; lists are supported for multiple locations. |
| `current` | comma-separated variable list | Requests a single current observation-style object. Every hourly variable is eligible. |
| `hourly` | comma-separated variable list | Requests arrays at one-hour resolution. |
| `minutely_15` | comma-separated variable list | Requests arrays at 15-minute resolution. Native data is regional; elsewhere it is interpolated from hourly data. |
| `daily` | comma-separated variable list | Requests daily aggregations. **Requires `timezone`.** |
| `temperature_unit` | `celsius` (default) or `fahrenheit` | Converts temperature fields. |
| `wind_speed_unit` | `kmh` (default), `ms`, `mph`, `kn` | Converts wind speed/gust fields. |
| `precipitation_unit` | `mm` (default) or `inch` | Converts precipitation and evaporation amounts. |
| `timeformat` | `iso8601` (default) or `unixtime` | UNIX timestamps are UTC; apply `utc_offset_seconds` when interpreting daily local dates. |
| `timezone` | `GMT` (default), IANA name, or `auto` | Determines timestamps and day boundaries. Example: `Asia/Kolkata`; `auto` resolves from coordinates. |
| `past_days` | integer `0–92`, default `0` | Prepends prior days. |
| `forecast_days` | integer `0–16`, default `7` | Forecast-day length. |
| `forecast_hours`, `past_hours` | positive integer | Window hourly data from the current hour. |
| `forecast_minutely_15`, `past_minutely_15` | positive integer | Window 15-minute data from the current 15-minute interval. |
| `start_date`, `end_date` | `YYYY-MM-DD` | Explicit date interval. |
| `start_hour`, `end_hour` | `YYYY-MM-DDTHH:MM` | Explicit hourly interval. |
| `start_minutely_15`, `end_minutely_15` | `YYYY-MM-DDTHH:MM` | Explicit 15-minute interval. |
| `models` | comma-separated list; default `auto` | Selects one/more named forecast models instead of automatic best-match blending. |
| `cell_selection` | `land` (default), `sea`, `nearest` | Chooses grid-cell preference. `land` favours similar land elevation. |
| `tilt`, `azimuth` | degrees; optional | Required together when requesting `global_tilted_irradiance`. Tilt: 0–90; azimuth: 0=south, -90=east, 90=west, ±180=north. `nan` enables tracker assumptions. |
| `apikey` | string; optional | For commercial/reserved resources; use the customer API hostname when applicable. |

Use a comma-delimited value (for example, `hourly=temperature_2m,precipitation`) or repeat the parameter. Do not assume every model offers every variable: availability and native resolution depend on model and region.

## Variable selectors

### `current` and `hourly`

Current supports every hourly selector. Hourly values are usually instantaneous at the timestamp; precipitation, radiation, evapotranspiration, and gust values are backward-looking aggregations over the prior hour.

| Family | Valid selectors | Units / interpretation |
|---|---|---|
| Temperature & humidity | `temperature_2m`, `relative_humidity_2m`, `dew_point_2m`, `apparent_temperature`, `wet_bulb_temperature_2m` | °C/°F (except humidity `%`). |
| Pressure & clouds | `pressure_msl`, `surface_pressure`, `cloud_cover`, `cloud_cover_low`, `cloud_cover_mid`, `cloud_cover_high` | hPa and %. |
| Wind | `wind_speed_10m`, `_80m`, `_120m`, `_180m`; matching `wind_direction_*`; `wind_gusts_10m` | speed in selected wind unit; directions in degrees; gust is preceding-hour maximum. |
| Precipitation & conditions | `precipitation`, `rain`, `showers`, `snowfall`, `precipitation_probability`, `weather_code`, `snow_depth`, `freezing_level_height`, `visibility`, `is_day` | mm/inch (snowfall cm/inch), probability %, WMO code, metres, or 0/1. Precipitation fields are preceding-hour sums. |
| Radiation | `shortwave_radiation`, `direct_radiation`, `diffuse_radiation`, `direct_normal_irradiance`, `global_tilted_irradiance` | W/m², preceding-hour mean. Tilted irradiance also needs `tilt` and `azimuth`. |
| Land, solar & convection | `evapotranspiration`, `et0_fao_evapotranspiration`, `vapour_pressure_deficit`, `cape`, `uv_index`, `uv_index_clear_sky`, `sunshine_duration`, `total_column_integrated_water_vapour`, `lifted_index`, `convective_inhibition`, `freezing_level_height`, `boundary_layer_height` | mm/inch, kPa, J/kg, index, seconds, kg/m², K, J/kg, or metres, respectively. Some are model-dependent. |
| Soil | `soil_temperature_0cm`, `_6cm`, `_18cm`, `_54cm`; `soil_moisture_0_to_1cm`, `_1_to_3cm`, `_3_to_9cm`, `_9_to_27cm`, `_27_to_81cm` | Temperature °C/°F; moisture m³/m³. |

### Pressure-level `hourly` selectors

For each level in `1000,975,950,925,900,850,800,700,600,500,400,300,250,200,150,100,70,50,30` hPa, use any of:

`temperature_<level>hPa`, `relative_humidity_<level>hPa`, `dew_point_<level>hPa`, `cloud_cover_<level>hPa`, `wind_speed_<level>hPa`, `wind_direction_<level>hPa`, `geopotential_height_<level>hPa`.

These are instantaneous. They are pressure surfaces rather than fixed heights: use geopotential height to determine actual altitude.

### `minutely_15`

The native 15-minute set includes `temperature_2m`, `relative_humidity_2m`, `dew_point_2m`, `apparent_temperature`, radiation fields (`shortwave_radiation`, `direct_radiation`, `direct_normal_irradiance`, `diffuse_radiation`, `global_tilted_irradiance`, `global_tilted_irradiance_instant`), `sunshine_duration`, `lightning_potential`, `precipitation`, `rain`, `showers`, `snowfall`, `snowfall_height`, `freezing_level_height`, `cape`, wind speed/direction at 10m/80m, `wind_gusts_10m`, `visibility`, and `weather_code`. Other hourly variables can also be requested but are interpolated. Native coverage is North America and Central Europe; not every listed native field is furnished by every regional model.

### `daily`

Daily fields are 24-hour aggregates in the supplied timezone: `weather_code`; `temperature_2m_max`, `_mean`, `_min`; `apparent_temperature_max`, `_mean`, `_min`; `sunrise`, `sunset`, `daylight_duration`, `sunshine_duration`; `precipitation_sum`, `rain_sum`, `showers_sum`, `snowfall_sum`, `precipitation_hours`; `precipitation_probability_max`, `_mean`, `_min`; `wind_speed_10m_max`, `wind_gusts_10m_max`, `wind_direction_10m_dominant`; `shortwave_radiation_sum`; `et0_fao_evapotranspiration`; `uv_index_max`, and `uv_index_clear_sky_max`.

The documentation also lists additional daily fields: `temperature_2m_mean`, `apparent_temperature_mean`, `cape_mean`, `_max`, `_min`, `cloud_cover_mean`, `_max`, `_min`, `dew_point_2m_mean`, `_max`, `_min`, `et0_fao_evapotranspiration_sum`, `growing_degree_days_base_0_limit_50`, `leaf_wetness_probability_mean`, `precipitation_probability_mean`, `_min`, `relative_humidity_2m_mean`, `_max`, `_min`, `snowfall_water_equivalent_sum`, `pressure_msl_mean`, `_max`, `_min`, `surface_pressure_mean`, `_max`, `_min`, `updraft_max`, `visibility_mean`, `_min`, `_max`, `wind_gusts_10m_mean`, `_min`, and `wind_speed_10m_mean`, `_min`, plus `wet_bulb_temperature_2m_mean`, `_max`, `_min`, and `vapour_pressure_deficit_max`.

## Response and validation rules

Successful responses contain location metadata: `latitude`, `longitude` (the selected grid-cell centre, which may differ slightly from the request), `elevation`, `generationtime_ms`, `utc_offset_seconds`, `timezone`, and `timezone_abbreviation`.

Requested data appears only in its matching section:

```json
{
  "current": {"time": "…", "interval": 900, "temperature_2m": 27.1},
  "current_units": {"time": "iso8601", "interval": "seconds", "temperature_2m": "°C"},
  "hourly": {"time": ["…"], "temperature_2m": [27.1]},
  "hourly_units": {"time": "iso8601", "temperature_2m": "°C"},
  "daily": {"time": ["…"], "temperature_2m_max": [31.4]},
  "daily_units": {"time": "iso8601", "temperature_2m_max": "°C"}
}
```

Arrays in a section are index-aligned with its `time` array. `current.interval` is the aggregation duration in seconds (often 900 seconds), so current precipitation refers to the preceding interval. With multiple coordinate pairs, the top-level JSON is an array of objects. Malformed requests return HTTP 400 and JSON such as `{"error": true, "reason": "…"}`.

`weather_code` uses WMO codes: 0 clear; 1–3 mainly clear/partly cloudy/overcast; 45/48 fog; 51–57 drizzle; 61–67 rain/freezing rain; 71–77 snow; 80–82 showers; 85/86 snow showers; and 95/96/99 thunderstorms (with hail for 96/99).

## Practical recommendation

Start small: request only variables the application needs, pass an explicit IANA timezone, and treat `hourly.time[i]` and every requested `hourly.<field>[i]` as one record. For reproducible testing, set coordinates, timezone, `forecast_days`, and `models` explicitly. Use the script beside this document to inspect the actual current payload and save it for review.

Sources: [Forecast API reference](https://open-meteo.com/en/docs), [Historical API reference](https://open-meteo.com/en/docs/historical-weather-api) (same core coordinate/unit conventions, but historical endpoint and required dates), and [Air Quality API reference](https://open-meteo.com/en/docs/air-quality-api) (a distinct endpoint with separate pollutant variables).
