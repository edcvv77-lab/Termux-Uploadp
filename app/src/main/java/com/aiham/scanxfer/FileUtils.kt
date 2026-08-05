package com.aiham.scanxfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {
    fun mimeTypeForName(name: String): String {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return "application/octet-stream"
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun prepareSingle(context: Context, uri: Uri): PreparedPayload {
        val name = queryDisplayName(context.contentResolver, uri) ?: "file_${System.currentTimeMillis()}"
        val outFile = File(context.cacheDir, sanitize(name))
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open file stream" }
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return PreparedPayload(
            file = outFile,
            displayName = name,
            mimeType = context.contentResolver.getType(uri) ?: mimeTypeForName(name),
            sizeBytes = outFile.length(),
            sha256 = sha256(outFile),
        )
    }

    fun prepareMultiple(context: Context, uris: List<Uri>): PreparedPayload {
        val zipFile = File(context.cacheDir, "scanxfer_${System.currentTimeMillis()}.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            uris.forEachIndexed { index, uri ->
                val name = queryDisplayName(context.contentResolver, uri) ?: "file_${index + 1}"
                zip.putNextEntry(ZipEntry(sanitizeForZip(name)))
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open file stream" }
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
        return PreparedPayload(
            file = zipFile,
            displayName = zipFile.name,
            mimeType = "application/zip",
            sizeBytes = zipFile.length(),
            sha256 = sha256(zipFile),
        )
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    fun saveToDownloads(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val outFile = File(downloads, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            Uri.fromFile(outFile)
        }
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    private fun sanitizeForZip(name: String): String = sanitize(name)
}
