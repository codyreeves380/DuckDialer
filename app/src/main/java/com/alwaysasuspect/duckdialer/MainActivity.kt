package com.alwaysasuspect.duckdialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var numberView: TextView
    private lateinit var countView: TextView
    private lateinit var hideCallerIdSwitch: Switch
    private val prefs by lazy { getSharedPreferences("duckdialer_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        numberView.text = intent?.data?.schemeSpecificPart.orEmpty()
        requestDialerRoleIfNeeded()
        requestRuntimePermissions()
        refreshTodayCount()
    }

    override fun onResume() {
        super.onResume()
        if (::countView.isInitialized) refreshTodayCount()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(16))
            setBackgroundColor(0xFF080808.toInt())
        }

        val brand = TextView(this).apply {
            text = "🦆  DUCKDIALER"
            textSize = 22f
            setTextColor(0xFFFF8A00.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(brand, matchWrap())

        countView = TextView(this).apply {
            text = "Calls made today: —"
            textSize = 14f
            setTextColor(0xFFB8B8B8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(countView, matchWrap())

        numberView = TextView(this).apply {
            text = ""
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(18))
            minHeight = dp(64)
        }
        root.addView(numberView, matchWrap())

        val grid = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        )
        grid.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { digit ->
                val key = Button(this).apply {
                    text = digit
                    textSize = 24f
                    setTextColor(0xFFFF8A00.toInt())
                    setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF202020.toInt()))
                    setOnClickListener { numberView.append(digit) }
                }
                val lp = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
                rowLayout.addView(key, lp)
            }
            root.addView(rowLayout, matchWrap())
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val back = Button(this).apply {
            text = "⌫"
            setOnClickListener {
                val s = numberView.text.toString()
                if (s.isNotEmpty()) numberView.text = s.dropLast(1)
            }
        }
        val call = Button(this).apply {
            text = "CALL"
            textSize = 18f
            setTextColor(0xFF101010.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A00.toInt()))
            setOnClickListener { placeOutgoingCall() }
        }
        actions.addView(back, LinearLayout.LayoutParams(0, dp(58), 1f))
        actions.addView(call, LinearLayout.LayoutParams(0, dp(58), 2f))
        root.addView(actions, matchWrap())

        hideCallerIdSwitch = Switch(this).apply {
            text = "Hide caller ID for all calls (*67)"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            isChecked = prefs.getBoolean("hide_caller_id", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("hide_caller_id", checked).apply()
            }
        }
        root.addView(hideCallerIdSwitch, matchWrap())

        val tabs = TextView(this).apply {
            text = "DIALER                         CONTACTS                         RECENTS"
            textSize = 11f
            setTextColor(0xFFFF8A00.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(tabs, matchWrap())
        setContentView(root)
    }

    private fun placeOutgoingCall() {
        var number = numberView.text.toString().trim()
        if (number.isBlank()) return

        val isServiceCode = number.startsWith("*") || number.startsWith("#")
        val normalized = number.filter { it.isDigit() || it == '+' }
        val isEmergency = normalized == "911" || normalized == "112"

        if (prefs.getBoolean("hide_caller_id", false) && !isServiceCode && !isEmergency) {
            number = "*67$number"
        }

        try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: SecurityException) {
            Toast.makeText(this, "Set DuckDialer as the default Phone app first.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestDialerRoleIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 200)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 201)
    }

    private fun refreshTodayCount() {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            countView.text = "Calls made today: permission needed"
            return
        }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val projection = arrayOf(CallLog.Calls._ID)
        val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} = ?"
        val args = arrayOf(start.toString(), CallLog.Calls.OUTGOING_TYPE.toString())
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, args, null)?.use {
                countView.text = "Calls made today: ${it.count}"
            }
        } catch (_: SecurityException) {
            countView.text = "Calls made today: unavailable"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
