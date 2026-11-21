package com.alwaysasuspect.duckdialer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
    private var recentFilter = "All"
    private val white = 0xFFF5F5F5.toInt()
    private val muted = 0xFF9B9B9B.toInt()
    private val panel = 0xFF171717.toInt()
    private val orange: Int get() = prefs.getInt("accent_color", 0xFFFF8A00.toInt())
    private val favoriteNumbers: MutableSet<String>
        get() = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
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
            setPadding(dp(22), dp(18), dp(22), dp(10))
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "DUCKDIALER"
                textSize = 10f
                letterSpacing = 0.18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(orange)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Phone"
                textSize = 28f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(white)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(actionText("⚙", panel, white, 22f) { showTab("Settings") },
            LinearLayout.LayoutParams(dp(46), dp(46)))
        titleRow.addView(TextView(this).apply {
            text = "🦆"
            textSize = 25f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(46)))
        header.addView(titleRow, matchWrap())
        countView = TextView(this).apply {
            text = "TODAY  ·  — outgoing calls"
            textSize = 12f
            letterSpacing = 0.06f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(6))
        }
        header.addView(countView, matchWrap())
        root.addView(header, matchWrap())

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(12))
        }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(10))
            setBackgroundColor(0xFF101010.toInt())
        }
        listOf("Favorites" to "★", "Recents" to "◷", "Contacts" to "☷", "Keypad" to "▦").forEach { (tab, glyph) ->
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = tab
                setPadding(0, dp(5), 0, dp(5))
                addView(TextView(this@MainActivity).apply {
                    text = glyph
                    textSize = 19f
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    text = tab
                    textSize = 10f
                    gravity = Gravity.CENTER
                })
                setOnClickListener { showTab(tab) }
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav, matchWrap())
        setContentView(root)
        updateNavigation()
    }

    private fun updateNavigation() {
        val root = content.parent?.parent as? LinearLayout ?: return
        val nav = root.getChildAt(root.childCount - 1) as? LinearLayout ?: return
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as LinearLayout
            val active = item.tag == selectedTab
            for (j in 0 until item.childCount) {
                (item.getChildAt(j) as? TextView)?.setTextColor(if (active) orange else muted)
            }
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
            "Settings" -> buildSettings()
            else -> buildFavorites()
        }
    }

    private fun buildKeypad() {
        sectionTitle("Enter number")
        numberView = TextView(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(white)
            minHeight = dp(58)
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        content.addView(numberView, matchWrap())
        val digits = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#"))
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
                letters[digit]?.let { sub ->
                    key.addView(TextView(this).apply {
                        text = sub
                        textSize = 9f
                        letterSpacing = 0.16f
                        gravity = Gravity.CENTER
                        setTextColor(muted)
                    })
                }
                rowView.addView(key, LinearLayout.LayoutParams(0, dp(65), 1f).apply {
                    setMargins(dp(5), dp(4), dp(5), dp(4))
                })
            }
            content.addView(rowView, matchWrap())
        }
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(12))
        }
        actions.addView(actionText("⌫", panel, white, 22f) {
            val value = numberView.text.toString()
            if (value.isNotEmpty()) numberView.text = value.dropLast(1)
        }, LinearLayout.LayoutParams(dp(62), dp(54)).apply { setMargins(dp(8), 0, dp(8), 0) })
        actions.addView(actionText("Call", orange, 0xFF17100A.toInt(), 17f) {
            placeOutgoingCall()
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        content.addView(actions, matchWrap())
        val hint = TextView(this).apply {
            text = if (prefs.getBoolean("hide_caller_id", false)) "Caller ID hidden for eligible calls" else "Caller ID shown"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        content.addView(hint, matchWrap())
        content.addView(actionText("Caller ID settings  ·  *67", panel, white, 13f) {
            showTab("Settings")
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
    }

    private fun buildRecents() {
        sectionTitle("Recent calls")
        val filters = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) }
        listOf("All", "Missed").forEach { filter ->
            filters.addView(actionText(filter, if (recentFilter == filter) orange else panel,
                if (recentFilter == filter) 0xFF17100A.toInt() else white, 12f) {
                recentFilter = filter
                showTab("Recents")
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        content.addView(filters, matchWrap())
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            emptyMessage("Allow call-log access to see your recent calls.")
            return
        }
        val columns = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val selection = if (recentFilter == "Missed") CallLog.Calls.TYPE + " = ?" else null
        val args = if (recentFilter == "Missed") arrayOf(CallLog.Calls.MISSED_TYPE.toString()) else null
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, columns, selection, args,
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
                    val stamp = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(date))
                    val detail = kind + " · " + stamp + if (duration > 0) " · " + duration + " sec" else ""
                    personRow(name.ifBlank { number.ifBlank { "Unknown caller" } }, detail, number,
                        favoriteAction = number.isNotBlank()) { placeCall(number) }
                    shown++
                }
                if (shown == 0) emptyMessage(if (recentFilter == "Missed") "No missed calls. Lovely." else "Your recent calls will appear here.")
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
        val search = EditText(this).apply {
            hint = "Search contacts"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(panel, 14)
            setPadding(dp(15), dp(11), dp(15), dp(11))
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results, matchWrap())
        fun loadContacts(term: String) {
            results.removeAllViews()
            val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = if (term.isBlank()) null else
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " +
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?"
            val pattern = "%$term%"
            val args = if (selection == null) null else arrayOf(pattern, pattern)
            try {
                contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    columns, selection, args,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")?.use { cursor ->
                    var shown = 0
                    while (cursor.moveToNext() && shown < 150) {
                        val name = cursor.getString(0).orEmpty()
                        val number = cursor.getString(1).orEmpty()
                        personRow(name.ifBlank { number }, number, number, true) { placeCall(number) }
                        shown++
                    }
                    if (shown == 0) emptyMessage("No matching contacts found.")
                }
            } catch (_: SecurityException) {
                emptyMessage("Contacts are not available.")
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = loadContacts(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        loadContacts("")
    }

    private fun buildFavorites() {
        sectionTitle("Favorites")
        val numbers = favoriteNumbers.sorted()
        if (numbers.isEmpty()) {
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
                text = "Tap the star beside someone in Contacts to add them here."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(muted)
            }, matchWrap())
            content.addView(card, matchWrap())
        } else {
            numbers.forEach { number ->
                personRow(number, "Favorite contact", number, true) { placeCall(number) }
            }
        }
    }

    private fun buildSettings() {
        sectionTitle("Settings")
        val callerCard = settingsCard()
        val hide = Switch(this).apply {
            text = "Hide caller ID (*67)"
            textSize = 15f
            setTextColor(white)
            isChecked = prefs.getBoolean("hide_caller_id", false)
            buttonTintList = ColorStateList.valueOf(orange)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("hide_caller_id", checked).apply()
            }
        }
        callerCard.addView(hide, matchWrap())
        callerCard.addView(smallText("Prepends *67 to eligible outgoing calls. Carrier support varies."), matchWrap())
        content.addView(callerCard, matchWrap())

        sectionTitle("Accent color")
        val colors = listOf("Orange" to 0xFFFF8A00.toInt(), "Blue" to 0xFF3998FF.toInt(),
            "Purple" to 0xFFB078FF.toInt(), "Green" to 0xFF39C98A.toInt())
        colors.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            pair.forEach { (name, color) ->
                row.addView(actionText(if (orange == color) "✓  $name" else name, color,
                    0xFF101010.toInt(), 13f) {
                    prefs.edit().putInt("accent_color", color).apply()
                    showTab("Settings")
                }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            content.addView(row, matchWrap())
        }
        val about = settingsCard()
        about.addView(TextView(this).apply {
            text = "DuckDialer"
            textSize = 17f
            setTextColor(white)
        }, matchWrap())
        about.addView(smallText("A dark, duck-approved dialer. Set DuckDialer as your default Phone app to handle calls."), matchWrap())
        content.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun settingsCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel, 18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun smallText(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(muted)
        setPadding(0, dp(5), 0, 0)
    }

    private fun sectionTitle(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            setPadding(0, dp(8), 0, dp(13))
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

    private fun personRow(title: String, subtitle: String, number: String,
        favoriteAction: Boolean = false, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(12), dp(10), dp(10), dp(10))
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
            setPadding(dp(12), 0, dp(6), 0)
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 14f
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
        if (favoriteAction && number.isNotBlank()) {
            row.addView(actionText(if (favoriteNumbers.contains(number)) "★" else "☆",
                panel, orange, 21f) {
                val updated = favoriteNumbers
                if (!updated.add(number)) updated.remove(number)
                prefs.edit().putStringSet("favorites", updated).apply()
                showTab(selectedTab)
            }, LinearLayout.LayoutParams(dp(38), dp(42)))
        }
        row.addView(actionText("☎", panel, orange, 19f) { action() },
            LinearLayout.LayoutParams(dp(38), dp(42)))
        row.setOnClickListener { action() }
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
        if (prefs.getBoolean("hide_caller_id", false) && !serviceCode && !emergency) number = "*67$number"
        placeCall(number)
    }

    private fun placeCall(number: String) {
        if (number.isBlank()) return
        try {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (_: SecurityException) {
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
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.TYPE + " = ?"
        val args = arrayOf(start.toString(), CallLog.Calls.OUTGOING_TYPE.toString())
        try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                selection, args, null)?.use { countView.text = "TODAY  ·  ${it.count} outgoing calls" }
        } catch (_: SecurityException) {
            countView.text = "TODAY  ·  call history unavailable"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
