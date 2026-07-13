package com.hemant.myapplication.domain

import java.util.Locale

object DomainRegistry {
    private val adapters = mapOf(
        "weather" to WeatherDomainAdapter(),
        "generic" to GenericDomainAdapter()
    )

    fun getAdapter(profileName: String): DomainAdapter {
        return adapters[profileName] ?: adapters["generic"]!!
    }

    fun getAllAdapters(): List<DomainAdapter> {
        return adapters.values.toList()
    }

    fun classify(prompt: String): DomainAdapter {
        val text = prompt.lowercase(Locale.US)
        val weatherTokens = arrayOf("weather", "forecast", "rain", "temperature", "humidity", "air quality", "uv index", "wind", "sunny", "cloud", "snow")
        for (token in weatherTokens) {
            if (text.contains(token)) {
                return getAdapter("weather")
            }
        }
        return getAdapter("generic")
    }
}
