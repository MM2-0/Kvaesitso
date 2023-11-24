package de.mm20.launcher2.devicepose

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import de.mm20.launcher2.ktx.checkPermission
import kotlinx.coroutines.flow.combine

class DevicePoseProvider internal constructor(
    private val context: Context
) {
    private var declination: Float? = null
    private fun updateDeclination(location: Location) {
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            location.time
        ).declination
    }

    fun getLocation() = channelFlow {
        val locationCallback = LocationListener {
            updateDeclination(it)
            trySend(it)
        }

        context.getSystemService<LocationManager>()
            ?.runCatching {
                val hasFineAccess =
                    context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                val hasCoarseAccess =
                    context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

                val location =
                    (if (hasFineAccess) this@runCatching.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
                        ?: if (hasCoarseAccess) this@runCatching.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER
                        ) else null

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
            context.getSystemService<LocationManager>()?.removeUpdates(locationCallback)
        }
    }

    fun getAzimuthRadians(): Flow<Float> = callbackFlow {
        val azimuthCallback = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR)
                    return

                val rotationMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3)

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                trySend(orientationAngles[0])
            }
        }

        context
            .getSystemService<SensorManager>()
            ?.runCatching {
                this@runCatching.registerListener(
                    azimuthCallback,
                    this@runCatching.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                        ?: return@runCatching,
                    SensorManager.SENSOR_DELAY_UI
                )
            }?.onFailure {
                Log.e("DevicePoseProvider", "Failed to register ROTATION_VECTOR listener", it)
            }

        awaitClose {
            context.getSystemService<SensorManager>()?.unregisterListener(azimuthCallback)
        }
    }

    fun getHeadingToDegrees(headingEastwardDegrees: Float): Flow<Float> = combine(
        getAzimuthRadians(),
        callbackFlow {
            val upsideDownCallback = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_GRAVITY)
                        return

                    val (_, _, z) = event.values
                    trySend(z < 0f)
                }
            }
            context
                .getSystemService<SensorManager>()
                ?.runCatching {
                    this@runCatching.registerListener(
                        upsideDownCallback,
                        this@runCatching.getDefaultSensor(Sensor.TYPE_GRAVITY)
                            ?: return@runCatching,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }?.onFailure {
                    Log.e("SearchableItemVM", "Failed to start heading updates", it)
                }

            awaitClose {
                context.getSystemService<SensorManager>()?.unregisterListener(upsideDownCallback)
            }
        }) { azimuthRadians, isUpsideDown ->
        val azimuthDegrees = azimuthRadians * 180f / Float.PI

        if (isUpsideDown) {
            azimuthDegrees - headingEastwardDegrees
        } else {
            headingEastwardDegrees - azimuthDegrees
        }
    }
}