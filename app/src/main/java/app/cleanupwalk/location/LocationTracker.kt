package app.cleanupwalk.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import app.cleanupwalk.domain.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class LocationFix(val location: GeoPoint, val accuracyMeters: Float?, val timeMillis: Long)

/**
 * GPS updates from the platform location manager (no Google Play Services needed)
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Continuous location updates. Requires location permission.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMillis: Long = 1000L): Flow<LocationFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // Not used
            }

            override fun onProviderEnabled(provider: String) {
                // Not used
            }

            override fun onProviderDisabled(provider: String) {
                // Not used
            }
        }

        // Start with the last known fix so the screen isn't empty while GPS warms up
        lastKnown()?.let { trySend(it) }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        for (provider in providers) {
            manager.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
        }

        awaitClose { manager.removeUpdates(listener) }
    }

    /**
     * A single current location, or null if none arrives in time
     */
    suspend fun current(timeoutMillis: Long = 20_000L): LocationFix? {
        if (!hasPermission()) {
            return null
        }
        return withTimeoutOrNull(timeoutMillis) {
            updates().first {
                val isRecent = System.currentTimeMillis() - it.timeMillis < 2 * 60_000L
                isRecent && (it.accuracyMeters == null || it.accuracyMeters <= 50f)
            }
        } ?: lastKnown()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): LocationFix? {
        if (!hasPermission()) {
            return null
        }
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.toFix()
    }

    private fun Location.toFix(): LocationFix {
        return LocationFix(
            GeoPoint(latitude, longitude),
            if (hasAccuracy()) accuracy else null,
            time
        )
    }
}
