package com.alwaysasuspect.duckdialer

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class InCallActivity : Activity() {
    private lateinit var stateView: TextView
    private lateinit var numberView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(0xFF080808.toInt())
        }
        val brand = TextView(this).apply {
            text = "🦆 DUCKDIALER"
            textSize = 20f
            setTextColor(0xFFFF8A00.toInt())
        }
        numberView = TextView(this).apply {
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 32, 0, 12)
        }
        stateView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFBBBBBB.toInt())
        }
        root.addView(brand)
        root.addView(numberView)
        root.addView(stateView)

        val answer = Button(this).apply {
            text = "ANSWER"
            setOnClickListener { DuckDialerInCallService.activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY); refresh() }
        }
        val mute = Button(this).apply {
            text = "MUTE"
            setOnClickListener {
                val service = DuckDialerInCallService.serviceInstance
                service?.setMuted(true)
            }
        }
        val speaker = Button(this).apply {
            text = "SPEAKER"
            setOnClickListener {
                val service = DuckDialerInCallService.serviceInstance
                service?.setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER)
            }
        }
        val hangup = Button(this).apply {
            text = "END CALL"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFCF3030.toInt()))
            setOnClickListener { DuckDialerInCallService.activeCall?.disconnect(); finish() }
        }
        listOf(answer, mute, speaker, hangup).forEach {
            root.addView(it, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 12 })
        }
        setContentView(root)
    }

    private fun refresh() {
        val call = DuckDialerInCallService.activeCall
        numberView.text = call?.details?.handle?.schemeSpecificPart ?: "Unknown caller"
        stateView.text = when (call?.state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING -> "Calling…"
            Call.STATE_CONNECTING -> "Connecting…"
            Call.STATE_ACTIVE -> "Connected"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> "Call"
        }
    }

    companion object {
        @Volatile private var visible = false
        fun refreshIfVisible() {
            // The screen refreshes on resume; later milestones will add lifecycle-safe state observation.
        }
    }
}
