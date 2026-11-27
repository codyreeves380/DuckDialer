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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.content.ClipData
import android.view.DragEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var numberView: EditText
    private lateinit var countView: TextView
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("duckdialer_settings", MODE_PRIVATE) }
    private var selectedTab = "Home"
    private var selectedSettingsSection = "General"
    private var recentFilter = "All"
    private var recentSearchTerm = ""
    private var favoritesSearchTerm = ""
    private val white = 0xFFF5F5F5.toInt()
    private val muted = 0xFF9B9B9B.toInt()
    private val panel = 0xFF171717.toInt()
    private val orange: Int get() = prefs.getInt("accent_color", 0xFFFF8A00.toInt())
    private val favoriteNumbers: MutableSet<String>
        get() = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        buildScreen()
        requestDialerRoleIfNeeded()
        requestRuntimePermissions()
        refreshTodayCount()
        showTab(selectedTab)
    }

    private fun configureEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
        window.statusBarColor = 0xFF080808.toInt()
        window.navigationBarColor = 0xFF080808.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
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
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    android.graphics.Insets.of(insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop, insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom)
                }
                view.setPadding(bars.left, bars.top, bars.right, 0)
                insets
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(10))
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (prefs.getBoolean("home_brand_label", true)) addView(TextView(this@MainActivity).apply {
                text = prefs.getString("home_greeting", "DUCKDIALER") ?: "DUCKDIALER"
                textSize = 10f
                letterSpacing = 0.18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(orange)
            })
            addView(TextView(this@MainActivity).apply {
                text = if (selectedTab == "Home") "Home" else selectedTab
                tag = "duckdialer_page_title"
                textSize = 28f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(white)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(actionText("🦆", panel, white, prefs.getInt("home_duck_size", 25).toFloat()) { showTab("Settings") },
            LinearLayout.LayoutParams(dp(48), dp(46)))
        header.addView(titleRow, matchWrap())
        countView = TextView(this).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            setPadding(dp(16), dp(if (prefs.getBoolean("counter_compact", false)) 10 else 16),
                dp(16), dp(if (prefs.getBoolean("counter_compact", false)) 10 else 16))
            background = counterBackground()
            minHeight = dp(if (prefs.getBoolean("counter_compact", false)) 64 else 104)
        }
        countView.visibility = if (selectedTab == "Home" && prefs.getBoolean("home_show_count", true)) View.VISIBLE else View.GONE
        header.addView(countView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
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
        val glyphs = mapOf("Favorites" to "★", "Recents" to "◷", "Contacts" to "☷", "Keypad" to "▦")
        visibleTabs().forEach { tab ->
            val glyph = glyphs[tab] ?: "•"
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
        nav.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(dp(6), dp(7), dp(6), dp(10) + bottomInset)
            insets
        }
        root.addView(nav, matchWrap())
        setContentView(root)
        updateNavigation()
    }

    private fun visibleTabs(): List<String> {
        val defaults = listOf("Favorites", "Recents", "Contacts", "Keypad")
        val order = (prefs.getString("tab_order", defaults.joinToString("|")) ?: defaults.joinToString("|"))
            .split("|").filter { it in defaults }.distinct()
        return (order + defaults.filterNot { it in order }).filter {
            prefs.getBoolean("tab_visible_$it", true)
        }.ifEmpty { listOf("Keypad") }
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
        if (::countView.isInitialized) {
            countView.visibility = if (tab == "Home" && prefs.getBoolean("home_show_count", true)) View.VISIBLE else View.GONE
        }
        (window.decorView.findViewWithTag("duckdialer_page_title") as? TextView)?.text = if (tab == "Home") "Home" else tab
        when (tab) {
            "Home" -> buildHome()
            "Keypad" -> buildKeypad()
            "Recents" -> buildRecents()
            "Contacts" -> buildContacts()
            "Settings" -> buildSettings()
            "Home Customization" -> buildHomeCustomization()
            else -> buildFavorites()
        }
    }

    private fun buildHome() {
        // Workbench Home: persistent page heading and daily counter, then recent calls.
        buildRecents()
    }

    private fun buildKeypad() {
        val compactCounter = TextView(this).apply {
            text = countView.text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(panel, 14)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        content.addView(compactCounter, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(10) })
        sectionTitle("Enter number")
        content.getChildAt(content.childCount - 1)?.tag = "entry"
        numberView = EditText(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(white)
            setHintTextColor(muted)
            hint = "Enter or paste number"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            showSoftInputOnFocus = false
            imeOptions = EditorInfo.IME_ACTION_GO
            minHeight = dp(58)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = rounded(panel, 16)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        numberView.tag = "entry"
        content.addView(numberView, matchWrap())
        val digits = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#"))
        val letters = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
            "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
        val keypadRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
                if (prefs.getBoolean("home_keypad_letters", true)) {
                    letters[digit]?.let { sub ->
                        key.addView(TextView(this).apply {
                            text = sub
                            textSize = 9f
                            letterSpacing = 0.16f
                            gravity = Gravity.CENTER
                            setTextColor(muted)
                        })
                    }
                }
                rowView.addView(key, LinearLayout.LayoutParams(0, dp(prefs.getInt("home_key_height", 65)), 1f).apply {
                    setMargins(dp(5), dp(4), dp(5), dp(4))
                })
            }
            keypadRows.addView(rowView, matchWrap())
        }
        keypadRows.tag = "keypad"
        if (prefs.getBoolean("home_show_keypad", true)) content.addView(keypadRows, matchWrap())
        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(12))
        }
        actions.addView(actionText("⌫", panel, white, 22f) {
            val value = numberView.text.toString()
            if (value.isNotEmpty()) numberView.setText(value.dropLast(1))
        }, LinearLayout.LayoutParams(dp(62), dp(54)).apply { setMargins(dp(8), 0, dp(8), 0) })
        val callButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(orange, 30)
            isClickable = true
            isFocusable = true
            contentDescription = "Place call"
            setOnClickListener { placeOutgoingCall() }
            val callIcon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_reed_duck_call)
                contentDescription = "Reed duck call"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(callIcon, LinearLayout.LayoutParams(dp(58), dp(34)))
            addView(TextView(this@MainActivity).apply {
                text = "CALL"
                textSize = 16f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(0xFF17100A.toInt())
                setPadding(dp(8), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(-2, -1))
        }
        actions.addView(callButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            setMargins(dp(8), 0, dp(8), 0)
        })
        actions.tag = "call"
        content.addView(actions, matchWrap())
        val hint = TextView(this).apply {
            text = if (prefs.getBoolean("hide_caller_id", false)) "Caller ID hidden for eligible calls" else "Caller ID shown"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        hint.tag = "caller_status"
        if (prefs.getBoolean("home_show_caller_hint", true)) content.addView(hint, matchWrap())
        if (prefs.getBoolean("home_show_caller_shortcut", true)) {
            val shortcut = actionText("Caller ID settings  ·  *67", panel, white, 13f) { showTab("Settings") }
            shortcut.tag = "caller_shortcut"
            content.addView(shortcut, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }
        applyHomeSectionOrder()
    }

    private fun buildRecents() {
        val homeCallLog = selectedTab == "Home"
        sectionTitle("Recent calls")
        val search = EditText(this).apply {
            hint = "Search contacts"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(panel, 14)
            setPadding(dp(15), dp(11), dp(15), dp(11))
            setText(recentSearchTerm)
            tag = "recent_search"
        }
        search.visibility = if (homeCallLog) View.GONE else View.VISIBLE
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
        val filters = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            visibility = if (homeCallLog) View.GONE else View.VISIBLE
        }
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
                    val term = recentSearchTerm.trim()
                    if (term.isNotEmpty() && !name.contains(term, true) && !number.contains(term, true)) continue
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
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recentSearchTerm = s?.toString().orEmpty()
                val cursor = search.selectionStart
                showTab(if (selectedTab == "Home") "Home" else "Recents")
                content.findViewWithTag<EditText>("recent_search")?.let {
                    it.setSelection(cursor.coerceIn(0, it.text.length))
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
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
            isSingleLine = true
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
                        personRow(name.ifBlank { number }, number, number, true, results) { placeCall(number) }
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
        sectionTitle("Favorite contacts")
        val search = EditText(this).apply {
            hint = "Search contacts"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(panel, 14)
            setPadding(dp(15), dp(11), dp(15), dp(11))
            setText(favoritesSearchTerm)
            tag = "favorites_search"
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })
        val numbers = favoriteNumbers.sorted().filter { number ->
            favoritesSearchTerm.isBlank() || number.contains(favoritesSearchTerm, true) ||
                contactNameForNumber(number).contains(favoritesSearchTerm, true)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                favoritesSearchTerm = s?.toString().orEmpty()
                val cursor = search.selectionStart
                showTab("Favorites")
                content.findViewWithTag<EditText>("favorites_search")?.let {
                    it.setSelection(cursor.coerceIn(0, it.text.length))
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
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



    private fun contactNameForNumber(number: String): String {
        if (number.isBlank() || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun buildHomeCustomization() {
        sectionTitle("Customize Home")
        content.addView(smallText("Changes save automatically."), matchWrap())
        val display = settingsCard()
        display.addView(smallText("HOME DISPLAY"), matchWrap())
        addHomeSwitch(display, "home_show_count", "Show today's call count", true)
        addHomeSwitch(display, "home_brand_label", "Show DuckDialer label", true)
        addHomeSwitch(display, "home_keypad_letters", "Show keypad letter hints", true)
        content.addView(display, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        sectionTitle("Daily call counter")
        val counter = settingsCard()
        counter.addView(smallText("COUNTER STYLE"), matchWrap())
        addCounterStyleRow(counter)
        addHomeSwitch(counter, "counter_show_label", "Show Calls Made Today label", true)
        addHomeSwitch(counter, "counter_show_icon", "Show flame icon", true)
        addHomeSwitch(counter, "counter_show_comparison", "Compare with yesterday", true)
        addHomeSwitch(counter, "counter_animation", "Animate count updates", true)
        addHomeSwitch(counter, "counter_compact", "Use compact counter height", false)
        counter.addView(smallText("Accent colors follow Appearance settings. The count is based on outgoing calls in your call history."), matchWrap())
        content.addView(counter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        sectionTitle("Keypad size")
        val sizes = settingsCard()
        addChoiceRow(sizes, listOf("Compact" to 58, "Comfortable" to 65, "Large" to 74), "home_key_height", 65)
        content.addView(sizes, matchWrap())

        sectionTitle("Home widgets")
        val widgets = settingsCard()
        addHomeSwitch(widgets, "home_show_caller_hint", "Show caller ID status", true)
        addHomeSwitch(widgets, "home_show_caller_shortcut", "Show caller ID settings shortcut", true)
        addHomeSwitch(widgets, "home_show_keypad", "Show keypad", true)
        content.addView(widgets, matchWrap())

        sectionTitle("Home section order")
        content.addView(smallText("Drag a section by its handle, or use the arrows. The preview updates to match."), matchWrap())
        addHomeSectionOrderEditor()
        sectionTitle("Live layout preview")
        val preview = settingsCard()
        preview.addView(smallText("YOUR HOME SCREEN"), matchWrap())
        preview.addView(TextView(this).apply {
            text = homeSectionOrder().joinToString("   →   ") { homeSectionLabel(it) }
            textSize = 12f
            setTextColor(white)
            setPadding(0, dp(10), 0, dp(8))
        }, matchWrap())
        preview.addView(smallText("Key size: ${prefs.getInt("home_key_height", 65)}dp  ·  Duck: ${prefs.getInt("home_duck_size", 25)}dp"), matchWrap())
        content.addView(preview, matchWrap())

        sectionTitle("Navigation tabs")
        content.addView(smallText("Use arrows to reorder tabs. Hide tabs you don't use."), matchWrap())
        val tabsCard = settingsCard()
        listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { tab ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = tab
                textSize = 14f
                setTextColor(if (prefs.getBoolean("tab_visible_$tab", true)) white else muted)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(Switch(this).apply {
                isChecked = prefs.getBoolean("tab_visible_$tab", true)
                buttonTintList = ColorStateList.valueOf(orange)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("tab_visible_$tab", checked).apply()
                    buildScreen()
                    showTab(if (selectedTab in visibleTabs()) selectedTab else "Keypad")
                }
            })
            row.addView(actionText("↑", panel, white, 17f) { moveTab(tab, -1) },
                LinearLayout.LayoutParams(dp(40), dp(38)))
            row.addView(actionText("↓", panel, white, 17f) { moveTab(tab, 1) },
                LinearLayout.LayoutParams(dp(40), dp(38)))
            tabsCard.addView(row, matchWrap())
        }
        content.addView(tabsCard, matchWrap())

        sectionTitle("Saved home layouts")
        val layouts = settingsCard()
        listOf("Classic", "Minimal", "Power User").forEach { preset ->
            layouts.addView(actionText("Apply $preset", panel, white, 13f) {
                applyLayoutPreset(preset)
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
        }
        (1..3).forEach { slot ->
            val saved = prefs.contains("custom_layout_$slot")
            layouts.addView(smallText("CUSTOM SLOT $slot" + if (saved) " · saved" else " · empty"), matchWrap())
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            row.addView(actionText("Save current", 0xFF292929.toInt(), white, 12f) {
                saveCustomLayout(slot)
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            row.addView(actionText("Load", 0xFF292929.toInt(), white, 12f) {
                loadCustomLayout(slot)
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            layouts.addView(row, matchWrap())
        }
        content.addView(layouts, matchWrap())

        sectionTitle("Duck branding")
        val duck = settingsCard()
        addChoiceRow(duck, listOf("Small" to 20, "Medium" to 25, "Large" to 31), "home_duck_size", 25)
        val greeting = EditText(this).apply {
            setText(prefs.getString("home_greeting", "DUCKDIALER"))
            setTextColor(white)
            setHintTextColor(muted)
            hint = "Your app label"
            isSingleLine = true
            textSize = 14f
            background = rounded(0xFF242424.toInt(), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        duck.addView(greeting, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        greeting.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.edit().putString("home_greeting", s?.toString()?.take(22) ?: "DUCKDIALER").apply()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(duck, matchWrap())
        content.addView(actionText("←  Back to Settings", panel, white, 14f) {
            showTab("Settings")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(18) })
    }

    private val homeSections = listOf("entry", "keypad", "call", "caller_status", "caller_shortcut")

    private fun homeSectionLabel(key: String): String = when (key) {
        "entry" -> "Number entry"
        "keypad" -> "Keypad"
        "call" -> "Call controls"
        "caller_status" -> "Caller ID status"
        else -> "Caller ID shortcut"
    }

    private fun homeSectionOrder(): List<String> {
        val saved = (prefs.getString("home_section_order", homeSections.joinToString("|")) ?: homeSections.joinToString("|"))
            .split("|").filter { it in homeSections }.distinct()
        return saved + homeSections.filterNot { it in saved }
    }

    private fun applyHomeSectionOrder() {
        if (content.childCount < 2) return
        val children = (0 until content.childCount).map { content.getChildAt(it) }
        val order = homeSectionOrder()
        val ordered = children.withIndex().sortedWith(compareBy<IndexedValue<View>> {
            val key = it.value.tag as? String
            if (key == null) -1 else order.indexOf(key).let { index -> if (index < 0) 99 else index }
        }.thenBy { it.index }).map { it.value }
        content.removeAllViews()
        ordered.forEach { view -> content.addView(view) }
    }

    private fun moveHomeSection(from: String, to: String) {
        val order = homeSectionOrder().toMutableList()
        val old = order.indexOf(from)
        val target = order.indexOf(to)
        if (old < 0 || target < 0 || old == target) return
        val moved = order.removeAt(old)
        order.add(target, moved)
        prefs.edit().putString("home_section_order", order.joinToString("|")).apply()
        showTab("Home Customization")
    }

    private fun addHomeSectionOrderEditor() {
        val card = settingsCard()
        homeSectionOrder().forEachIndexed { index, section ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                tag = section
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = rounded(0xFF222222.toInt(), 10)
                setOnLongClickListener {
                    startDragAndDrop(ClipData.newPlainText("home-section", section), View.DragShadowBuilder(this), section, 0)
                }
                setOnDragListener { target, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> true
                        DragEvent.ACTION_DRAG_ENTERED -> { target.alpha = 0.65f; true }
                        DragEvent.ACTION_DRAG_EXITED -> { target.alpha = 1f; true }
                        DragEvent.ACTION_DROP -> {
                            target.alpha = 1f
                            val from = event.localState as? String
                            val to = target.tag as? String
                            if (from != null && to != null) moveHomeSection(from, to)
                            true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> { target.alpha = 1f; true }
                        else -> true
                    }
                }
            }
            row.addView(TextView(this).apply {
                text = "☷  ${homeSectionLabel(section)}"
                textSize = 13f
                setTextColor(white)
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(actionText("↑", panel, white, 15f) {
                if (index > 0) moveHomeSection(section, homeSectionOrder()[index - 1])
            }, LinearLayout.LayoutParams(dp(38), dp(36)))
            row.addView(actionText("↓", panel, white, 15f) {
                val order = homeSectionOrder()
                if (index < order.lastIndex) moveHomeSection(section, order[index + 1])
            }, LinearLayout.LayoutParams(dp(38), dp(36)))
            card.addView(row, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(4) })
        }
        content.addView(card, matchWrap())
    }

    private fun addCounterStyleRow(parent: LinearLayout) {
        val styles = listOf("Glow", "Gradient", "Minimal", "Bubble", "Badge")
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        styles.forEach { style ->
            val selected = prefs.getString("counter_style", "Glow") == style
            row.addView(actionText(if (selected) "✓ $style" else style,
                if (selected) orange else 0xFF292929.toInt(),
                if (selected) 0xFF17100A.toInt() else white, 10f) {
                prefs.edit().putString("counter_style", style).apply()
                if (::countView.isInitialized) countView.background = counterBackground()
                showTab("Home Customization")
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), dp(7), dp(2), dp(7))
            })
        }
        parent.addView(row, matchWrap())
    }

    private fun counterBackground(): GradientDrawable {
        return when (prefs.getString("counter_style", "Glow")) {
            "Gradient" -> GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3A1600.toInt(), 0xFF17100A.toInt(), 0xFF101010.toInt())).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), orange)
            }
            "Minimal" -> GradientDrawable().apply {
                setColor(0xFF151515.toInt())
                cornerRadius = dp(14).toFloat()
            }
            "Bubble" -> GradientDrawable().apply {
                setColor(0xFF24152D.toInt())
                cornerRadius = dp(34).toFloat()
                setStroke(dp(1), 0xFF8E45B5.toInt())
            }
            "Badge" -> GradientDrawable().apply {
                setColor(0xFF2A1708.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), orange)
            }
            else -> GradientDrawable().apply {
                setColor(0xFF211307.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), orange)
            }
        }
    }

    private fun addHomeSwitch(parent: LinearLayout, key: String, label: String, default: Boolean) {
        parent.addView(Switch(this).apply {
            text = label
            textSize = 13f
            setTextColor(white)
            isChecked = prefs.getBoolean(key, default)
            buttonTintList = ColorStateList.valueOf(orange)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                if (key == "home_show_count" && ::countView.isInitialized) {
                    countView.visibility = if (checked) View.VISIBLE else View.GONE
                }
                if (key.startsWith("counter_") && ::countView.isInitialized) refreshTodayCount()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun addChoiceRow(parent: LinearLayout, choices: List<Pair<String, Int>>, key: String, default: Int) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        choices.forEach { (label, value) ->
            val selected = prefs.getInt(key, default) == value
            row.addView(actionText(if (selected) "✓ $label" else label,
                if (selected) orange else 0xFF292929.toInt(),
                if (selected) 0xFF17100A.toInt() else white, 11f) {
                prefs.edit().putInt(key, value).apply()
                showTab("Home Customization")
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(3), dp(7), dp(3), dp(7))
            })
        }
        parent.addView(row, matchWrap())
    }



    private fun layoutKeys(): List<String> = listOf(
        "home_show_count", "home_brand_label", "home_show_duck", "home_keypad_letters",
        "home_show_caller_hint", "home_show_caller_shortcut", "home_show_keypad",
        "home_key_height", "home_duck_size", "home_greeting", "tab_order",
        "counter_style", "counter_show_label", "counter_show_icon", "counter_show_comparison",
        "counter_animation", "counter_compact",
        "tab_visible_Favorites", "tab_visible_Recents", "tab_visible_Contacts", "tab_visible_Keypad"
    )

    private fun saveCustomLayout(slot: Int) {
        val values = layoutKeys().joinToString("\n") { key ->
            val value: String = when {
                key == "home_greeting" -> prefs.getString(key, "DUCKDIALER") ?: "DUCKDIALER"
                key == "tab_order" -> prefs.getString(key, "Favorites|Recents|Contacts|Keypad") ?: "Favorites|Recents|Contacts|Keypad"
                key == "counter_style" -> prefs.getString(key, "Glow") ?: "Glow"
                key.startsWith("home_key_height") -> prefs.getInt(key, 65).toString()
                key == "home_duck_size" -> prefs.getInt(key, 25).toString()
                else -> prefs.getBoolean(key, true).toString()
            }
            key + "=" + value.replace("\n", " ")
        }
        prefs.edit().putString("custom_layout_$slot", values).apply()
        Toast.makeText(this, "Layout saved to slot $slot", Toast.LENGTH_SHORT).show()
        showTab("Home Customization")
    }

    private fun loadCustomLayout(slot: Int) {
        val raw = prefs.getString("custom_layout_$slot", null)
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "This layout slot is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val editor = prefs.edit()
        raw.lines().forEach { line ->
            val split = line.indexOf("=")
            if (split > 0) {
                val key = line.substring(0, split)
                val value = line.substring(split + 1)
                when {
                    key == "home_greeting" || key == "tab_order" || key == "counter_style" -> editor.putString(key, value)
                    key == "home_key_height" || key == "home_duck_size" -> editor.putInt(key, value.toIntOrNull() ?: 65)
                    else -> editor.putBoolean(key, value.toBoolean())
                }
            }
        }
        editor.apply()
        buildScreen()
        showTab("Home Customization")
        Toast.makeText(this, "Layout loaded", Toast.LENGTH_SHORT).show()
    }

    private fun moveTab(tab: String, delta: Int) {
        val tabs = listOf("Favorites", "Recents", "Contacts", "Keypad")
        val order = (prefs.getString("tab_order", tabs.joinToString("|")) ?: tabs.joinToString("|"))
            .split("|").filter { it in tabs }.distinct().toMutableList()
        tabs.filterNot { it in order }.forEach { order.add(it) }
        val from = order.indexOf(tab)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from != to) {
            val item = order.removeAt(from)
            order.add(to, item)
            prefs.edit().putString("tab_order", order.joinToString("|")).apply()
            showTab("Home Customization")
        }
    }

    private fun applyLayoutPreset(name: String) {
        val editor = prefs.edit()
        when (name) {
            "Classic" -> {
                listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { editor.putBoolean("tab_visible_$it", true) }
                editor.putBoolean("home_show_count", true).putBoolean("home_show_duck", true)
                    .putBoolean("home_show_keypad", true).putBoolean("home_show_caller_hint", true)
                    .putBoolean("home_show_caller_shortcut", true).putInt("home_key_height", 65)
            }
            "Minimal" -> {
                listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { editor.putBoolean("tab_visible_$it", it == "Keypad" || it == "Recents") }
                editor.putBoolean("home_show_count", false).putBoolean("home_show_duck", false)
                    .putBoolean("home_show_keypad", true).putBoolean("home_show_caller_hint", false)
                    .putBoolean("home_show_caller_shortcut", false).putInt("home_key_height", 58)
            }
            "Power User" -> {
                listOf("Favorites", "Recents", "Contacts", "Keypad").forEach { editor.putBoolean("tab_visible_$it", true) }
                editor.putBoolean("home_show_count", true).putBoolean("home_show_duck", true)
                    .putBoolean("home_show_keypad", true).putBoolean("home_show_caller_hint", true)
                    .putBoolean("home_show_caller_shortcut", true).putInt("home_key_height", 74)
            }
        }
        editor.putString("tab_order", when (name) {
            "Minimal" -> "Keypad|Recents|Favorites|Contacts"
            "Power User" -> "Keypad|Favorites|Recents|Contacts"
            else -> "Favorites|Recents|Contacts|Keypad"
        }).apply()
        buildScreen()
        showTab("Home Customization")
    }

    private fun buildSettings() {
        sectionTitle("Settings")
        content.addView(smallText("Choose a category"), matchWrap())
        val categories = listOf("General", "Caller ID", "Appearance", "In-Call", "Home Screen", "About")
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, dp(14))
        }
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        categories.forEach { category ->
            tabs.addView(actionText(
                if (selectedSettingsSection == category) "✓  $category" else category,
                if (selectedSettingsSection == category) orange else panel,
                if (selectedSettingsSection == category) 0xFF17100A.toInt() else white, 12f
            ) {
                selectedSettingsSection = category
                showTab("Settings")
            }, LinearLayout.LayoutParams(-2, dp(42)).apply { setMargins(dp(3), 0, dp(5), 0) })
        }
        scroller.addView(tabs)
        content.addView(scroller, LinearLayout.LayoutParams(-1, dp(58)))

        when (selectedSettingsSection) {
            "General" -> {
                sectionTitle("General")
                val card = settingsCard()
                card.addView(TextView(this).apply {
                    text = "DuckDialer"
                    textSize = 17f
                    setTextColor(white)
                }, matchWrap())
                card.addView(smallText("Set DuckDialer as your default Phone app to handle calls. Call history and contacts depend on the permissions you allow."), matchWrap())
                content.addView(card, matchWrap())
            }
            "Caller ID" -> {
                sectionTitle("Caller ID")
                val card = settingsCard()
                card.addView(Switch(this).apply {
                    text = "Hide caller ID (*67)"
                    textSize = 15f
                    setTextColor(white)
                    isChecked = prefs.getBoolean("hide_caller_id", false)
                    buttonTintList = ColorStateList.valueOf(orange)
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean("hide_caller_id", checked).apply()
                    }
                }, matchWrap())
                card.addView(smallText("Prepends *67 to eligible outgoing calls. Carrier support varies. Emergency and service codes are excluded."), matchWrap())
                content.addView(card, matchWrap())
            }
            "Appearance" -> {
                sectionTitle("Accent color")
                val colors = listOf("Orange" to 0xFFFF8A00.toInt(), "Blue" to 0xFF3998FF.toInt(),
                    "Purple" to 0xFFB078FF.toInt(), "Green" to 0xFF39C98A.toInt())
                colors.chunked(2).forEach { pair ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
                    pair.forEach { (name, color) ->
                        row.addView(actionText(if (orange == color) "✓  $name" else name, color,
                            0xFF101010.toInt(), 13f) {
                            prefs.edit().putInt("accent_color", color).apply()
                            selectedSettingsSection = "Appearance"
                            showTab("Settings")
                        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                    }
                    content.addView(row, matchWrap())
                }
            }
            "In-Call" -> {
                sectionTitle("In-Call Screen")
                content.addView(smallText("Personalize the screen shown during incoming and active calls. Changes apply to the next call."), matchWrap())
                val display = settingsCard()
                fun callSwitch(key: String, label: String, default: Boolean) {
                    display.addView(Switch(this).apply {
                        text = label
                        textSize = 14f
                        setTextColor(white)
                        isChecked = prefs.getBoolean(key, default)
                        buttonTintList = ColorStateList.valueOf(orange)
                        setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
                    }, LinearLayout.LayoutParams(-1, dp(46)))
                }
                callSwitch("incall_show_brand", "Show DuckDialer branding", true)
                callSwitch("incall_show_avatar", "Show caller photo / duck avatar", true)
                callSwitch("incall_show_timer", "Show call duration timer", true)
                callSwitch("incall_show_control_labels", "Show labels under control icons", true)
                content.addView(display, matchWrap())
                sectionTitle("Avatar size")
                fun callChoiceRow(parent: LinearLayout, choices: List<Pair<String, Int>>, key: String, default: Int) {
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
                    choices.forEach { (label, value) ->
                        val selected = prefs.getInt(key, default) == value
                        row.addView(actionText(if (selected) "✓ $label" else label,
                            if (selected) orange else 0xFF292929.toInt(),
                            if (selected) 0xFF17100A.toInt() else white, 11f) {
                            prefs.edit().putInt(key, value).apply()
                            selectedSettingsSection = "In-Call"
                            showTab("Settings")
                        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                            setMargins(dp(3), dp(7), dp(3), dp(7))
                        })
                    }
                    parent.addView(row, matchWrap())
                }
                val avatar = settingsCard()
                callChoiceRow(avatar, listOf("Small" to 80, "Medium" to 108, "Large" to 144), "incall_avatar_size", 108)
                content.addView(avatar, matchWrap())
                sectionTitle("Call control size")
                val controls = settingsCard()
                callChoiceRow(controls, listOf("Compact" to 68, "Comfortable" to 78, "Large" to 100), "incall_control_size", 78)
                content.addView(controls, matchWrap())
                sectionTitle("In-call accent")
                val callColors = listOf("Orange" to 0xFFFF8A00.toInt(), "Blue" to 0xFF3998FF.toInt(),
                    "Purple" to 0xFFB078FF.toInt(), "Green" to 0xFF39C98A.toInt())
                callColors.chunked(2).forEach { pair ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
                    pair.forEach { (name, color) ->
                        row.addView(actionText(
                            if (prefs.getInt("incall_accent_color", prefs.getInt("accent_color", 0xFFFF8A00.toInt())) == color) "✓  $name" else name,
                            color, 0xFF101010.toInt(), 13f
                        ) {
                            prefs.edit().putInt("incall_accent_color", color).apply()
                            selectedSettingsSection = "In-Call"
                            showTab("Settings")
                        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        })
                    }
                    content.addView(row, matchWrap())
                }
                content.addView(actionText("Use app accent color", panel, white, 13f) {
                    prefs.edit().remove("incall_accent_color").apply()
                    selectedSettingsSection = "In-Call"
                    showTab("Settings")
                }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
            }
            "Home Screen" -> {
                sectionTitle("Home Screen")
                val card = settingsCard()
                card.addView(smallText("Customize the keypad, home widgets, navigation tabs, section order, branding, and saved layouts."), matchWrap())
                content.addView(card, matchWrap())
                content.addView(actionText("✦  Open Home Screen Customization", orange, 0xFF17100A.toInt(), 14f) {
                    showTab("Home Customization")
                }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
            }
            "About" -> {
                sectionTitle("About DuckDialer")
                val card = settingsCard()
                card.addView(TextView(this).apply {
                    text = "DuckDialer"
                    textSize = 17f
                    setTextColor(white)
                }, matchWrap())
                card.addView(smallText("A dark, duck-approved dialer. Built as an independent Android phone app."), matchWrap())
                content.addView(card, matchWrap())
            }
        }
        content.addView(actionText("←  Back to Phone", panel, white, 14f) {
            showTab("Keypad")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(20) })
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
        favoriteAction: Boolean = false, parent: LinearLayout = content, action: () -> Unit) {
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
        parent.addView(row, matchWrap().apply { setMargins(0, 0, 0, dp(8)) })
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
        placeCall(numberView.text.toString().trim())
    }

    // Apply caller-ID suppression in one place so keypad, contacts, recents, and
    // favorites all follow the same preference. Never prefix service/emergency codes.
    private fun placeCall(rawNumber: String) {
        if (rawNumber.isBlank()) return
        var number = rawNumber.trim()
        val serviceCode = number.startsWith("*") || number.startsWith("#")
        val normalized = number.filter { it.isDigit() || it == '+' }
        val emergency = normalized == "911" || normalized == "112"
        val alreadySuppressed = number.startsWith("*67")
        if (prefs.getBoolean("hide_caller_id", false) &&
            !serviceCode && !emergency && !alreadySuppressed) {
            number = "*67$number"
        }
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
        val permissions = mutableListOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val needed = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 201)
    }

    private fun refreshTodayCount() {
        if (!::countView.isInitialized) return
        countView.background = counterBackground()
        countView.minHeight = dp(if (prefs.getBoolean("counter_compact", false)) 64 else 104)
        countView.setPadding(dp(16), dp(if (prefs.getBoolean("counter_compact", false)) 10 else 16),
            dp(16), dp(if (prefs.getBoolean("counter_compact", false)) 10 else 16))
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            countView.text = "Allow call-log access to show today's count"
            return
        }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val tomorrowStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val yesterdayStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun outgoingCount(from: Long, until: Long): Int {
            val selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE +
                " < ? AND " + CallLog.Calls.TYPE + " = ?"
            val args = arrayOf(from.toString(), until.toString(), CallLog.Calls.OUTGOING_TYPE.toString())
            return contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                selection, args, null)?.use { it.count } ?: 0
        }
        try {
            val today = outgoingCount(todayStart.timeInMillis, tomorrowStart.timeInMillis)
            val yesterday = if (prefs.getBoolean("counter_show_comparison", true))
                outgoingCount(yesterdayStart.timeInMillis, todayStart.timeInMillis) else 0
            val display = SpannableStringBuilder()
            if (prefs.getBoolean("counter_show_label", true)) {
                val labelStart = display.length
                if (prefs.getBoolean("counter_show_icon", true)) display.append("🔥  ")
                display.append("CALLS MADE TODAY")
                display.setSpan(RelativeSizeSpan(0.82f), labelStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                display.setSpan(ForegroundColorSpan(orange), labelStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                display.append("\n")
            }
            val numberStart = display.length
            display.append(today.toString())
            display.setSpan(RelativeSizeSpan(2.7f), numberStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            display.setSpan(StyleSpan(Typeface.BOLD), numberStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (prefs.getBoolean("counter_show_comparison", true)) {
                val difference = today - yesterday
                val comparison = when {
                    difference > 0 -> "▲ $difference vs yesterday"
                    difference < 0 -> "▼ ${-difference} vs yesterday"
                    else -> "Same as yesterday"
                }
                display.append("\n")
                val comparisonStart = display.length
                display.append(comparison)
                display.setSpan(RelativeSizeSpan(0.88f), comparisonStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                display.setSpan(ForegroundColorSpan(muted), comparisonStart, display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            countView.text = display
            countView.textSize = 16f
            countView.setTextColor(white)
            if (prefs.getBoolean("counter_animation", true)) {
                countView.animate().cancel()
                countView.scaleX = 0.94f
                countView.scaleY = 0.94f
                countView.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            }
        } catch (_: SecurityException) {
            countView.text = "Call history unavailable"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
