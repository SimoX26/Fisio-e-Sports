package it.simosw.fisioesport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SecureTokenStore(context: Context, backendUrl: String) {
    private val appContext = context.applicationContext
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

    fun discardOfflineCopies() {
        val sharedPrefs = File(appContext.applicationInfo.dataDir, "shared_prefs")
        sharedPrefs.listFiles()?.filter { it.name.startsWith("fisio.session.") && it.name.endsWith(".xml") }
            ?.forEach { file ->
                try {
                    val store = appContext.getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                    val editor = store.edit()
                    store.all.keys.filter { it != "token" }.forEach(editor::remove)
                    editor.commit()
                } catch (_: Exception) { }
            }
        appContext.databaseList().filter { it.startsWith("fisio.offline.") && it.endsWith(".db") }
            .forEach { try { appContext.deleteDatabase(it) } catch (_: Exception) { } }
        try {
            val keys = keyStore()
            val aliases = keys.aliases()
            while (aliases.hasMoreElements()) {
                val name = aliases.nextElement()
                if (name.startsWith("fisio.offline.")) keys.deleteEntry(name)
            }
        } catch (_: Exception) {
            // La pulizia di chiavi di vecchie versioni non deve bloccare l'accesso.
        }
    }

    fun clear(): Boolean = try {
        preferences.edit().clear().commit()
    } catch (_: Exception) {
        false
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
