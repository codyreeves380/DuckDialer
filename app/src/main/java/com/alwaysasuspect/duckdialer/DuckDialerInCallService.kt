package com.alwaysasuspect.duckdialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import java.util.concurrent.CopyOnWriteArrayList

class DuckDialerInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        serviceInstance = this
        calls.addIfAbsent(call)
        call.registerCallback(callCallback)
        selectBestCall()

        // The app is the selected default dialer. Keep the call surface synchronized
        // with Telecom's full call list rather than assuming only one call exists.
        try {
            startActivity(Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        } catch (_: Exception) {
            // Telecom remains authoritative; the UI can be reopened from the app.
        }
        InCallActivity.refreshIfVisible()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callCallback)
        calls.remove(call)
        selectBestCall()
        if (calls.isEmpty()) InCallActivity.finishIfVisible()
        else InCallActivity.refreshIfVisible()
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        InCallActivity.refreshIfVisible()
    }

    private fun selectBestCall() {
        // Prefer ringing calls, then active, dialing/connecting, and finally held calls.
        activeCall = calls.minByOrNull { call ->
            when (call.state) {
                Call.STATE_RINGING -> 0
                Call.STATE_ACTIVE -> 1
                Call.STATE_DIALING, Call.STATE_CONNECTING -> 2
                Call.STATE_HOLDING -> 3
                else -> 4
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            selectBestCall()
            InCallActivity.refreshIfVisible()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            selectBestCall()
            InCallActivity.refreshIfVisible()
        }
    }

    companion object {
        private val calls = CopyOnWriteArrayList<Call>()
        @Volatile var activeCall: Call? = null
        @Volatile var serviceInstance: DuckDialerInCallService? = null
    }
}
