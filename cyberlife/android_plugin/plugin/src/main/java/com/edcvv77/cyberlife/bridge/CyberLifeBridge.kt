package com.edcvv77.cyberlife.bridge

import android.app.Activity
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class CyberLifeBridge(godot: Godot) : GodotPlugin(godot) {

    companion object {
        internal const val EXTRA_EXECUTION_ID = "cyberlife_execution_id"
        private const val REQUEST_DIRECTORY = 7301
        private const val REQUEST_TERMUX_PERMISSION = 7302
        private const val PREFS = "cyberlife_bridge"
        private const val PREF_TREE_URI = "selected_tree_uri"
        private val executionIds = AtomicInteger(1000)

        @Volatile
        internal var current: CyberLifeBridge? = null
    }

    private var browserOverlay: LinearLayout? = null
    private var webView: WebView? = null

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
        SignalInfo("download_started", String::class.java),
        SignalInfo("bridge_error", String::class.java)
    )

    override fun onMainCreate(activity: Activity?): View? {
        current = this
        return null
    }

    override fun onMainDestroy() {
        hideWebViewInternal()
        if (current === this) current = null
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

        val resultPendingIntent = PendingIntent.getBroadcast(context, executionId, callbackIntent, flags)

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
    fun showWebView(url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        val host = activity ?: return false

        runOnHostThread {
            hideWebViewInternal()

            val root = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF101318.toInt())
            }

            val toolbar = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
            }

            val back = Button(host).apply { text = "←" }
            val reload = Button(host).apply { text = "↻" }
            val close = Button(host).apply { text = "Close" }
            val address = EditText(host).apply {
                setSingleLine(true)
                setText(normalized)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val browser = WebView(host).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        if (!pageUrl.isNullOrBlank()) address.setText(pageUrl)
                    }
                }
                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                    try {
                        val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                        val request = DownloadManager.Request(Uri.parse(downloadUrl))
                            .setMimeType(mimeType)
                            .addRequestHeader("User-Agent", userAgent ?: "")
                            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(downloadUrl) ?: "")
                            .setTitle(fileName)
                            .setDescription("Downloaded from CyberLife")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(request)
                        emitSignal("download_started", fileName)
                    } catch (error: Exception) {
                        emitBridgeError("Download failed: " + (error.message ?: error.javaClass.simpleName))
                    }
                }
                loadUrl(normalized)
            }

            back.setOnClickListener {
                if (browser.canGoBack()) browser.goBack()
            }
            reload.setOnClickListener { browser.reload() }
            close.setOnClickListener { hideWebViewInternal() }
            address.setOnEditorActionListener { _, _, _ ->
                normalizeUrl(address.text.toString())?.let { browser.loadUrl(it) }
                true
            }

            toolbar.addView(back)
            toolbar.addView(reload)
            toolbar.addView(address)
            toolbar.addView(close)
            root.addView(toolbar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            root.addView(browser, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))

            host.addContentView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            browserOverlay = root
            webView = browser
        }
        return true
    }

    @UsedByGodot
    fun hideWebView() {
        runOnHostThread { hideWebViewInternal() }
    }

    private fun hideWebViewInternal() {
        val overlay = browserOverlay
        val browser = webView
        browser?.stopLoading()
        browser?.destroy()
        if (overlay?.parent is ViewGroup) {
            (overlay.parent as ViewGroup).removeView(overlay)
        }
        webView = null
        browserOverlay = null
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
    fun getSelectedDirectoryUri(): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_TREE_URI, "") ?: ""
    }

    @UsedByGodot
    fun listSelectedDirectory(): String {
        val tree = getSelectedDirectoryUri()
        if (tree.isBlank()) return "[]"
        return listTreeRoot(Uri.parse(tree))
    }

    private fun listTreeRoot(treeUri: Uri): String {
        val result = JSONArray()
        return try {
            val parentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val displayName = cursor.getString(nameIndex) ?: "Unnamed"
                    val mimeType = cursor.getString(mimeIndex) ?: "application/octet-stream"
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

                    val item = JSONObject()
                        .put("name", displayName)
                        .put("mime_type", mimeType)
                        .put("is_directory", isDirectory)
                        .put("uri", documentUri.toString())

                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) item.put("size", cursor.getLong(sizeIndex))
                    if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) item.put("modified", cursor.getLong(modifiedIndex))
                    result.put(item)
                }
            }
            result.toString()
        } catch (error: Exception) {
            emitBridgeError("Could not list selected folder: " + (error.message ?: error.javaClass.simpleName))
            "[]"
        }
    }

    @UsedByGodot
    fun openDocument(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            emitBridgeError("Could not open file: " + (error.message ?: error.javaClass.simpleName))
            false
        }
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
            append("\nSaved folder: ")
            append(if (getSelectedDirectoryUri().isBlank()) "none" else "granted")
        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_DIRECTORY || resultCode != Activity.RESULT_OK) return

        val resultData = data ?: return
        val uri = resultData.data ?: return
        val flags = resultData.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_TREE_URI, uri.toString())
            .apply()

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
        return if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) trimmed else "https://$trimmed"
    }
}
