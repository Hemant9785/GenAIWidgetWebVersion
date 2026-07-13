package com.hemant.myapplication.ui

import android.content.Context
import com.hemant.myapplication.R
import com.hemant.myapplication.model.ComponentValue
import com.hemant.myapplication.model.IconRequest
import java.util.Locale

class ComposeIconResolver {
    fun resourceFor(context: Context, iconValue: ComponentValue?, bindings: ComposeBindingResolver, fallbackRef: String = "ms:info"): Int {
        val iconRef = iconRef(iconValue, bindings, fallbackRef)
        return resourceForRef(iconRef)
    }

    fun isWeatherBitmapRef(iconRef: String): Boolean {
        val ref = iconRef.lowercase(Locale.US).removePrefix("ms:")
        return ref in listOf(
            "sunny", "clear_night", "partly_cloudy_day", "partly_cloudy_night", 
            "cloudy", "cloud", "rainy", "rain", "heavy_rain", "shower", 
            "thunderstorm", "weather_snowy", "snow", "foggy", "fog", "windy", "wind"
        )
    }

    fun iconRef(iconValue: ComponentValue?, bindings: ComposeBindingResolver, fallbackRef: String = "ms:info"): String {
        return when (iconValue) {
            is ComponentValue.Icon -> {
                val req = iconValue.request
                if (req.binding != null) {
                    val resolved = bindings.path(req.binding.pointer)?.toString()?.trim().orEmpty()
                    if (resolved.isNotEmpty()) return resolved
                }
                req.ref ?: req.fallbackRef ?: fallbackRef
            }
            is ComponentValue.Text -> iconValue.value
            is ComponentValue.Binding -> bindings.text(iconValue)
            else -> fallbackRef
        }
    }

    fun resourceForRef(iconRef: String): Int {
        val ref = iconRef.lowercase(Locale.US).removePrefix("ms:").replace("_", "")
        
        // Weather bitmap drawables mapping
        if (ref == "sunny" || ref == "clearnight") return R.drawable.gfw_weather_day_sunny
        if (ref == "partlycloudyday" || ref == "mostlysunny") return R.drawable.gfw_weather_day_partly_cloud
        if (ref == "partlycloudynight") return R.drawable.gfw_weather_night_partly_cloud
        if (ref == "cloud" || ref == "cloudy") return R.drawable.gfw_weather_day_cloudy
        if (ref == "rain" || ref == "rainy" || ref == "shower" || ref == "heavyrain") return R.drawable.gfw_weather_day_rain
        if (ref == "thunderstorm") return R.drawable.gfw_weather_day_thunderstorm
        if (ref == "snow" || ref == "snowy" || ref == "weathersnowy") return R.drawable.gfw_weather_day_snow
        if (ref == "fog" || ref == "foggy") return R.drawable.gfw_weather_day_fog
        if (ref == "wind" || ref == "windy") return R.drawable.gfw_weather_day_wind

        // Material symbols mapping
        if (ref == "humidity") return R.drawable.gfw_ic_humidity_24
        if (ref == "battery") return R.drawable.gfw_ic_battery_24
        if (ref == "wifi") return R.drawable.gfw_ic_wifi_24
        if (ref == "signal" || ref == "network") return R.drawable.gfw_ic_signal_24
        if (ref == "money" || ref == "spend") return R.drawable.gfw_ic_money_24
        if (ref == "chart" || ref == "stock") return R.drawable.gfw_ic_chart_24
        if (ref == "task") return R.drawable.gfw_ic_task_24
        if (ref == "flight" || ref == "travel") return R.drawable.gfw_ic_flight_24
        if (ref == "news") return R.drawable.gfw_ic_news_24
        if (ref == "play") return R.drawable.gfw_ic_play_24
        if (ref == "shopping") return R.drawable.gfw_ic_shipping_24
        if (ref == "restaurant" || ref == "food") return R.drawable.gfw_ic_restaurant_24
        if (ref == "home") return R.drawable.gfw_ic_home_24
        if (ref == "settings") return R.drawable.gfw_ic_settings_24
        if (ref == "warning" || ref == "error") return R.drawable.gfw_ic_warning_24
        if (ref == "refresh") return R.drawable.gfw_ic_refresh_24
        if (ref == "person") return R.drawable.gfw_ic_person_24
        if (ref == "phone" || ref == "call") return R.drawable.gfw_ic_phone_24
        
        return R.drawable.gfw_ic_info_24
    }
}
