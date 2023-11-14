package de.mm20.launcher2.ui.ktx

import android.Manifest
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.PI
import de.mm20.launcher2.ktx.checkPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow

private var declination: Float? = null
private fun updateDeclination(location: Location) {
    declination = GeomagneticField(
        location.latitude.toFloat(),
        location.longitude.toFloat(),
        location.altitude.toFloat(),
        location.time
    ).declination
}

fun Context.getUserLocation() = channelFlow {
    val locationCallback = LocationListener {
        updateDeclination(it)
        trySend(it)
    }

    this@getUserLocation.getSystemService<LocationManager>()
        ?.runCatching {
            val hasFineAccess =
                this@getUserLocation.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val hasCoarseAccess =
                this@getUserLocation.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

            val location =
                (if (hasFineAccess) this@runCatching.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
                    ?: if (hasCoarseAccess) this@runCatching.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

            if (location != null) {
                updateDeclination(location)
                trySend(location)
            }

            if (hasFineAccess) {
                this@runCatching.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    locationCallback
                )
            }
            if (hasCoarseAccess) {
                this@runCatching.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000,
                    1f,
                    locationCallback
                )
            }
        }?.onFailure {
            Log.e("SearchableItemVM", "Failed to start location updates", it)
        }

    awaitClose {
        this@getUserLocation.getSystemService<LocationManager>()?.removeUpdates(locationCallback)
    }
}

fun Context.getNorthHeading(): Flow<Float> = callbackFlow {
    val sensorCallback = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR)
                return

            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val (azimuth, _, roll) = orientationAngles

            val isScreenUpsideDown = roll < -Float.PI / 2f || Float.PI / 2f < roll

            trySend(
                (if (isScreenUpsideDown) -1f else 1f) *
                        // eastward heading from magnetic north plus correction for geographic north, if available
                        (azimuth * 180f / Float.PI + (declination ?: 0f))
            )
        }
    }

    this@getNorthHeading
        .getSystemService<SensorManager>()
        ?.runCatching {
            this@runCatching.registerListener(
                sensorCallback,
                this@runCatching.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                    ?: return@runCatching,
                SensorManager.SENSOR_DELAY_UI
            )
        }?.onFailure {
            Log.e("SearchableItemVM", "Failed to start heading updates", it)
        }

    awaitClose {
        this@getNorthHeading.getSystemService<SensorManager>()?.unregisterListener(sensorCallback)
    }
}
