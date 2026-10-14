package it.simosw.fisioesport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureTokenStore(context: Context, backendUrl: String) {
    private val namespace = MessageDigest.getInstance("SHA-256")
        .digest(backendUrl.toByteArray(StandardCharsets.UTF_8))
        .take(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    private val alias = "fisio.session.$namespace"
    private val preferences = context.getSharedPreferences("fisio.session.$namespace", Context.MODE_PRIVATE)

    fun load(): String? {
        return try {
            val stored = preferences.getString("token", null) ?: return null
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val key = keyStore().getKey(alias, null) as? SecretKey ?: error("Chiave assente")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val token = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
            if (valid(token)) token else { clear(); null }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(token: String): Boolean {
        if (!valid(token)) {
            clear()
            return false
        }
        return try {
            val saved = preferences.edit().putString("token", encrypt(token)).commit()
            if (!saved) clear()
            saved
        } catch (_: Exception) {
            clear()
            false
        }
    }

    fun cachedIdentity(): String? = decryptEntry("identity")

    fun saveIdentity(username: String): Boolean {
        val previous = cachedIdentity()
        if (previous != username) {
            val editor = preferences.edit().remove("patients")
            preferences.all.keys.filter { it == "agenda" || it.startsWith("calendar_") }
                .forEach(editor::remove)
            editor.commit()
        }
        return saveEntry("identity", username)
    }

    fun saveAgenda(username: String, day: String, json: String): Boolean =
        saveSnapshot(username, "agenda", day, json)

    fun cachedAgenda(username: String, day: String): Pair<String, Long>? =
        cachedSnapshot(username, "agenda", day)

    fun saveCalendar(username: String, month: String, json: String): Boolean =
        saveSnapshot(username, "calendar_$month", month, json)

    fun cachedCalendar(username: String, month: String): Pair<String, Long>? =
        cachedSnapshot(username, "calendar_$month", month)

    private fun saveSnapshot(username: String, key: String, period: String, json: String): Boolean {
        if (cachedIdentity() != username) return false
        return saveEntry(key, JSONObject().put("period", period)
            .put("savedAt", System.currentTimeMillis()).put("rows", json).toString())
    }

    private fun cachedSnapshot(username: String, key: String, period: String): Pair<String, Long>? {
        if (cachedIdentity() != username) return null
        return try {
            val value = JSONObject(decryptEntry(key) ?: return null)
            if (value.getString("period") != period) return null
            value.getString("rows") to value.getLong("savedAt")
        } catch (_: Exception) { null }
    }

    fun savePatients(username: String, json: String): Boolean {
        if (cachedIdentity() != username) return false
        return saveEntry("patients", JSONObject().put("savedAt", System.currentTimeMillis())
            .put("rows", json).toString())
    }

    fun cachedPatients(username: String): Pair<String, Long>? {
        if (cachedIdentity() != username) return null
        return try {
            val value = JSONObject(decryptEntry("patients") ?: return null)
            value.getString("rows") to value.getLong("savedAt")
        } catch (_: Exception) { null }
    }

    fun clear(): Boolean = try {
        preferences.edit().clear().commit()
    } catch (_: Exception) {
        false
    }

    private fun saveEntry(name: String, value: String): Boolean = try {
        preferences.edit().putString(name, encrypt(value)).commit()
    } catch (_: Exception) { false }

    private fun decryptEntry(name: String): String? {
        return try {
            val stored = preferences.getString(name, null) ?: return null
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val key = keyStore().getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            preferences.edit().remove(name).commit()
            null
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore().getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun valid(token: String) = token.matches(Regex("[A-Za-z0-9_-]{43}"))
}
