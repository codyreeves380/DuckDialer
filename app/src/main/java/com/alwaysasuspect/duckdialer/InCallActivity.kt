package com.alwaysasuspect.duckdialer

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

class InCallActivity : Activity() {
    private lateinit var stateView: TextView
    private lateinit var numberView: TextView
    private lateinit var answerButton: Button
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button
    private var muted = false
    private var speakerOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        visibleActivity = WeakReference(this)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        visibleActivity = WeakReference(this)
        refresh()
    }

    override fun onDestroy() {
        if (visibleActivity?.get() === this) visibleActivity = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(40))
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
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, dp(12))
        }
        stateView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(brand)
        root.addView(numberView)
        root.addView(stateView)

        answerButton = makeButton("ANSWER") {
            DuckDialerInCallService.activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            refresh()
        }
        muteButton = makeButton("MUTE") {
            muted = !muted
            DuckDialerInCallService.serviceInstance?.setMuted(muted)
            refresh()
        }
        speakerButton = makeButton("SPEAKER") {
            speakerOn = !speakerOn
            DuckDialerInCallService.serviceInstance?.setAudioRoute(
                if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            )
            refresh()
        }
        val hangup = makeButton("END CALL") {
            DuckDialerInCallService.activeCall?.disconnect()
            finish()
        }.apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFCF3030.toInt()))
        }

        listOf(answerButton, muteButton, speakerButton, hangup).forEach {
            root.addView(it, LinearLayout.LayoutParams(-1, dp(58)).apply {
                topMargin = dp(12)
            })
        }
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun refresh() {
        val call = DuckDialerInCallService.activeCall
        numberView.text = call?.details?.handle?.schemeSpecificPart ?: "Unknown caller"
        val state = call?.state
        stateView.text = when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING -> "Calling…"
            Call.STATE_CONNECTING -> "Connecting…"
            Call.STATE_ACTIVE -> "Connected"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> "Call"
        }
        answerButton.visibility = if (state == Call.STATE_RINGING) View.VISIBLE else View.GONE
        muteButton.visibility = if (state == Call.STATE_ACTIVE) View.VISIBLE else View.GONE
        speakerButton.visibility = if (state == Call.STATE_ACTIVE) View.VISIBLE else View.GONE
        muteButton.text = if (muted) "UNMUTE" else "MUTE"
        speakerButton.text = if (speakerOn) "EARPIECE" else "SPEAKER"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        @Volatile private var visibleActivity: WeakReference<InCallActivity>? = null

        fun refreshIfVisible() {
            visibleActivity?.get()?.runOnUiThread { visibleActivity?.get()?.refresh() }
        }

        fun finishIfVisible() {
            visibleActivity?.get()?.runOnUiThread { visibleActivity?.get()?.finish() }
        }
    }
}
