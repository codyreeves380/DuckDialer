package com.alwaysasuspect.duckdialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import java.util.concurrent.CopyOnWriteArrayList

class DuckDialerInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CALL_CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call alerts for DuckDialer"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        serviceInstance = this
        calls.addIfAbsent(call)
        call.registerCallback(callCallback)
        selectBestCall()
        updateIncomingCallNotification()

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
        updateIncomingCallNotification()
        if (calls.isEmpty()) InCallActivity.finishIfVisible()
        else InCallActivity.refreshIfVisible()
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        InCallActivity.refreshIfVisible()
    }

    private fun updateIncomingCallNotification() {
        val ringingCall = calls.firstOrNull { it.state == Call.STATE_RINGING }
        val manager = getSystemService(NotificationManager::class.java)
        if (ringingCall == null) {
            manager.cancel(CALL_NOTIFICATION_ID)
            return
        }
        val number = ringingCall.details?.handle?.schemeSpecificPart ?: "Unknown caller"
        val launchIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, CALL_NOTIFICATION_ID, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CALL_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming call")
            .setContentText(number)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        manager.notify(CALL_NOTIFICATION_ID, notification)
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
            updateIncomingCallNotification()
            InCallActivity.refreshIfVisible()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            selectBestCall()
            InCallActivity.refreshIfVisible()
        }
    }

    companion object {
        private const val CALL_CHANNEL_ID = "duckdialer_incoming_calls"
        private const val CALL_NOTIFICATION_ID = 7101
        private val calls = CopyOnWriteArrayList<Call>()
        @Volatile var activeCall: Call? = null
        @Volatile var serviceInstance: DuckDialerInCallService? = null
    }
}
