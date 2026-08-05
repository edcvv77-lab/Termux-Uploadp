package com.aiham.scanxfer

import android.net.Uri

object UriCodec {
    fun encode(value: String): String = Uri.encode(value)
    fun decode(value: String?): String = Uri.decode(value.orEmpty())
}
