package com.aiham.scanxfer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aiham.scanxfer.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val secureRandom = SecureRandom()
    private var server: TransferServer? = null

    private val singlePick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startSendFlow(listOf(uri))
    }

    private val multiPick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startSendFlow(uris)
    }

    private val scanQr = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            toast("لم يتم التقاط QR")
            return@registerForActivityResult
        }
        receiveFromQr(contents)
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchScanner() else toast("يلزم إذن الكاميرا للمسح")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sendSingleButton.setOnClickListener { singlePick.launch(arrayOf("*/*")) }
        binding.sendMultiButton.setOnClickListener { multiPick.launch(arrayOf("*/*")) }
        binding.scanButton.setOnClickListener { ensureCameraAndScan() }

        log("جاهز. اختر ملفًا أو امسح QR للاستقبال.")
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    private fun ensureCameraAndScan() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> launchScanner()
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED -> launchScanner()
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.toString())
            .setPrompt("وجّه الكاميرا إلى رمز النقل")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
        scanQr.launch(options)
    }

    private fun startSendFlow(uris: List<Uri>) {
        lifecycleScope.launch {
            try {
                binding.logText.text = "جاري تجهيز الملف..."
                val payload = withContext(Dispatchers.IO) {
                    if (uris.size == 1) FileUtils.prepareSingle(this@MainActivity, uris.first())
                    else FileUtils.prepareMultiple(this@MainActivity, uris)
                }
                startServer(payload)
            } catch (t: Throwable) {
                log("فشل تجهيز الملف: ${t.message}")
                toast("فشل تجهيز الملف")
            }
        }
    }

    private fun startServer(payload: PreparedPayload) {
        server?.stop()
        val port = findFreePort()
        val host = NetworkUtils.localIpv4Address()
        val sessionId = randomToken(16)
        val token = randomToken(32)
        val transferSession = TransferSession(
            sessionId = sessionId,
            token = token,
            host = host,
            port = port,
            displayName = payload.displayName,
            mimeType = payload.mimeType,
            sizeBytes = payload.sizeBytes,
            sha256 = payload.sha256,
        )
        val transferServer = TransferServer(transferSession, payload.file)
        transferServer.start(10_000, false)
        server = transferServer

        val qrText = transferSession.qrText()
        binding.qrImage.setImageBitmap(QrUtils.createQrBitmap(qrText))
        binding.qrText.text = qrText
        binding.fileNameText.text = payload.displayName
        binding.detailsText.text = "${humanSize(payload.sizeBytes)} • ${payload.mimeType} • SHA-256 جاهز"
        log("الجلسة نشطة: http://$host:$port/download?token=$token")
    }

    private fun receiveFromQr(contents: String) {
        val uri = runCatching { Uri.parse(contents) }.getOrNull()
        if (uri == null || uri.scheme != "scanxfer") {
            toast("QR غير صالح")
            return
        }
        val host = uri.getQueryParameter("host") ?: return toast("Host مفقود")
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return toast("Port غير صالح")
        val token = uri.getQueryParameter("token") ?: return toast("Token مفقود")
        val name = UriCodec.decode(uri.getQueryParameter("name"))
        val mime = UriCodec.decode(uri.getQueryParameter("mime")).ifBlank { "application/octet-stream" }
        val size = uri.getQueryParameter("size")?.toLongOrNull() ?: 0L
        val sha256 = uri.getQueryParameter("sha256") ?: ""
        val url = "http://$host:$port/download?token=$token"

        binding.logText.text = "جاري التنزيل من الجهاز الآخر..."
        lifecycleScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) { downloadBytes(url) }
                val saved = withContext(Dispatchers.IO) {
                    val display = if (name.isBlank()) "scanxfer_${System.currentTimeMillis()}" else name
                    FileUtils.saveToDownloads(this@MainActivity, display, mime, downloaded)
                }
                val gotSha = sha256Of(downloaded)
                val integrityOk = sha256.isBlank() || sha256.equals(gotSha, ignoreCase = true)
                binding.logText.text = buildString {
                    append("تم الحفظ بنجاح\n")
                    append("المسار: ").append(saved?.toString() ?: "غير معروف").append('\n')
                    if (size > 0) append("الحجم: ").append(humanSize(size)).append('\n')
                    append("السلامة: ").append(if (integrityOk) "مطابق" else "يوجد اختلاف").append('\n')
                }
                toast("تم التنزيل إلى Downloads")
            } catch (t: Throwable) {
                binding.logText.text = "فشل الاستقبال: ${t.message}"
                toast("فشل الاستقبال")
            }
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 0
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        BufferedInputStream(conn.inputStream).use { input ->
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(tmp)
                if (read <= 0) break
                buffer.write(tmp, 0, read)
            }
            return buffer.toByteArray()
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            return max(1024, socket.localPort)
        }
    }

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        secureRandom.nextBytes(data)
        return data.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val out = digest.digest(bytes)
        return out.joinToString("") { "%02x".format(it) }
    }

    private fun humanSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            size >= gb -> String.format("%.2f GB", size / gb)
            size >= mb -> String.format("%.2f MB", size / mb)
            size >= kb -> String.format("%.2f KB", size / kb)
            else -> "$size B"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun log(msg: String) { binding.logText.text = msg }
}
