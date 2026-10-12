package it.simosw.fisioesport

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var token: String? = null
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
    private lateinit var refreshButton: Button

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
        refreshButton = findViewById(R.id.refreshButton)
        loginButton.setOnClickListener { login() }
        refreshButton.setOnClickListener { loadTodayAgenda() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
    }

    private fun login() {
        val user = username.text.toString().trim()
        val secret = password.text.toString()
        if (user.isEmpty() || secret.isEmpty()) {
            message.setText(R.string.credentials_required)
            return
        }
        loginButton.isEnabled = false
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
                    loadTodayAgenda()
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
            message.setText(resource)
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
        refreshButton.isEnabled = false
        appointmentsCount.setText(R.string.appointments_count_initial)
        patientsCount.setText(R.string.patients_count_initial)
        remindersCount.setText(R.string.reminders_count_initial)
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
                val rows = mutableListOf<String>()
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
                    val state = when (props.optString("state")) {
                        "COMPLETED" -> " · " + getString(R.string.completed)
                        "CANCELLED" -> " · " + getString(R.string.cancelled)
                        else -> ""
                    }
                    rows.add(time + "  " + event.optString("title") + "\n" + kind + state)
                }
                runOnUiThread {
                    if (token != activeToken) return@runOnUiThread
                    refreshButton.isEnabled = true
                    appointmentsCount.text = getString(R.string.appointments_count, events.length())
                    patientsCount.text = getString(R.string.patients_count, patientIds.size)
                    remindersCount.text = getString(R.string.reminders_count, reminders)
                    agendaList.removeAllViews()
                    if (rows.isEmpty()) addAgendaLine(getString(R.string.agenda_empty))
                    rows.forEach(::addAgendaLine)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != activeToken) return@runOnUiThread
                    refreshButton.isEnabled = true
                    agendaList.removeAllViews()
                    addAgendaLine(getString(R.string.agenda_error))
                }
            }
        }
    }

    private fun addAgendaLine(value: String) {
        agendaList.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, 12, 0, 12)
        })
    }

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
        loggedInPanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        agendaList.removeAllViews()
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
