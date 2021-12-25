package com.cts.locationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(
    private val context: Context,
    private val accuracyInMeters: Int,
    private val intervalMillis: Long = 3000,
    private val fastestIntervalMillis: Long = 1000,
    private val ageInMinutes: Int = 0
) : CoroutineScope {

    private fun <T1 : Any, T2 : Any, R : Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2) -> R?): R? {
        return if (p1 != null && p2 != null) block(p1, p2) else null
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val locationRequestGPS by lazy {
        LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(intervalMillis)
            .setFastestInterval(fastestIntervalMillis)
    }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback


    fun requestLocation(onError: (Int) -> Unit, onSuccess: (Location) -> Unit) = launch {
        if (locationPermissionGranted()) {
            val location = async { getLocationUpdates() }.await()
            withContext(Dispatchers.Main) {
                location?.let {
                    onSuccess(it)
                } ?: onError(ERROR_LOCATION_NO_GPS_AVAILABLE)
            }
        } else {
            withContext(Dispatchers.Main) {
                onError(ERROR_LOCATION_PERMISSION)
            }
        }
    }

    fun unsubscribeLocationHelper() {
        safeLet(fusedLocationProviderClient, locationCallback) { provider, callback ->
            provider.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationUpdates(): Location? = suspendCoroutine { continuation ->
        var resumed = false
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                lastLocation?.let { location ->
                    if (ageMinutes(location) <= ageInMinutes && location.accuracy <= accuracyInMeters) {
                        fusedLocationProviderClient.removeLocationUpdates(this)
                        if(!resumed){
                            continuation.resume(location)
                            resumed = true
                        }
                    }
                }
            }
        }
        when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequestGPS,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
            else -> {
                continuation.resume(null)
            }
        }
    }

    private fun locationPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ageMinutes(last: Location): Int {
        return ageMillis(last).toInt() / (60 * 1000)
    }

    private fun ageMillis(last: Location): Long {
        return (SystemClock.elapsedRealtimeNanos() - last
            .elapsedRealtimeNanos) / 1000000
    }


    companion object {
        const val ERROR_LOCATION_PERMISSION = 1
        const val ERROR_LOCATION_NO_GPS_AVAILABLE = 2
    }

}