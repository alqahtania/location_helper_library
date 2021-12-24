package com.cts.locationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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

class LocationHelper(private val context: Context) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val locationRequestGPS by lazy {
        LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(3000)
    }

    private val locationRequestNETWORK by lazy {
        LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_LOW_POWER)
            .setInterval(3000)
    }
//    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient

    fun getCurrentLocation(onError : (Int) -> Unit, onSuccess : (Location) -> Unit) = launch{
        if(locationPermissionGranted()){
            val location = async { getLocationUpdates() }.await()
            Log.d("???", "final getCurrentLocation $location")
            withContext(Dispatchers.Main){
                location?.let {
                    onSuccess(it)
                }
            }
        }else{
            onError(ERROR_LOCATION_PERMISSION)
        }
    }



    @SuppressLint("MissingPermission")
    private suspend fun getLocationUpdates() : Location? = suspendCoroutine{ continuation ->
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d("???", "location callback running on thread -> ${Thread.currentThread().name}")
                Log.d("????", "outside accuracy location received: ${locationResult.lastLocation}")
                val lastLocation = locationResult.lastLocation
                lastLocation?.let { location ->
                    Log.d("???", "how old is the location in minutes -> ${ageMinutes(location)}")
                    if(ageMinutes(location) == 0 && location.accuracy < 30){
                        fusedLocationProviderClient.removeLocationUpdates(this)
                        Log.d("????", "inside accuracylocation received: ${locationResult.lastLocation}")
                        continuation.resume(location)
                    }
                }
            }
        }
        when{
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                fusedLocationProviderClient.requestLocationUpdates(locationRequestGPS, callback, Looper.getMainLooper())
            }
            else -> {
                continuation.resume(null)
            }
        }

    }
    private fun locationPermissionGranted() : Boolean{
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun ageMinutes(last: Location): Int {
        return ageMillis(last).toInt() / (60 * 1000)
    }

    private fun ageMillis(last: Location): Long {
        return (SystemClock.elapsedRealtimeNanos() - last
            .elapsedRealtimeNanos) / 1000000
    }


    companion object{
        const val ERROR_LOCATION_PERMISSION = 1
    }

}