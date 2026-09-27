package com.edcvv77.cyberlife.bridge

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.view.View
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.util.concurrent.atomic.AtomicInteger

class CyberLifeBridge(godot: Godot) : GodotPlugin(godot) {

    companion object {
        internal const val EXTRA_EXECUTION_ID = "cyberlife_execution_id"
        private const val REQUEST_DIRECTORY = 7301
        private const val REQUEST_TERMUX_PERMISSION = 7302
        private val executionIds = AtomicInteger(1000)

        @Volatile
        internal var current: CyberLifeBridge? = null
    }

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): Set<SignalInfo> = setOf(
        SignalInfo(
            "termux_result",
            java.lang.Integer::class.java,
            String::class.java,
            String::class.java,
            java.lang.Integer::class.java
        ),
        SignalInfo("directory_picked", String::class.java),
        SignalInfo("bridge_error", String::class.java)
    )

    override fun onMainCreate(activity: Activity?): View? {
        current = this
        return null
    }

    override fun onMainDestroy() {
        if (current === this) {
            current = null
        }
    }

    @UsedByGodot
    fun isTermuxAvailable(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @UsedByGodot
    fun requestTermuxPermission() {
        val host = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            host.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED
        ) {
            host.requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"), REQUEST_TERMUX_PERMISSION)
        }
    }

    @UsedByGodot
    fun runTermuxCommand(command: String): Int {
        val safeCommand = command.trim()
        if (safeCommand.isEmpty() || safeCommand.length > 32768) {
            emitBridgeError("Command is empty or too long.")
            return -1
        }
        if (!isTermuxAvailable()) {
            emitBridgeError("Termux is not installed.")
            return -1
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED
        ) {
            emitBridgeError("Grant the Run commands in Termux permission first.")
            return -1
        }

        val executionId = executionIds.incrementAndGet()
        val callbackIntent = Intent(context, TermuxResultReceiver::class.java)
            .putExtra(EXTRA_EXECUTION_ID, executionId)

        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val resultPendingIntent = PendingIntent.getBroadcast(
            context,
            executionId,
            callbackIntent,
            flags
        )

        val intent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", safeCommand))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent)
        }

        return try {
            context.startService(intent)
            executionId
        } catch (error: Exception) {
            emitBridgeError(
                "Termux could not start the command. Verify allow-external-apps=true. " +
                    (error.message ?: error.javaClass.simpleName)
            )
            -1
        }
    }

    @UsedByGodot
    fun openBrowser(url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            emitBridgeError(error.message ?: "Could not open browser.")
            false
        }
    }

    @UsedByGodot
    fun pickDirectory() {
        val host = activity ?: run {
            emitBridgeError("Android activity is unavailable.")
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        host.startActivityForResult(intent, REQUEST_DIRECTORY)
    }

    @UsedByGodot
    fun deviceSummary(): String {
        val battery = context.getSystemService(BatteryManager::class.java)
        val batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return buildString {
            append("Device: ")
            append(Build.MANUFACTURER)
            append(" ")
            append(Build.MODEL)
            append("\nAndroid: ")
            append(Build.VERSION.RELEASE)
            append(" (API ")
            append(Build.VERSION.SDK_INT)
            append(")")
            if (batteryPercent >= 0) {
                append("\nBattery: ")
                append(batteryPercent)
                append("%")
            }
            append("\nTermux: ")
            append(if (isTermuxAvailable()) "installed" else "not detected")
        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_DIRECTORY || resultCode != Activity.RESULT_OK) {
            return
        }
        val resultData = data ?: return
        val uri = resultData.data ?: return
        val flags = resultData.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        }
        emitSignal("directory_picked", uri.toString())
    }

    internal fun handleTermuxResult(intent: Intent) {
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        val bundle = intent.getBundleExtra("result")
        if (bundle == null) {
            emitBridgeError("Termux returned no result bundle.")
            return
        }

        val stdout = bundle.getString("stdout", "")
        val stderr = bundle.getString("stderr", "")
        val exitCode = bundle.getInt("exitCode", -1)
        emitSignal("termux_result", executionId, stdout, stderr, exitCode)
    }

    private fun emitBridgeError(message: String) {
        emitSignal("bridge_error", message)
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
