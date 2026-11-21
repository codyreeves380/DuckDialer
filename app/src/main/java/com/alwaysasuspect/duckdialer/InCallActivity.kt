package com.alwaysasuspect.duckdialer

import android.app.Activity
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Locale

class InCallActivity : Activity() {
    private lateinit var stateView: TextView
    private lateinit var numberView: TextView
    private lateinit var callerNameView: TextView
    private lateinit var avatarPhoto: ImageView
    private lateinit var avatarFallback: TextView
    private lateinit var durationView: TextView
    private lateinit var answerRow: LinearLayout
    private lateinit var controlsPanel: LinearLayout
    private lateinit var keypadPanel: LinearLayout
    private lateinit var muteButton: TextView
    private lateinit var speakerButton: TextView
    private lateinit var holdButton: TextView
    private lateinit var keypadButton: TextView
    private var muted = false
    private var speakerOn = false
    private var keypadVisible = false
    private val orange = 0xFFFF8A00.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val mutedText = 0xFFAAAAAA.toInt()
    private val panel = 0xFF202020.toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = 0xFF080808.toInt()
        window.navigationBarColor = 0xFF080808.toInt()
        buildUi()
        visibleActivity = WeakReference(this)
        refresh()
        handler.post(timerTick)
    }

    override fun onResume() {
        super.onResume()
        visibleActivity = WeakReference(this)
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTick)
        if (visibleActivity?.get() === this) visibleActivity = null
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            fillViewport = true
            setBackgroundColor(0xFF080808.toInt())
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(26))
            setBackgroundColor(0xFF080808.toInt())
        }

        root.addView(TextView(this).apply {
            text = "🦆  DUCKDIALER"
            textSize = 13f
            letterSpacing = 0.18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(orange)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(32)))

        val avatarFrame = FrameLayout(this).apply {
            background = circle(0xFF2B1D10.toInt())
            clipToOutline = true
        }
        avatarFallback = TextView(this).apply {
            text = "🦆"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        avatarPhoto = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            contentDescription = "Caller contact photo"
        }
        avatarFrame.addView(avatarFallback, FrameLayout.LayoutParams(-1, -1))
        avatarFrame.addView(avatarPhoto, FrameLayout.LayoutParams(-1, -1))
        root.addView(avatarFrame, LinearLayout.LayoutParams(dp(108), dp(108)).apply {
            topMargin = dp(28)
            bottomMargin = dp(12)
        })

        callerNameView = TextView(this).apply {
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            gravity = Gravity.CENTER
            maxLines = 2
            visibility = View.GONE
        }
        root.addView(callerNameView, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(4)
        })

        numberView = TextView(this).apply {
            textSize = 27f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        root.addView(numberView, LinearLayout.LayoutParams(-1, -2))

        stateView = TextView(this).apply {
            textSize = 14f
            setTextColor(orange)
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(stateView, LinearLayout.LayoutParams(-1, -2))

        durationView = TextView(this).apply {
            textSize = 13f
            setTextColor(mutedText)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(22))
        }
        root.addView(durationView, LinearLayout.LayoutParams(-1, -2))

        answerRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val decline = action("Decline", 0xFFB83232.toInt(), white, 58) {
            DuckDialerInCallService.activeCall?.reject(false, null)
            finish()
        }
        val answer = action("Answer", 0xFF238B55.toInt(), white, 58) {
            DuckDialerInCallService.activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            refresh()
        }
        answerRow.addView(decline, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            setMargins(dp(5), 0, dp(5), 0)
        })
        answerRow.addView(answer, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            setMargins(dp(5), 0, dp(5), 0)
        })
        root.addView(answerRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
            bottomMargin = dp(14)
        })

        controlsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF151515.toInt(), 22)
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        val firstRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        muteButton = control("◖", "Mute") {
            muted = !muted
            DuckDialerInCallService.serviceInstance?.setMuted(muted)
            refresh()
        }
        keypadButton = control("▦", "Keypad") {
            keypadVisible = !keypadVisible
            keypadPanel.visibility = if (keypadVisible) View.VISIBLE else View.GONE
            refresh()
        }
        firstRow.addView(muteButton, controlParams())
        firstRow.addView(keypadButton, controlParams())
        controlsPanel.addView(firstRow, LinearLayout.LayoutParams(-1, dp(92)))

        val secondRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        speakerButton = control("◉", "Speaker") {
            speakerOn = !speakerOn
            DuckDialerInCallService.serviceInstance?.setAudioRoute(
                if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            )
            refresh()
        }
        holdButton = control("Ⅱ", "Hold") {
            val call = DuckDialerInCallService.activeCall
            if (call?.state == Call.STATE_HOLDING) call.unhold() else call?.hold()
            refresh()
        }
        secondRow.addView(speakerButton, controlParams())
        secondRow.addView(holdButton, controlParams())
        controlsPanel.addView(secondRow, LinearLayout.LayoutParams(-1, dp(92)))
        root.addView(controlsPanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })

        keypadPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xFF151515.toInt(), 20)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            visibility = View.GONE
        }
        val keypadDigits = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        )
        keypadDigits.forEach { rowDigits ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            rowDigits.forEach { digit ->
                val key = TextView(this).apply {
                    text = digit
                    textSize = 23f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    background = rounded(panel, 18)
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener { _, event ->
                        val call = DuckDialerInCallService.activeCall
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                call?.playDtmfTone(digit.first())
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                call?.stopDtmfTone()
                                true
                            }
                            else -> true
                        }
                    }
                }
                row.addView(key, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    setMargins(dp(5), dp(4), dp(5), dp(4))
                })
            }
            keypadPanel.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(keypadPanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })

        val hangup = action("End call", 0xFFB83232.toInt(), white, 58) {
            DuckDialerInCallService.activeCall?.disconnect()
            finish()
        }
        root.addView(hangup, LinearLayout.LayoutParams(-1, dp(58)).apply {
            topMargin = dp(22)
        })

        root.addView(TextView(this).apply {
            text = "Call controls"
            textSize = 11f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun control(symbol: String, label: String, onTap: () -> Unit): TextView =
        TextView(this).apply {
            text = "$symbol\n$label"
            textSize = 13f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(white)
            background = rounded(panel, 18)
            setOnClickListener { onTap() }
        }

    private fun controlParams() = LinearLayout.LayoutParams(0, dp(78), 1f).apply {
        setMargins(dp(5), dp(5), dp(5), dp(5))
    }

    private fun action(label: String, color: Int, foreground: Int, height: Int, onTap: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(foreground)
            background = rounded(color, 30)
            minHeight = dp(height)
            setOnClickListener { onTap() }
        }

    private fun refresh() {
        val call = DuckDialerInCallService.activeCall
        val number = call?.details?.handle?.schemeSpecificPart
        numberView.text = number ?: "Unknown caller"
        updateCallerIdentity(number)
        val state = call?.state
        stateView.text = when (state) {
            Call.STATE_RINGING -> "INCOMING CALL"
            Call.STATE_DIALING -> "CALLING"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_ACTIVE -> "CONNECTED"
            Call.STATE_HOLDING -> "ON HOLD"
            Call.STATE_DISCONNECTED -> "CALL ENDED"
            else -> "CALL"
        }
        val ringing = state == Call.STATE_RINGING
        val inCall = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
        answerRow.visibility = if (ringing) View.VISIBLE else View.GONE
        controlsPanel.visibility = if (inCall) View.VISIBLE else View.GONE
        keypadPanel.visibility = if (inCall && keypadVisible) View.VISIBLE else View.GONE
        muteButton.text = if (muted) "◖\nMuted" else "◖\nMute"
        speakerButton.text = if (speakerOn) "◉\nSpeaker on" else "◉\nSpeaker"
        holdButton.text = if (state == Call.STATE_HOLDING) "▶\nResume" else "Ⅱ\nHold"
        keypadButton.text = if (keypadVisible) "▦\nHide keypad" else "▦\nKeypad"
        durationView.visibility = if (inCall) View.VISIBLE else View.GONE
        updateDuration()
    }

    private fun updateCallerIdentity(rawNumber: String?) {
        callerNameView.text = ""
        callerNameView.visibility = View.GONE
        avatarPhoto.setImageDrawable(null)
        avatarPhoto.visibility = View.GONE
        avatarFallback.visibility = View.VISIBLE
        if (rawNumber.isNullOrBlank()) return

        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)
                    val lookupKey = cursor.getString(1)
                    val displayName = cursor.getString(2)
                    if (!displayName.isNullOrBlank()) {
                        callerNameView.text = displayName
                        callerNameView.visibility = View.VISIBLE
                    }

                    val contactUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
                    if (contactUri != null) {
                        ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, contactUri, true)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            if (bitmap != null) {
                                avatarPhoto.setImageBitmap(bitmap)
                                avatarPhoto.visibility = View.VISIBLE
                                avatarFallback.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Contacts permission may be revoked while a call screen is open.
        } catch (_: Exception) {
            // Keep the number and DuckDialer fallback visible if lookup fails.
        }
    }

    private fun updateDuration() {
        if (!::durationView.isInitialized) return
        val call = DuckDialerInCallService.activeCall
        val connectedAt = call?.details?.connectTimeMillis ?: 0L
        if (connectedAt <= 0L) {
            durationView.text = ""
            return
        }
        val seconds = ((System.currentTimeMillis() - connectedAt).coerceAtLeast(0L) / 1000L)
        durationView.text = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
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
