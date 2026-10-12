package it.simosw.fisioesport

import android.os.Bundle
import android.content.res.ColorStateList
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.activity.OnBackPressedCallback
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
    private data class PatientRow(val name: String, val phone: String, val created: String)
    private enum class Section { HOME, PATIENTS }

    private val worker = Executors.newSingleThreadExecutor()
    private var token: String? = null
    private var section = Section.HOME
    private var patientsRequest = 0
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
    private lateinit var patientsPanel: View
    private lateinit var homeTab: MaterialButton
    private lateinit var patientsTab: MaterialButton
    private lateinit var patientsSearch: SearchView
    private lateinit var patientResults: TextView
    private lateinit var patientList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
        contentScroll = findViewById(R.id.contentScroll)
        homePanel = findViewById(R.id.homePanel)
        patientsPanel = findViewById(R.id.patientsPanel)
        homeTab = findViewById(R.id.homeTab)
        patientsTab = findViewById(R.id.patientsTab)
        patientsSearch = findViewById(R.id.patientsSearch)
        patientResults = findViewById(R.id.patientResults)
        patientList = findViewById(R.id.patientList)
        swipeRefresh.isEnabled = false
        swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> contentScroll.canScrollVertically(-1) }
        swipeRefresh.setOnRefreshListener {
            if (section == Section.HOME) loadTodayAgenda() else loadPatients()
        }
        loginButton.setOnClickListener { login() }
        homeTab.setOnClickListener { showSection(Section.HOME) }
        patientsTab.setOnClickListener { showSection(Section.PATIENTS) }
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
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (token != null && section == Section.PATIENTS) showSection(Section.HOME)
                else finish()
            }
        })
    }

    private fun login() {
        val user = username.text.toString().trim()
        val secret = password.text.toString()
        if (user.isEmpty() || secret.isEmpty()) {
            message.setText(R.string.credentials_required)
            return
        }
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
                issuedToken = JSONObject(body).getString("token")
                val (identityStatus, identityBody) = request("GET", "/api/me", "Bearer $issuedToken")
                if (identityStatus != 200) throw IllegalStateException("HTTP $identityStatus")
                val displayName = JSONObject(identityBody).getString("username")
                runOnUiThread {
                    token = issuedToken
                    password.text.clear()
                    welcome.text = getString(R.string.welcome, displayName)
                    loginPanel.visibility = View.GONE
                    loggedInPanel.visibility = View.VISIBLE
                    swipeRefresh.isEnabled = true
                    showSection(Section.HOME)
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
        swipeRefresh.isRefreshing = false
        homePanel.visibility = if (next == Section.HOME) View.VISIBLE else View.GONE
        patientsPanel.visibility = if (next == Section.PATIENTS) View.VISIBLE else View.GONE
        selectTab(homeTab, next == Section.HOME)
        selectTab(patientsTab, next == Section.PATIENTS)
        contentScroll.scrollTo(0, 0)
        if (next == Section.HOME) loadTodayAgenda() else loadPatients()
    }

    private fun selectTab(tab: MaterialButton, selected: Boolean) {
        tab.backgroundTintList = ColorStateList.valueOf(getColor(
            if (selected) R.color.brand_primary_soft else R.color.surface
        ))
        tab.setTextColor(getColor(if (selected) R.color.brand_primary else R.color.text_muted))
        tab.isSelected = selected
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

    private fun addAgendaLine(value: String, color: Int = R.color.text_muted) {
        agendaList.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(getColor(color))
            setPadding(0, dp(12), 0, dp(12))
        })
    }

    private fun addAgendaRow(row: AgendaRow) {
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
        agendaList.addView(item, LinearLayout.LayoutParams(
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
        token = null
        section = Section.HOME
        patientsRequest++
        loggedInPanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false
        swipeRefresh.isEnabled = false
        agendaList.removeAllViews()
        patientList.removeAllViews()
        patientsSearch.setQuery("", false)
        message.text = ""
        loginButton.isEnabled = true
        worker.execute {
            try {
                request("DELETE", "/api/auth/remember", "Bearer $currentToken")
            } catch (_: Exception) {
                // Il token non è conservato sul dispositivo; la revoca remota richiede connettività.
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
