package com.cts.locationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(private val context: Context, private val intervalMillis : Long = 3000) : CoroutineScope {

    inline fun <T1 : Any, T2 : Any, R : Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2) -> R?): R? {
        return if (p1 != null && p2 != null) block(p1, p2) else null
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val locationRequestGPS by lazy {
        LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(intervalMillis)
    }

    private val locationRequestNETWORK by lazy {
        LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_LOW_POWER)
            .setInterval(intervalMillis)
    }
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback


    fun getCurrentLocation(ageInMinutes : Int = 0, accuracyInMeters : Int, onError: (Int) -> Unit, onSuccess: (Location) -> Unit) = launch {
        if (locationPermissionGranted()) {
            val location = async { getLocationUpdates(ageInMinutes, accuracyInMeters) }.await()
            Log.d("???", "final getCurrentLocation $location")
            withContext(Dispatchers.Main) {
                location?.let {
                    onSuccess(it)
                } ?: onError(ERROR_LOCATION_NULL)
            }
        } else {
            onError(ERROR_LOCATION_PERMISSION)
        }
    }

    fun unsubscribeLocationHelper(){
        safeLet(fusedLocationProviderClient, locationCallback){ provider, callback->
            provider.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationUpdates(ageInMinutes : Int, accuracyInMeters : Int): Location? = suspendCoroutine { continuation ->
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d("????", "outside accuracy location received: ${locationResult.lastLocation}")
                val lastLocation = locationResult.lastLocation
                lastLocation?.let { location ->
                    Log.d("???", "how old is the location in minutes -> ${ageMinutes(location)}")
                    if (ageMinutes(location) <= ageInMinutes && location.accuracy <= accuracyInMeters) {
                        fusedLocationProviderClient.removeLocationUpdates(this)
                        Log.d(
                            "????",
                            "inside accuracylocation received: ${locationResult.lastLocation}"
                        )
                        continuation.resume(location)
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
        const val ERROR_LOCATION_NULL = 2
    }

}