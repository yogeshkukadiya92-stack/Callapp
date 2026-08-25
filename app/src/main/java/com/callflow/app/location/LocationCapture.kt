package com.callflow.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val capturedAt: String = Instant.now().toString())

@Singleton
class LocationCapture @Inject constructor(@ApplicationContext private val context: Context) {
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    suspend fun current(): Result<GeoPoint> = runCatching { withTimeout(15_000) {
        check(hasPermission()) { "Location permission is required." }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER; manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER; else -> error("Turn on device location and try again.") }
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            @Suppress("DEPRECATION")
            val listener = android.location.LocationListener { value -> if (continuation.isActive) continuation.resume(value) }
            @Suppress("MissingPermission", "DEPRECATION") manager.requestSingleUpdate(provider, listener, null)
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        } ?: error("Current location is unavailable.")
        GeoPoint(location.latitude, location.longitude, location.accuracy)
    } }
}
