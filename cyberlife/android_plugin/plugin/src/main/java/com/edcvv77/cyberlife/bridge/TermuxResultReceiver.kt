package com.edcvv77.cyberlife.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CyberLifeBridge.current?.handleTermuxResult(intent)
    }
}
