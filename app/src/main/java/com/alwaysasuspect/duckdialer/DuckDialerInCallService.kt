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

        // Telecom binds this service for active calls. Launch the in-call surface;
        // the activity also refreshes from the current Call object on resume.
        startActivity(Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        InCallActivity.refreshIfVisible()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        if (activeCall == call) {
            activeCall = null
            InCallActivity.finishIfVisible()
        }
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        InCallActivity.refreshIfVisible()
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
