package com.alwaysasuspect.duckdialer

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

class InCallActivity : Activity() {
    private lateinit var stateView: TextView
    private lateinit var numberView: TextView
    private lateinit var answerButton: TextView
    private lateinit var declineButton: TextView
    private lateinit var muteButton: TextView
    private lateinit var speakerButton: TextView
    private lateinit var hangupButton: TextView
    private var muted = false
    private var speakerOn = false
    private val orange = 0xFFFF8A00.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val mutedText = 0xFFAAAAAA.toInt()
    private val panel = 0xFF202020.toInt()

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
            setPadding(dp(26), dp(34), dp(26), dp(30))
            setBackgroundColor(0xFF080808.toInt())
        }
        root.addView(TextView(this).apply {
            text = "🦆  DUCKDIALER"
            textSize = 14f
            letterSpacing = 0.16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(orange)
        }, LinearLayout.LayoutParams(-2, -2))

        root.addView(TextView(this).apply {
            text = "🦆"
            textSize = 54f
            gravity = Gravity.CENTER
            background = circle(0xFF302114.toInt())
        }, LinearLayout.LayoutParams(dp(112), dp(112)).apply { topMargin = dp(48) })

        numberView = TextView(this).apply {
            textSize = 27f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(6))
        }
        stateView = TextView(this).apply {
            textSize = 14f
            setTextColor(mutedText)
            gravity = Gravity.CENTER
        }
        root.addView(numberView, LinearLayout.LayoutParams(-1, -2))
        root.addView(stateView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(42) })

        val incoming = LinearLayout(this).apply { gravity = Gravity.CENTER }
        declineButton = action("Decline", 0xFFB83232.toInt(), white) {
            DuckDialerInCallService.activeCall?.reject(false, null)
            finish()
        }
        answerButton = action("Answer", 0xFF238B55.toInt(), white) {
            DuckDialerInCallService.activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            refresh()
        }
        incoming.addView(declineButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        incoming.addView(answerButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        root.addView(incoming, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        muteButton = action("Mute", panel, white) {
            muted = !muted
            DuckDialerInCallService.serviceInstance?.setMuted(muted)
            refresh()
        }
        speakerButton = action("Speaker", panel, white) {
            speakerOn = !speakerOn
            DuckDialerInCallService.serviceInstance?.setAudioRoute(
                if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            )
            refresh()
        }
        controls.addView(muteButton, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        controls.addView(speakerButton, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(5), 0, dp(5), 0) })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        hangupButton = action("End call", 0xFFB83232.toInt(), white) {
            DuckDialerInCallService.activeCall?.disconnect()
            finish()
        }
        root.addView(hangupButton, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(30) })
        setContentView(root)
    }

    private fun action(label: String, color: Int, foreground: Int, onTap: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(foreground)
            background = rounded(color, 30)
            setOnClickListener { onTap() }
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
        val ringing = state == Call.STATE_RINGING
        answerButton.visibility = if (ringing) View.VISIBLE else View.GONE
        declineButton.visibility = if (ringing) View.VISIBLE else View.GONE
        muteButton.visibility = if (state == Call.STATE_ACTIVE) View.VISIBLE else View.GONE
        speakerButton.visibility = if (state == Call.STATE_ACTIVE) View.VISIBLE else View.GONE
        hangupButton.visibility = if (ringing) View.GONE else View.VISIBLE
        muteButton.text = if (muted) "Unmute" else "Mute"
        speakerButton.text = if (speakerOn) "Earpiece" else "Speaker"
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), orange)
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
