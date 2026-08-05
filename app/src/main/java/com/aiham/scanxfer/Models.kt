package com.aiham.scanxfer

import java.io.File

data class PreparedPayload(
    val file: File,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class TransferSession(
    val sessionId: String,
    val token: String,
    val host: String,
    val port: Int,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    fun qrText(): String = buildString {
        append("scanxfer://receive")
        append("?session=").append(sessionId)
        append("&token=").append(token)
        append("&host=").append(host)
        append("&port=").append(port)
        append("&name=").append(UriCodec.encode(displayName))
        append("&mime=").append(UriCodec.encode(mimeType))
        append("&size=").append(sizeBytes)
        append("&sha256=").append(sha256)
    }
}
