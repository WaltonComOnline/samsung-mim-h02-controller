package io.github.waltoncomonline.samsungmimh02.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.pairingDataStore by preferencesDataStore(name = "pairing")

data class PairingMetadata(
    val host: String,
    val port: Int,
    val duid: String,
    val model: String,
)

data class SavedPairing(
    val metadata: PairingMetadata,
    val token: String,
)

class TokenStore(context: Context) {
    private val dataStore = context.applicationContext.pairingDataStore

    suspend fun save(metadata: PairingMetadata, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, encryptionKey())
        }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        dataStore.edit { values ->
            values[Keys.host] = metadata.host
            values[Keys.port] = metadata.port
            values[Keys.duid] = metadata.duid
            values[Keys.model] = metadata.model
            values[Keys.token] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            values[Keys.iv] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }
    }

    suspend fun metadata(): PairingMetadata? {
        val values = dataStore.data.first()
        val host = values[Keys.host] ?: return null
        val duid = values[Keys.duid] ?: return null
        return PairingMetadata(
            host = host,
            port = values[Keys.port] ?: 2878,
            duid = duid,
            model = values[Keys.model] ?: "Samsung AC",
        )
    }

    suspend fun load(): SavedPairing? {
        val metadata = metadata() ?: return null
        val values = dataStore.data.first()
        val encrypted = values[Keys.token] ?: return null
        val iv = values[Keys.iv] ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
        }
        val token = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            .toString(Charsets.UTF_8)
        return SavedPairing(metadata, token)
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private object Keys {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val duid = stringPreferencesKey("duid")
        val model = stringPreferencesKey("model")
        val token = stringPreferencesKey("token_ciphertext")
        val iv = stringPreferencesKey("token_iv")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "samsung_mim_h02_pairing_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
