package dev.rexios.workout

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger

/** WorkoutPlugin */
class WorkoutPlugin : FlutterPlugin, ActivityAware {

    private var activity: FlutterActivity? = null
    private var binaryMessenger: BinaryMessenger? = null
    private var workout: Workout? = null

    override fun onAttachedToEngine(flutterpluginBinding: FlutterPlugin.FlutterPluginBinding) {
        binaryMessenger = flutterpluginBinding.binaryMessenger
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        workout?.channel?.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity as FlutterActivity
        workout = Workout(activity!!, binaryMessenger!!)
    }

    override fun onDetachedFromActivityForConfigChanges() { }
    override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) { }
    override fun onDetachedFromActivity() { }
}
