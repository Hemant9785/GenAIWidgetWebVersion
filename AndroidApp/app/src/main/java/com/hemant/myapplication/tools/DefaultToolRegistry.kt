package com.hemant.myapplication.tools

import com.hemant.myapplication.domain.ToolSummary
import com.hemant.myapplication.location.CurrentLocationProvider
import com.hemant.myapplication.location.CurrentLocationResult
import org.json.JSONObject

/**
 * Device capabilities available to every tool-using agent, independent of a
 * selected domain. Tool results are resolved locally by the Android client.
 */
class DefaultToolRegistry(private val currentLocationProvider: CurrentLocationProvider) {
    fun getTools(): List<ToolSummary> = listOf(
        ToolSummary(
            path = CURRENT_LOCATION_PATH,
            name = "Current Device Location",
            description = "Returns the device's current coarse location for a location-aware request. Use only when the user did not provide a location.",
        ),
    )

    fun owns(toolPath: String): Boolean = toolPath == CURRENT_LOCATION_PATH

    fun readTool(toolPath: String): JSONObject = when (toolPath) {
        CURRENT_LOCATION_PATH -> JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
            .put("additionalProperties", false)
        else -> JSONObject()
    }

    fun readToolResponseSchema(toolPath: String): JSONObject = when (toolPath) {
        CURRENT_LOCATION_PATH -> JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("latitude", JSONObject().put("type", "number"))
                .put("longitude", JSONObject().put("type", "number"))
                .put("accuracyMeters", JSONObject().put("type", "number"))
                .put("label", JSONObject().put("type", "string"))
            )
        else -> JSONObject()
    }

    suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject = when (toolPath) {
        CURRENT_LOCATION_PATH -> when (val result = currentLocationProvider.getCurrentLocation()) {
            is CurrentLocationResult.Available -> JSONObject()
                .put("latitude", result.latitude)
                .put("longitude", result.longitude)
                .put("accuracyMeters", result.accuracyMeters)
                .put("label", "Current location")
            CurrentLocationResult.PermissionDenied -> JSONObject()
                .put("error", "Location permission is not granted. Enter a city or enable location in Settings.")
            CurrentLocationResult.PlayServicesUnavailable -> JSONObject()
                .put("error", "Google Play services location is unavailable. Enter a city instead.")
            is CurrentLocationResult.Unavailable -> JSONObject().put("error", result.reason)
        }
        else -> JSONObject().put("error", "Unknown default tool path $toolPath")
    }

    companion object {
        const val CURRENT_LOCATION_PATH = "/tool/default/current-location"
    }
}
