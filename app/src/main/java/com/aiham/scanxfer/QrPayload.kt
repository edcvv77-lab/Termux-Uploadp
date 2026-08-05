package com.aiham.scanxfer

data class QrPayload(
    val version: Int,
    val mode: String,
    val sessionId: String,
    val token: String,
    val host: String,
    val port: Int,
    val fileCount: Int
) {
    override fun toString(): String =
        listOf(version, mode, sessionId, token, host, port, fileCount).joinToString("|")

    companion object {
        fun parse(raw: String): QrPayload {
            val parts = raw.split("|")
            require(parts.size == 7) { "invalid payload" }
            return QrPayload(
                version = parts[0].toInt(),
                mode = parts[1],
                sessionId = parts[2],
                token = parts[3],
                host = parts[4],
                port = parts[5].toInt(),
                fileCount = parts[6].toInt()
            )
        }
    }
}
