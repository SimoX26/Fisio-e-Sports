package it.simosw.fisioesport

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
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
        loginButton.setOnClickListener { login() }
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
