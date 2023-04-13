package dev.rexios.workout

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import io.flutter.plugin.common.MethodChannel.Result

class WorkoutService : Service(), ExerciseUpdateCallback {
    private var observer: ((List<Any>) -> Unit)? = null
    private var stopCallBack: ((String) -> Unit)? = null
    private val binder = LocalBinder()
    private val notifId = 101
    private val channelId = "service_notification"
    private lateinit var exerciseClient: ExerciseClient

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    inner class LocalBinder : Binder() {
        fun getInstance(): WorkoutService = this@WorkoutService
    }

    private val dataTypeStringMap = mapOf(
        DataType.HEART_RATE_BPM to "heartRate",
        DataType.CALORIES_TOTAL to "calories",
        DataType.STEPS_TOTAL to "steps",
        DataType.DISTANCE_TOTAL to "distance",
        DataType.SPEED to "speed",
        DataType.LOCATION to "location",
    )

    private fun dataTypeToString(type: DataType<*, *>): String {
        return dataTypeStringMap[type] ?: "unknown"
    }

    private fun dataTypeFromString(string: String): DataType<*, *> {
        return dataTypeStringMap.entries.firstOrNull { it.value == string }?.key
            ?: throw IllegalArgumentException("Unknown data type: $string")
    }

    fun setServiceDataObserver(observer: (List<Any>) -> Unit) {
        this.observer = observer
    }

    fun setStopCallBack(callback: (String) -> Unit) {
        this.stopCallBack = callback
    }

    override fun onBind(p0: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        exerciseClient = HealthServices.getClient(applicationContext).exerciseClient
        exerciseClient.setUpdateCallback(this)
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Workout In Progress")
            .setContentText("Your location is being actively tracked")
            .setSmallIcon(R.drawable.baseline_directions_bike_24)
        startForeground(notifId, builder.build())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopCallBack?.invoke("service stopped")
        super.onDestroy()
    }

    fun execute(args: Map<String, Any>, result: Result) {
        val exerciseTypeId = args["exerciseType"] as Int
        val exerciseType = ExerciseType.fromId(exerciseTypeId)

        val typeStrings = args["sensors"] as List<String>
        val requestedDataTypes = typeStrings.map { dataTypeFromString(it) }

        val enableGps = args["enableGps"] as Boolean

        serviceScope.launch {
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            if (exerciseType !in capabilities.supportedExerciseTypes) {
                result.error("ExerciseType $exerciseType not supported", null, null)
                return@launch
            }
            val exerciseCapabilities = capabilities.getExerciseTypeCapabilities(exerciseType)
            val supportedDataTypes = exerciseCapabilities.supportedDataTypes
            val requestedUnsupportedDataTypes = requestedDataTypes.minus(supportedDataTypes)
            val requestedSupportedDataTypes = requestedDataTypes.intersect(supportedDataTypes)

            val config = ExerciseConfig(
                exerciseType = exerciseType,
                dataTypes = requestedSupportedDataTypes,
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = enableGps,
            )

            exerciseClient.startExerciseAsync(config).await()

            // Return the unsupported data types so the developer can handle them
            result.success(mapOf("unsupportedFeatures" to requestedUnsupportedDataTypes.map {
                dataTypeToString(it)
            }))
        }
    }

    fun stopService() {
        serviceScope.launch {
            exerciseClient.endExerciseAsync().await()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
        val data = mutableListOf<List<Any>>()
        val bootInstant =
            Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

        update.latestMetrics.sampleDataPoints.forEach { dataPoint ->
            val value = if (dataPoint.value is LocationData)
                "${(dataPoint.value as LocationData).latitude}/${(dataPoint.value as LocationData).longitude}"
            else
                "${(dataPoint.value as Number).toDouble()}"
            data.add(
                listOf(
                    dataTypeToString(dataPoint.dataType),
                    value,
                    dataPoint.getTimeInstant(bootInstant).toEpochMilli()
                )
            )
        }

        update.latestMetrics.cumulativeDataPoints.forEach { dataPoint ->
            data.add(
                listOf(
                    dataTypeToString(dataPoint.dataType), "${dataPoint.total.toDouble()}",
                    // I feel like this should have getEndInstant on it like above, but whatever
                    dataPoint.end.toEpochMilli()
                )
            )
        }

        //send data back to flutter through the method channel
        data.forEach {
            observer?.invoke(it)
        }
    }

    override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) { }
    override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) { }
    override fun onRegistered() { }
    override fun onRegistrationFailed(throwable: Throwable) { }
}
