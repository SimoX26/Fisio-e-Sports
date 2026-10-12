package it.simosw.fisioesport

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private data class AgendaRow(val time: String, val title: String, val kind: String, val state: String)
    private data class CalendarEntry(val start: String, val end: String, val allDay: Boolean,
        val title: String, val kind: String, val state: String)
    private data class PatientRow(val id: Long, val name: String, val phone: String, val created: String)
    private enum class Section(val title: Int) {
        HOME(R.string.home), CALENDAR(R.string.calendar), PATIENTS(R.string.address_book),
        TREATMENTS(R.string.treatments), STATISTICS(R.string.statistics), SETTINGS(R.string.settings)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var tokenStore: SecureTokenStore
    private var token: String? = null
    private var loginAttempt = 0
    private var section = Section.HOME
    private var selectedPatientId: Long? = null
    private var patientsRequest = 0
    private var calendarRequest = 0
    private val selectedCalendarDay = Calendar.getInstance()
    private val displayedMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var calendarEntries = emptyList<CalendarEntry>()
    private var calendarLoading = false
    private var calendarLoadError = false
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var message: TextView
    private lateinit var loginButton: Button
    private lateinit var loggedInPanel: View
    private lateinit var loginPanel: View
    private lateinit var welcome: TextView
    private lateinit var agendaDate: TextView
    private lateinit var appointmentsCount: TextView
    private lateinit var patientsCount: TextView
    private lateinit var remindersCount: TextView
    private lateinit var agendaList: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var contentScroll: ScrollView
    private lateinit var homePanel: View
    private lateinit var calendarPanel: View
    private lateinit var calendarMonthTitle: TextView
    private lateinit var calendarSelectedDate: TextView
    private lateinit var calendarWeekdays: LinearLayout
    private lateinit var calendarGrid: LinearLayout
    private lateinit var calendarDayEvents: LinearLayout
    private lateinit var patientsPanel: View
    private lateinit var patientDetailPanel: View
    private lateinit var patientDetailTitle: TextView
    private lateinit var patientDetailStatus: TextView
    private lateinit var patientDetailFields: LinearLayout
    private lateinit var placeholderPanel: View
    private lateinit var placeholderTitle: TextView
    private lateinit var currentSectionButton: MaterialButton
    private lateinit var patientsSearch: SearchView
    private lateinit var patientResults: TextView
    private lateinit var patientList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        tokenStore = SecureTokenStore(this, BuildConfig.FISIO_API_BASE_URL.trimEnd('/'))
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        message = findViewById(R.id.message)
        loginButton = findViewById(R.id.loginButton)
        loggedInPanel = findViewById(R.id.loggedInPanel)
        loginPanel = findViewById(R.id.loginPanel)
        welcome = findViewById(R.id.welcome)
        agendaDate = findViewById(R.id.agendaDate)
        appointmentsCount = findViewById(R.id.appointmentsCount)
        patientsCount = findViewById(R.id.patientsCount)
        remindersCount = findViewById(R.id.remindersCount)
        agendaList = findViewById(R.id.agendaList)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(swipeRefresh)
        contentScroll = findViewById(R.id.contentScroll)
        homePanel = findViewById(R.id.homePanel)
        calendarPanel = findViewById(R.id.calendarPanel)
        calendarMonthTitle = findViewById(R.id.calendarMonthTitle)
        calendarSelectedDate = findViewById(R.id.calendarSelectedDate)
        calendarWeekdays = findViewById(R.id.calendarWeekdays)
        calendarGrid = findViewById(R.id.calendarGrid)
        calendarDayEvents = findViewById(R.id.calendarDayEvents)
        patientsPanel = findViewById(R.id.patientsPanel)
        patientDetailPanel = findViewById(R.id.patientDetailPanel)
        patientDetailTitle = findViewById(R.id.patientDetailTitle)
        patientDetailStatus = findViewById(R.id.patientDetailStatus)
        patientDetailFields = findViewById(R.id.patientDetailFields)
        placeholderPanel = findViewById(R.id.placeholderPanel)
        placeholderTitle = findViewById(R.id.placeholderTitle)
        currentSectionButton = findViewById(R.id.currentSectionButton)
        patientsSearch = findViewById(R.id.patientsSearch)
        patientResults = findViewById(R.id.patientResults)
        patientList = findViewById(R.id.patientList)
        swipeRefresh.isEnabled = false
        swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> contentScroll.canScrollVertically(-1) }
        swipeRefresh.setOnRefreshListener {
            when {
                section == Section.HOME -> loadTodayAgenda()
                section == Section.CALENDAR -> loadCalendarMonth()
                selectedPatientId != null -> loadPatientDetail(selectedPatientId!!)
                section == Section.PATIENTS -> loadPatients()
                else -> swipeRefresh.isRefreshing = false
            }
        }
        loginButton.setOnClickListener { login() }
        currentSectionButton.setOnClickListener { showNavigationMenu() }
        findViewById<Button>(R.id.calendarToday).setOnClickListener {
            selectedCalendarDay.timeInMillis = System.currentTimeMillis()
            displayedMonth.timeInMillis = selectedCalendarDay.timeInMillis
            displayedMonth.set(Calendar.DAY_OF_MONTH, 1)
            loadCalendarMonth()
        }
        findViewById<View>(R.id.calendarPrevious).setOnClickListener { changeCalendarMonth(-1) }
        findViewById<View>(R.id.calendarNext).setOnClickListener { changeCalendarMonth(1) }
        findViewById<Button>(R.id.patientDetailBack).setOnClickListener { showSection(Section.PATIENTS) }
        patientsSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                patientsSearch.clearFocus()
                loadPatients()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty() && section == Section.PATIENTS) loadPatients()
                return false
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (token != null && selectedPatientId != null) showSection(Section.PATIENTS)
                else if (token != null && section != Section.HOME) showSection(Section.HOME)
                else finish()
            }
        })
        restoreSession()
    }

    private fun restoreSession() {
        val expectedAttempt = loginAttempt
        worker.execute {
            val savedToken = tokenStore.load() ?: return@execute
            try {
                val (status, body) = request("GET", "/api/me", "Bearer $savedToken")
                if (status == 401) {
                    tokenStore.clear()
                    return@execute
                }
                if (status != 200) throw IllegalStateException("HTTP $status")
                val displayName = JSONObject(body).getString("username")
                runOnUiThread {
                    if (loginAttempt == expectedAttempt && token == null) {
                        enterSession(savedToken, displayName)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (loginAttempt == expectedAttempt && token == null) {
                        message.setText(R.string.backend_unavailable)
                    }
                }
            }
        }
    }

    private fun enterSession(activeToken: String, displayName: String) {
        token = activeToken
        password.text.clear()
        welcome.text = getString(R.string.welcome, displayName)
        loginPanel.visibility = View.GONE
        loggedInPanel.visibility = View.VISIBLE
        showSection(Section.HOME)
    }

    private fun login() {
        val user = username.text.toString().trim()
        val secret = password.text.toString()
        if (user.isEmpty() || secret.isEmpty()) {
            message.setText(R.string.credentials_required)
            return
        }
        loginAttempt++
        loginButton.isEnabled = false
        message.setTextColor(getColor(R.color.text_muted))
        message.setText(R.string.connecting)
        worker.execute {
            var issuedToken: String? = null
            try {
                val pair = "$user:$secret".toByteArray(StandardCharsets.UTF_8)
                val basic = "Basic " + Base64.encodeToString(pair, Base64.NO_WRAP)
                val (status, body) = request("POST", "/api/auth/remember", basic)
                if (status == 401) {
                    showLoginError(R.string.invalid_credentials)
                    return@execute
                }
                if (status != 201) throw IllegalStateException("HTTP $status")
                val newToken = JSONObject(body).getString("token")
                issuedToken = newToken
                val (identityStatus, identityBody) = request("GET", "/api/me", "Bearer $newToken")
                if (identityStatus != 200) throw IllegalStateException("HTTP $identityStatus")
                val displayName = JSONObject(identityBody).getString("username")
                val saved = tokenStore.save(newToken)
                runOnUiThread {
                    enterSession(newToken, displayName)
                    if (!saved) Toast.makeText(this, R.string.session_save_error, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                issuedToken?.let {
                    try {
                        request("DELETE", "/api/auth/remember", "Bearer $it")
                    } catch (_: Exception) {
                        // Revoca best effort se il backend diventa irraggiungibile.
                    }
                }
                showLoginError(R.string.backend_unavailable)
            }
        }
    }

    private fun showLoginError(resource: Int) {
        runOnUiThread {
            loginButton.isEnabled = true
            message.setTextColor(getColor(R.color.brand_danger))
            message.setText(resource)
        }
    }

    private fun showSection(next: Section) {
        if (token == null) return
        section = next
        selectedPatientId = null
        swipeRefresh.isRefreshing = false
        swipeRefresh.isEnabled = next == Section.HOME || next == Section.CALENDAR || next == Section.PATIENTS
        homePanel.visibility = if (next == Section.HOME) View.VISIBLE else View.GONE
        calendarPanel.visibility = if (next == Section.CALENDAR) View.VISIBLE else View.GONE
        patientsPanel.visibility = if (next == Section.PATIENTS) View.VISIBLE else View.GONE
        patientDetailPanel.visibility = View.GONE
        placeholderPanel.visibility = if (next == Section.HOME || next == Section.CALENDAR || next == Section.PATIENTS) View.GONE else View.VISIBLE
        if (placeholderPanel.visibility == View.VISIBLE) placeholderTitle.setText(next.title)
        currentSectionButton.text = getString(R.string.navigation_current, getString(next.title))
        currentSectionButton.contentDescription = getString(R.string.navigation_open_description, getString(next.title))
        contentScroll.scrollTo(0, 0)
        when (next) {
            Section.HOME -> loadTodayAgenda()
            Section.CALENDAR -> loadCalendarMonth()
            Section.PATIENTS -> loadPatients()
            else -> Unit
        }
    }

    private fun showNavigationMenu() {
        if (token == null) return
        val menu = PopupMenu(this, currentSectionButton)
        menu.gravity = Gravity.END
        Section.entries.forEachIndexed { index, destination ->
            menu.menu.add(0, index + 1, index, destination.title).apply {
                isCheckable = true
                isChecked = destination == section
            }
        }
        menu.menu.setGroupCheckable(0, true, true)
        val logoutId = Section.entries.size + 1
        menu.menu.add(1, logoutId, logoutId, R.string.logout)
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == logoutId) logout()
            else Section.entries.getOrNull(item.itemId - 1)?.let(::showSection)
            true
        }
        menu.show()
    }

    private fun loadPatients() {
        val activeToken = token ?: return
        val requestId = ++patientsRequest
        val query = patientsSearch.query.toString().trim()
        if (query.length > 200) {
            swipeRefresh.isRefreshing = false
            patientList.removeAllViews()
            patientResults.setTextColor(getColor(R.color.brand_danger))
            patientResults.setText(R.string.search_too_long)
            return
        }
        swipeRefresh.isRefreshing = true
        patientResults.setTextColor(getColor(R.color.text_muted))
        patientResults.setText(R.string.patients_loading)
        patientList.removeAllViews()
        worker.execute {
            try {
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                val (status, body) = request(
                    "GET", "/api/patients?q=$encodedQuery&sort=created-desc", "Bearer $activeToken"
                )
                if (status != 200) throw IllegalStateException("HTTP $status")
                val result = JSONArray(body)
                val patients = (0 until result.length()).map { index ->
                    val item = result.getJSONObject(index)
                    PatientRow(
                        item.getLong("id"),
                        item.getString("fullName"),
                        if (item.isNull("phone")) "" else item.optString("phone"),
                        if (item.isNull("createdDateLabel")) "" else item.optString("createdDateLabel")
                    )
                }
                runOnUiThread {
                    if (token != activeToken || section != Section.PATIENTS || requestId != patientsRequest) {
                        return@runOnUiThread
                    }
                    swipeRefresh.isRefreshing = false
                    patientResults.text = if (patients.size == 1) getString(R.string.patient_results_one)
                        else getString(R.string.patient_results, patients.size)
                    patientList.removeAllViews()
                    if (patients.isEmpty()) addPatientMessage(R.string.patients_empty)
                    patients.forEach(::addPatientRow)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != activeToken || section != Section.PATIENTS || requestId != patientsRequest) {
                        return@runOnUiThread
                    }
                    swipeRefresh.isRefreshing = false
                    patientResults.setTextColor(getColor(R.color.brand_danger))
                    patientResults.setText(R.string.patients_error)
                    patientList.removeAllViews()
                }
            }
        }
    }

    private fun addPatientMessage(resource: Int) {
        patientList.addView(TextView(this).apply {
            setText(resource)
            setTextColor(getColor(R.color.text_muted))
            setPadding(dp(12), dp(16), dp(12), dp(16))
        })
    }

    private fun addPatientRow(patient: PatientRow) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.surface_card)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { openPatientDetail(patient) }
        }
        card.addView(TextView(this).apply {
            text = patient.name
            textSize = 16f
            setTextColor(getColor(R.color.text_main))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val details = listOf(patient.created, patient.phone).filter { it.isNotBlank() }.joinToString(" · ")
        if (details.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = details
                textSize = 13f
                setTextColor(getColor(R.color.text_muted))
                setPadding(0, dp(4), 0, 0)
            })
        }
        patientList.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
    }

    private fun openPatientDetail(patient: PatientRow) {
        selectedPatientId = patient.id
        patientsRequest++
        patientsPanel.visibility = View.GONE
        patientDetailPanel.visibility = View.VISIBLE
        patientDetailTitle.text = patient.name
        contentScroll.scrollTo(0, 0)
        loadPatientDetail(patient.id)
    }

    private fun loadPatientDetail(patientId: Long) {
        val activeToken = token ?: return
        swipeRefresh.isRefreshing = true
        patientDetailStatus.visibility = View.VISIBLE
        patientDetailStatus.setTextColor(getColor(R.color.text_muted))
        patientDetailStatus.setText(R.string.patient_detail_loading)
        patientDetailFields.visibility = View.GONE
        patientDetailFields.removeAllViews()
        worker.execute {
            var error = R.string.patient_detail_error
            try {
                val (status, body) = request("GET", "/api/patients/$patientId", "Bearer $activeToken")
                if (status == 404) error = R.string.patient_detail_not_found
                if (status != 200) throw IllegalStateException("HTTP $status")
                val patient = JSONObject(body)
                runOnUiThread {
                    if (token != activeToken || section != Section.PATIENTS || selectedPatientId != patientId) {
                        return@runOnUiThread
                    }
                    swipeRefresh.isRefreshing = false
                    patientDetailTitle.text = patient.getString("fullName")
                    patientDetailStatus.visibility = View.GONE
                    val state = when (patient.optString("state")) {
                        "ACTIVE" -> getString(R.string.patient_state_active)
                        "INACTIVE" -> getString(R.string.patient_state_inactive)
                        "ARCHIVED" -> getString(R.string.patient_state_archived)
                        else -> patient.optString("state")
                    }
                    addPatientDetailField(R.string.patient_detail_state, state)
                    addPatientDetailField(R.string.patient_detail_phone, patient.optString("phone"))
                    addPatientDetailField(R.string.patient_detail_email, patient.optString("email"))
                    addPatientDetailField(R.string.patient_detail_created, patient.optString("createdDateLabel"))
                    addPatientDetailField(R.string.patient_detail_appointments,
                        patient.optInt("linkedAppointmentsCount").toString())
                    patientDetailFields.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != activeToken || section != Section.PATIENTS || selectedPatientId != patientId) {
                        return@runOnUiThread
                    }
                    swipeRefresh.isRefreshing = false
                    patientDetailStatus.setTextColor(getColor(R.color.brand_danger))
                    patientDetailStatus.setText(error)
                }
            }
        }
    }

    private fun addPatientDetailField(label: Int, value: String) {
        if (value.isBlank() || value == "null") return
        val field = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        field.addView(TextView(this).apply {
            setText(label)
            textSize = 13f
            setTextColor(getColor(R.color.text_muted))
        })
        field.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(getColor(R.color.text_main))
            setPadding(0, dp(4), 0, 0)
        })
        patientDetailFields.addView(field, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })
    }

    private fun changeCalendarMonth(amount: Int) {
        displayedMonth.add(Calendar.MONTH, amount)
        selectedCalendarDay.timeInMillis = displayedMonth.timeInMillis
        loadCalendarMonth()
    }

    private fun calendarKey(day: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day.time)

    private fun entriesForDay(day: Calendar): List<CalendarEntry> {
        val start = calendarKey(day) + "T00:00"
        val nextDay = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val end = calendarKey(nextDay) + "T00:00"
        return calendarEntries.filter { it.start < end && it.end > start }
    }

    private fun loadCalendarMonth() {
        val activeToken = token ?: return
        val requestId = ++calendarRequest
        val start = calendarKey(displayedMonth) + "T00:00:00"
        val nextMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val end = calendarKey(nextMonth) + "T00:00:00"
        calendarEntries = emptyList()
        calendarLoading = true
        calendarLoadError = false
        renderCalendar()
        swipeRefresh.isRefreshing = true
        worker.execute {
            try {
                val (status, body) = request("GET", "/api/calendar?start=$start&end=$end", "Bearer $activeToken")
                if (status != 200) throw IllegalStateException("HTTP $status")
                val events = JSONArray(body)
                val entries = (0 until events.length()).map { index ->
                    val event = events.getJSONObject(index)
                    val props = event.getJSONObject("extendedProps")
                    CalendarEntry(event.getString("start"), event.getString("end"), event.optBoolean("allDay"),
                        event.optString("title"),
                        if (props.optBoolean("nonTreatmentEvent")) getString(R.string.event) else getString(R.string.patient),
                        props.optString("state"))
                }.sortedBy { it.start }
                runOnUiThread {
                    if (token != activeToken || section != Section.CALENDAR || requestId != calendarRequest) return@runOnUiThread
                    swipeRefresh.isRefreshing = false
                    calendarEntries = entries
                    calendarLoading = false
                    renderCalendar()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != activeToken || section != Section.CALENDAR || requestId != calendarRequest) return@runOnUiThread
                    swipeRefresh.isRefreshing = false
                    calendarLoading = false
                    calendarLoadError = true
                    renderCalendar()
                }
            }
        }
    }

    private fun renderCalendar() {
        calendarMonthTitle.text = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(displayedMonth.time)
        calendarSelectedDate.text = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(selectedCalendarDay.time)
        calendarWeekdays.removeAllViews()
        listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom").forEach { name ->
            calendarWeekdays.addView(TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(getColor(R.color.text_muted))
            }, LinearLayout.LayoutParams(0, dp(28), 1f))
        }
        calendarGrid.removeAllViews()
        val first = displayedMonth.clone() as Calendar
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = calendarKey(Calendar.getInstance())
        val selected = calendarKey(selectedCalendarDay)
        val weeks = (offset + days + 6) / 7
        repeat(weeks) { week ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) { weekday ->
                val number = week * 7 + weekday - offset + 1
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(4), 0, 0)
                }
                if (number in 1..days) {
                    val day = (first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, number) }
                    val key = calendarKey(day)
                    val count = entriesForDay(day).size
                    val selectedDay = key == selected
                    val label = TextView(this).apply {
                        text = number.toString()
                        gravity = Gravity.CENTER
                        textSize = 15f
                        setTextColor(getColor(when {
                            selectedDay -> R.color.surface
                            key == today -> R.color.brand_primary
                            else -> R.color.text_main
                        }))
                        if (selectedDay) background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(getColor(R.color.brand_primary))
                        }
                    }
                    cell.addView(label, LinearLayout.LayoutParams(dp(36), dp(36)))
                    if (count > 0) {
                        cell.addView(View(this).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(getColor(R.color.brand_primary))
                            }
                        }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { topMargin = dp(3) })
                    }
                    cell.contentDescription = getString(R.string.calendar_day_description,
                        SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(day.time), count)
                    cell.setOnClickListener {
                        selectedCalendarDay.timeInMillis = day.timeInMillis
                        renderCalendar()
                    }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(62), 1f))
            }
            calendarGrid.addView(row)
        }
        calendarDayEvents.removeAllViews()
        val dayKey = calendarKey(selectedCalendarDay)
        val rows = entriesForDay(selectedCalendarDay)
        if (calendarLoading) {
            addAgendaLine(getString(R.string.calendar_loading), target = calendarDayEvents)
            return
        }
        if (calendarLoadError) {
            addAgendaLine(getString(R.string.calendar_error), R.color.brand_danger, calendarDayEvents)
            return
        }
        if (rows.isEmpty()) addAgendaLine(getString(R.string.calendar_empty), target = calendarDayEvents)
        rows.forEach { entry ->
            val time = when {
                entry.allDay -> getString(R.string.all_day)
                !entry.start.startsWith(dayKey) -> getString(R.string.calendar_continues)
                entry.end.startsWith(dayKey) -> entry.start.substring(11, 16) + " – " + entry.end.substring(11, 16)
                else -> entry.start.substring(11, 16) + " →"
            }
            addAgendaRow(AgendaRow(time, entry.title, entry.kind, entry.state), calendarDayEvents)
        }
    }

    private fun loadTodayAgenda() {
        val activeToken = token ?: return
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
        val start = dateTimeFormat.format(today.time)
        val end = dateTimeFormat.format((today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.time)
        agendaDate.text = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(today.time)
        swipeRefresh.isRefreshing = true
        appointmentsCount.setText(R.string.count_initial)
        patientsCount.setText(R.string.count_initial)
        remindersCount.setText(R.string.count_initial)
        agendaList.removeAllViews()
        addAgendaLine(getString(R.string.agenda_loading))
        worker.execute {
            try {
                val (status, body) = request(
                    "GET", "/api/calendar?start=$start&end=$end", "Bearer $activeToken"
                )
                if (status != 200) throw IllegalStateException("HTTP $status")
                val events = JSONArray(body)
                val patientIds = mutableSetOf<Long>()
                val rows = mutableListOf<AgendaRow>()
                var reminders = 0
                val now = dateTimeFormat.format(Date())
                for (index in 0 until events.length()) {
                    val event = events.getJSONObject(index)
                    val props = event.getJSONObject("extendedProps")
                    if (!props.isNull("patientId")) patientIds.add(props.getLong("patientId"))
                    if (props.optString("state") != "CANCELLED"
                        && !props.isNull("patientId")
                        && event.getString("start") > now
                    ) reminders++
                    val time = if (event.optBoolean("allDay")) getString(R.string.all_day)
                        else event.getString("start").substring(11, 16) +
                            " – " + event.getString("end").substring(11, 16)
                    val kind = if (props.optBoolean("nonTreatmentEvent")) getString(R.string.event)
                        else getString(R.string.patient)
                    rows.add(AgendaRow(time, event.optString("title"), kind, props.optString("state")))
                }
                runOnUiThread {
                    if (token != activeToken || section != Section.HOME) return@runOnUiThread
                    swipeRefresh.isRefreshing = false
                    appointmentsCount.text = events.length().toString()
                    patientsCount.text = patientIds.size.toString()
                    remindersCount.text = reminders.toString()
                    agendaList.removeAllViews()
                    if (rows.isEmpty()) addAgendaLine(getString(R.string.agenda_empty))
                    rows.forEach(::addAgendaRow)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != activeToken || section != Section.HOME) return@runOnUiThread
                    swipeRefresh.isRefreshing = false
                    agendaList.removeAllViews()
                    addAgendaLine(getString(R.string.agenda_error), R.color.brand_danger)
                }
            }
        }
    }

    private fun addAgendaLine(value: String, color: Int = R.color.text_muted, target: LinearLayout = agendaList) {
        target.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(getColor(color))
            setPadding(0, dp(12), 0, dp(12))
        })
    }

    private fun addAgendaRow(row: AgendaRow, target: LinearLayout = agendaList) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.surface_card)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        item.addView(TextView(this).apply {
            text = row.time
            textSize = 14f
            setTextColor(getColor(R.color.text_main))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        item.addView(TextView(this).apply {
            text = row.title
            textSize = 16f
            setTextColor(getColor(R.color.text_main))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        })
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        footer.addView(TextView(this).apply {
            text = row.kind
            textSize = 13f
            setTextColor(getColor(R.color.text_muted))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (row.state == "COMPLETED" || row.state == "CANCELLED") {
            footer.addView(TextView(this).apply {
                text = getString(if (row.state == "COMPLETED") R.string.completed else R.string.cancelled)
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(getColor(if (row.state == "COMPLETED") R.color.brand_success else R.color.brand_danger))
                setBackgroundResource(if (row.state == "COMPLETED") R.drawable.success_soft else R.drawable.danger_soft)
                setPadding(dp(9), dp(4), dp(9), dp(4))
            })
        }
        item.addView(footer)
        target.addView(item, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun logout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.logout_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ -> confirmLogout() }
            .show()
    }

    private fun confirmLogout() {
        val currentToken = token ?: return
        if (!tokenStore.clear()) {
            Toast.makeText(this, R.string.session_clear_error, Toast.LENGTH_LONG).show()
            return
        }
        loginAttempt++
        token = null
        section = Section.HOME
        selectedPatientId = null
        patientsRequest++
        calendarRequest++
        calendarEntries = emptyList()
        calendarLoading = false
        calendarLoadError = false
        loggedInPanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
        swipeRefresh.isEnabled = false
        agendaList.removeAllViews()
        calendarGrid.removeAllViews()
        calendarDayEvents.removeAllViews()
        patientList.removeAllViews()
        patientsSearch.setQuery("", false)
        message.text = ""
        loginButton.isEnabled = true
        worker.execute {
            try {
                request("DELETE", "/api/auth/remember", "Bearer $currentToken")
            } catch (_: Exception) {
                // La revoca remota richiede connettività; il token locale è già eliminato.
            }
        }
    }

    private fun request(method: String, path: String, authorization: String): Pair<Int, String> {
        val base = BuildConfig.FISIO_API_BASE_URL.trimEnd('/')
        val url = URL("$base$path")
        require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Authorization", authorization)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
