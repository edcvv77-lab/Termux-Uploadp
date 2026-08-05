package com.aiham.scanxfer

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object URLConnectionHelper {
    fun open(url: String): Pair<String, ByteArray> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.connect()

        if (conn.responseCode != 200) {
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }

        val fileName = headerFileName(conn) ?: "scanxfer_${System.currentTimeMillis()}.bin"
        val bytes = conn.inputStream.use { it.readBytes() }
        return fileName to bytes
    }

    private fun headerFileName(conn: HttpURLConnection): String? {
        val cd = conn.getHeaderField("Content-Disposition") ?: return null
        val match = Regex("""filename=\"?([^\";]+)\"?""").find(cd)
        return match?.groupValues?.getOrNull(1)
    }
}
