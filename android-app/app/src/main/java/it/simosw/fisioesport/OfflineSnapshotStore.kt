package it.simosw.fisioesport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate database and Keystore key for each backend and therapist. Clinical rows are encrypted. */
internal class OfflineSnapshotStore(private val context: Context, backend: String, username: String) {
    private val namespace = MessageDigest.getInstance("SHA-256")
        .digest("$backend\u0000$username".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private val databaseName = "fisio.offline.$namespace.db"
    private val alias = "fisio.offline.$namespace"
    private val collections = listOf("patients", "anamneses", "appointments", "treatmentPlans", "treatmentSessions", "waitlist")

    private inner class Helper : SQLiteOpenHelper(context, databaseName, null, 1) {
        override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
            db.execSQL("CREATE TABLE entries (kind TEXT NOT NULL, position INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(kind, position))")
            db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    fun savedAt(): Long? {
        if (!context.getDatabasePath(databaseName).exists()) return null
        Helper().use { helper ->
            helper.readableDatabase.rawQuery("SELECT value FROM metadata WHERE name = 'savedAt'", null).use { cursor ->
                return if (cursor.moveToFirst()) cursor.getString(0).toLongOrNull() else null
            }
        }
    }

    fun replace(json: String) {
        val snapshot = JSONObject(json)
        require(snapshot.getInt("version") == 1)
        val arrays = collections.associateWith { snapshot.getJSONArray(it) }
        Helper().use { helper ->
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("entries", null, null)
                for ((kind, rows) in arrays) {
                    for (position in 0 until rows.length()) {
                        val values = ContentValues().apply {
                            put("kind", kind)
                            put("position", position)
                            put("payload", encrypt(rows.getJSONObject(position).toString()))
                        }
                        db.insertOrThrow("entries", null, values)
                    }
                }
                db.execSQL("INSERT OR REPLACE INTO metadata(name, value) VALUES('savedAt', ?)",
                    arrayOf(System.currentTimeMillis().toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun rows(kind: String): List<JSONObject> {
        require(kind in collections)
        if (savedAt() == null) return emptyList()
        Helper().use { helper ->
            helper.readableDatabase.query("entries", arrayOf("payload"), "kind = ?", arrayOf(kind),
                null, null, "position").use { cursor ->
                val result = ArrayList<JSONObject>()
                while (cursor.moveToNext()) result.add(JSONObject(decrypt(cursor.getBlob(0))))
                return result
            }
        }
    }

    fun clear() {
        context.deleteDatabase(databaseName)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keys.containsAlias(alias)) keys.deleteEntry(alias)
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decrypt(bytes: ByteArray): String {
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
