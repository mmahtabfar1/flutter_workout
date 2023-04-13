package dev.rexios.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result

class Workout(private val activity: FlutterActivity, private val binaryMessenger: BinaryMessenger) {

    private val channelId = "service_notification"
    private val channelName = "workout"
    private val notifyUpdate = "dataReceived"
    private val startService = "start"
    private val stopService = "stop"

    private lateinit var serviceIntent: Intent
    lateinit var channel: MethodChannel
    var workoutService: WorkoutService? = null
    var serviceConnection: ServiceConnection? = null

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                serviceConnection?.let { activity.unbindService(it) }
            }
        })
        createNotificationChannel()
        configure()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(channelId, "Messages", NotificationManager.IMPORTANCE_LOW)
        val manager = activity.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun configure() {
        serviceIntent = Intent(activity, WorkoutService::class.java)
        channel = MethodChannel(binaryMessenger, channelName)
        channel.setMethodCallHandler { call, result ->
            when(call.method) {
                startService -> {
                    Log.d("DART/NATIVE", "starting service")
                    startAndBindService(call.arguments as Map<String, Any>, result)
                    Log.d("DART/NATIVE", "service started successfully")
                }
                stopService -> {
                    Log.d("DART/NATIVE", "stopping service")
                    workoutService?.setStopCallBack { resultStr ->
                        result.success(resultStr)
                    }
                    workoutService?.stopService()
                    if (workoutService != null) {
                        serviceConnection?.let { activity.unbindService(it) }
                    }
                    workoutService = null
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun startAndBindService(args: Map<String, Any>, result: Result) {
        activity.startForegroundService(serviceIntent)
        serviceConnection = createServiceConnection(args, result)
        activity.bindService(serviceIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun createServiceConnection(args: Map<String, Any>, result: Result): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d("DART/NATIVE", "service is connected!!!!")
                workoutService = (service as WorkoutService.LocalBinder).getInstance()
                workoutService?.setServiceDataObserver { data ->
                    channel.invokeMethod(notifyUpdate, data)
                }
                workoutService?.execute(args, result)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                workoutService = null
            }
        }
    }
}
