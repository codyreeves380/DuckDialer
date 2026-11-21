package com.alwaysasuspect.duckdialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("duckdialer_settings", MODE_PRIVATE) }
    private var selectedTab = "Keypad"
    private val orange = 0xFFFF8A00.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val muted = 0xFF9B9B9B.toInt()
    private val panel = 0xFF171717.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        numberView.text = intent?.data?.schemeSpecificPart.orEmpty()
        requestDialerRoleIfNeeded()
        requestRuntimePermissions()
        refreshTodayCount()
        showTab(selectedTab)
    }

    override fun onResume() {
        super.onResume()
        if (::countView.isInitialized) refreshTodayCount()
        if (::content.isInitialized) showTab(selectedTab)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF080808.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(12))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "Phone"
            textSize = 29f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        titleRow.addView(TextView(this).apply {
            text = "🦆"
            textSize = 25f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(titleRow, matchWrap())
        countView = TextView(this).apply {
            text = "TODAY  ·  — outgoing calls"
            textSize = 12f
            letterSpacing = 0.06f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(8))
        }
        header.addView(countView, matchWrap())
        root.addView(header, matchWrap())

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(12))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(12))
            setBackgroundColor(0xFF101010.toInt())
        }
        listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { tab ->
            nav.addView(TextView(this).apply {
                text = tab
                tag = tab
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(13), dp(2), dp(13))
                setOnClickListener { showTab(tab) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav, matchWrap())
        setContentView(root)
        updateNavigation()
    }

    private fun updateNavigation() {
        val root = content.parent?.parent as? LinearLayout ?: return
        val nav = root.getChildAt(root.childCount - 1) as? LinearLayout ?: return
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as TextView
            val active = item.tag == selectedTab
            item.setTextColor(if (active) orange else muted)
            item.typeface = Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif", Typeface.NORMAL
            )
        }
    }

    private fun showTab(tab: String) {
        selectedTab = tab
        if (!::content.isInitialized) return
        content.removeAllViews()
        updateNavigation()
        when (tab) {
            "Keypad" -> buildKeypad()
            "Recents" -> buildRecents()
            "Contacts" -> buildContacts()
            else -> buildFavorites()
        }
    }

    private fun buildKeypad() {
        sectionTitle("Enter number")
        numberView = TextView(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(white)
            minHeight = dp(62)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        content.addView(numberView, matchWrap())
        val digits = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        )
        val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
            "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
        digits.forEach { row ->
            val rowView = LinearLayout(this).apply { gravity = Gravity.CENTER }
            row.forEach { digit ->
                val key = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(panel, 100)
                    setOnClickListener { numberView.append(digit) }
                }
                key.addView(TextView(this).apply {
                    text = digit
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                })
                val sub = letters[digit]
                if (sub != null) key.addView(TextView(this).apply {
                    text = sub
                    textSize = 9f
                    letterSpacing = 0.16f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                })
                rowView.addView(key, LinearLayout.LayoutParams(0, dp(68), 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            content.addView(rowView, matchWrap())
        }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        actions.addView(actionText("⌫", panel, white, 22f) {
            val value = numberView.text.toString()
            if (value.isNotEmpty()) numberView.text = value.dropLast(1)
        }, LinearLayout.LayoutParams(dp(62), dp(56)).apply { setMargins(dp(8), 0, dp(8), 0) })
        actions.addView(actionText("Call", orange, 0xFF17100A.toInt(), 17f) {
            placeOutgoingCall()
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        content.addView(actions, matchWrap())

        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 18)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val hide = Switch(this).apply {
            text = "Hide caller ID (*67)"
            textSize = 14f
            setTextColor(white)
            isChecked = prefs.getBoolean("hide_caller_id", false)
            buttonTintList = android.content.res.ColorStateList.valueOf(orange)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("hide_caller_id", checked).apply()
            }
        }
        settings.addView(hide, matchWrap())
        settings.addView(TextView(this).apply {
            text = "For eligible outgoing calls. Carrier support may vary."
            textSize = 11f
            setTextColor(muted)
        }, matchWrap())
        content.addView(settings, matchWrap())
    }

    private fun buildRecents() {
        sectionTitle("Recent calls")
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            emptyMessage("Allow call-log access to see your recent calls.")
            return
        }
        val columns = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, columns, null, null,
                CallLog.Calls.DATE + " DESC")?.use { cursor ->
                var shown = 0
                while (cursor.moveToNext() && shown < 50) {
                    val name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty()
                    val type = cursor.getInt(2)
                    val date = cursor.getLong(3)
                    val duration = cursor.getLong(4)
                    val kind = when (type) {
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Declined"
                        else -> "Incoming"
                    }
                    val whenText = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(date))
                    val detail = kind + " · " + whenText +
                        if (duration > 0) " · " + duration + " sec" else ""
                    personRow(name.ifBlank { number.ifBlank { "Unknown caller" } },
                        detail, number) { placeCall(number) }
                    shown++
                }
                if (shown == 0) emptyMessage("Your recent calls will appear here.")
            }
        } catch (_: SecurityException) {
            emptyMessage("Call history is not available.")
        }
    }

    private fun buildContacts() {
        sectionTitle("Contacts")
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            emptyMessage("Allow contacts access to browse your contacts.")
            return
        }
        val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER)
        try {
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                columns, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")?.use { cursor ->
                var shown = 0
                while (cursor.moveToNext() && shown < 100) {
                    val name = cursor.getString(0).orEmpty()
                    val number = cursor.getString(1).orEmpty()
                    personRow(name.ifBlank { number }, number, number) { placeCall(number) }
                    shown++
                }
                if (shown == 0) emptyMessage("No contacts with phone numbers found.")
            }
        } catch (_: SecurityException) {
            emptyMessage("Contacts are not available.")
        }
    }

    private fun buildFavorites() {
        sectionTitle("Favorites")
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panel, 20)
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        card.addView(TextView(this).apply {
            text = "★"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(orange)
        }, matchWrap())
        card.addView(TextView(this).apply {
            text = "Your favorites live here"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(white)
            setPadding(0, dp(8), 0, dp(6))
        }, matchWrap())
        card.addView(TextView(this).apply {
            text = "Favorite contact management is coming next. Browse Contacts or use the keypad for now."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }, matchWrap())
        content.addView(card, matchWrap())
    }

    private fun sectionTitle(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            setPadding(0, dp(8), 0, dp(14))
        }, matchWrap())
    }

    private fun emptyMessage(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }, matchWrap())
    }

    private fun personRow(title: String, subtitle: String, number: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnClickListener { action() }
        }
        row.addView(TextView(this).apply {
            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(orange)
            background = rounded(0xFF302114.toInt(), 100)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(white)
            maxLines = 1
        }, matchWrap())
        labels.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(muted)
            maxLines = 1
        }, matchWrap())
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "☎"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(orange)
        }, LinearLayout.LayoutParams(dp(38), dp(42)))
        content.addView(row, matchWrap().apply { setMargins(0, 0, 0, dp(8)) })
    }

    private fun actionText(label: String, color: Int, foreground: Int,
        size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(foreground)
        background = rounded(color, 28)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun placeOutgoingCall() {
        var number = numberView.text.toString().trim()
        if (number.isBlank()) return
        val serviceCode = number.startsWith("*") || number.startsWith("#")
        val normalized = number.filter { it.isDigit() || it == '+' }
        val emergency = normalized == "911" || normalized == "112"
        if (prefs.getBoolean("hide_caller_id", false) && !serviceCode && !emergency) {
            number = "*67" + number
        }
        placeCall(number)
    }

    private fun placeCall(number: String) {
        try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: SecurityException) {
            Toast.makeText(this, "Set DuckDialer as the default Phone app first.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestDialerRoleIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager != null && !manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 200)
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
            countView.text = "TODAY  ·  allow call-log access"
            return
        }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.TYPE + " = ?"
        val args = arrayOf(start.toString(), CallLog.Calls.OUTGOING_TYPE.toString())
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                selection, args, null)?.use {
                countView.text = "TODAY  ·  " + it.count + " outgoing calls"
            }
        } catch (_: SecurityException) {
            countView.text = "TODAY  ·  call history unavailable"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
