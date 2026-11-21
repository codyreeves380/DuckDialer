package com.alwaysasuspect.duckdialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class DuckDialerInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        serviceInstance = this
        call.registerCallback(callCallback)
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        if (activeCall == call) activeCall = null
        super.onCallRemoved(call)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            InCallActivity.refreshIfVisible()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            InCallActivity.refreshIfVisible()
        }
    }

    companion object {
        @Volatile var activeCall: Call? = null
        @Volatile var serviceInstance: DuckDialerInCallService? = null
    }
}
