package com.hemant.myapplication.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface CurrentLocationResult {
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    ) : CurrentLocationResult

    data object PermissionDenied : CurrentLocationResult
    data object PlayServicesUnavailable : CurrentLocationResult
    data class Unavailable(val reason: String) : CurrentLocationResult
}

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): CurrentLocationResult
}

/**
 * Foreground, single-fix provider. It never subscribes to continuous updates
 * and requires location permission to have already been granted by the UI.
 */
class FusedCurrentLocationProvider(context: Context) : CurrentLocationProvider {
    private val appContext = context.applicationContext

    override suspend fun getCurrentLocation(): CurrentLocationResult {
        val coarseGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
        val enabledProviders = locationManager.getProviders(true).joinToString().ifBlank { "none" }
        Log.d(
            TAG,
            "Location request preflight: coarseGranted=$coarseGranted, fineGranted=$fineGranted, " +
                "locationEnabled=$locationEnabled, enabledProviders=$enabledProviders",
        )

        if (!coarseGranted && !fineGranted) {
            Log.w(TAG, "Location request stopped: neither coarse nor fine permission is granted")
            return CurrentLocationResult.PermissionDenied
        }
        if (!locationEnabled) {
            Log.w(TAG, "Location request stopped: Android system location is disabled")
            return CurrentLocationResult.Unavailable("Device location is turned off")
        }
        val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Location request stopped: Google Play services status=$playServicesStatus")
            return CurrentLocationResult.PlayServicesUnavailable
        }

        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val cancellation = CancellationTokenSource()
        logLastKnownLocationState(client)
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(TAG, "Requesting one balanced-accuracy fused location fix")
        return try {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                        .addOnSuccessListener { location ->
                            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                            val result = if (location == null) {
                                Log.w(TAG, "Fused location completed after ${elapsedMs}ms with a null location")
                                CurrentLocationResult.Unavailable("Current location could not be determined")
                            } else {
                                Log.d(
                                    TAG,
                                    "Fused location completed after ${elapsedMs}ms; " +
                                        "accuracyMeters=${location.accuracy}, provider=${location.provider}",
                                )
                                CurrentLocationResult.Available(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracyMeters = location.accuracy,
                                )
                            }
                            if (continuation.isActive) continuation.resume(result)
                        }
                        .addOnFailureListener { error ->
                            Log.e(
                                TAG,
                                "Fused location failed after ${SystemClock.elapsedRealtime() - startedAt}ms: " +
                                    "${error.javaClass.simpleName}: ${error.message}",
                            )
                            if (continuation.isActive) continuation.resume(
                                CurrentLocationResult.Unavailable(
                                    error.localizedMessage ?: "Current location request failed",
                                ),
                            )
                        }
                        .addOnCanceledListener {
                            Log.w(TAG, "Fused location request was cancelled after ${SystemClock.elapsedRealtime() - startedAt}ms")
                            if (continuation.isActive) {
                                continuation.resume(CurrentLocationResult.Unavailable("Current location request was cancelled"))
                            }
                    }
                }
            } ?: run {
                Log.w(
                    TAG,
                    "Fused location timed out after ${SystemClock.elapsedRealtime() - startedAt}ms; " +
                        "no success, failure, or cancellation callback was received",
                )
                CurrentLocationResult.Unavailable("Current location request timed out")
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Location request raised SecurityException despite preflight: ${error.message}")
            CurrentLocationResult.PermissionDenied
        } finally {
            cancellation.cancel()
        }
    }

    private fun logLastKnownLocationState(client: com.google.android.gms.location.FusedLocationProviderClient) {
        client.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    Log.d(TAG, "Last-known location probe: none available")
                } else {
                    val ageSeconds = ((System.currentTimeMillis() - location.time).coerceAtLeast(0L)) / 1_000
                    Log.d(
                        TAG,
                        "Last-known location probe: available; ageSeconds=$ageSeconds, " +
                            "accuracyMeters=${location.accuracy}, provider=${location.provider}",
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Last-known location probe failed: ${error.javaClass.simpleName}: ${error.message}")
            }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "LocationDebug"
    }
}
