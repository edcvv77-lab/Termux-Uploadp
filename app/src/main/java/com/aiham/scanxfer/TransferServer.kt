package com.aiham.scanxfer

import fi.iki.elonen.NanoHTTPD
import java.io.File

class TransferServer(
    private val session: TransferSession,
    private val payloadFile: File,
) : NanoHTTPD(session.port) {

    override fun serve(sessionRequest: IHTTPSession): Response {
        return try {
            when (sessionRequest.uri) {
                "/ping" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
                "/download" -> handleDownload(sessionRequest)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${t.message}")
        }
    }

    private fun handleDownload(sessionRequest: IHTTPSession): Response {
        val params = sessionRequest.parameters
        val token = params["token"]?.firstOrNull().orEmpty()
        if (token != session.token) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid token")
        }
        val response = newChunkedResponse(
            Response.Status.OK,
            session.mimeType.ifBlank { "application/octet-stream" },
            payloadFile.inputStream(),
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${session.displayName}\"")
        response.addHeader("X-ScanXfer-SHA256", session.sha256)
        response.addHeader("Cache-Control", "no-store")
        return response
    }
}
