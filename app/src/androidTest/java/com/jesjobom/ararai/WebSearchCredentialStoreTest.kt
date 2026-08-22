package com.jesjobom.ararai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jesjobom.ararai.knowledge.EncryptedWebSearchPreferences
import com.jesjobom.ararai.knowledge.ProviderTokenCipher
import com.jesjobom.ararai.knowledge.WebSearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(AndroidJUnit4::class)
class WebSearchCredentialStoreTest {
    @Test
    fun tokenIsEncryptedAtRestRestoredPrivatelyAndRemoved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        clearWebSearchPreferences(context)
        val token = "test-only-provider-token-123"
        val store = testStore(context)
        store.removeToken(WebSearchProvider.Tavily)

        store.saveToken(WebSearchProvider.Tavily, token)
        store.setProviderEnabled(WebSearchProvider.Tavily, true)

        val restored = testStore(context)
        assertEquals(token, restored.token(WebSearchProvider.Tavily))
        assertTrue(restored.settings.value.isConfigured(WebSearchProvider.Tavily))
        assertTrue(restored.settings.value.isEnabled(WebSearchProvider.Tavily))
        assertTrue(restored.settings.value.isPreferred(WebSearchProvider.Tavily))
        val sharedPreferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        sharedPreferencesDirectory.listFiles().orEmpty().forEach { file ->
            assertFalse("${file.name} contains plaintext provider token", file.readText().contains(token))
        }

        restored.removeToken(WebSearchProvider.Tavily)
        assertNull(restored.token(WebSearchProvider.Tavily))
        assertFalse(restored.settings.value.isConfigured(WebSearchProvider.Tavily))
    }

    @Test
    fun malformedCiphertextIsRetainedButProviderIsDisabledUntilReplacement() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        clearWebSearchPreferences(context)
        val credentials =
            context.getSharedPreferences(
                TEST_CREDENTIAL_PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            )
        val configuration =
            context.getSharedPreferences(
                TEST_CONFIGURATION_PREFERENCES,
                android.content.Context.MODE_PRIVATE,
            )
        credentials.edit().putString("token_exa", "malformed-ciphertext").commit()
        configuration.edit()
            .putStringSet(EncryptedWebSearchPreferences.KEY_ENABLED_PROVIDERS, setOf(WebSearchProvider.Exa.name))
            .commit()

        val store = testStore(context)

        assertFalse(store.settings.value.isConfigured(WebSearchProvider.Exa))
        assertFalse(store.settings.value.isEnabled(WebSearchProvider.Exa))
        assertTrue(store.settings.value.isUnreadable(WebSearchProvider.Exa))
        assertNull(store.token(WebSearchProvider.Exa))
        assertEquals("malformed-ciphertext", credentials.getString("token_exa", null))

        store.saveToken(WebSearchProvider.Exa, "replacement-token")

        assertTrue(store.settings.value.isConfigured(WebSearchProvider.Exa))
        assertFalse(store.settings.value.isUnreadable(WebSearchProvider.Exa))
        assertFalse(store.settings.value.isEnabled(WebSearchProvider.Exa))
        assertEquals("replacement-token", store.token(WebSearchProvider.Exa))
    }

    @Test
    fun keystoreFailureDoesNotExposeOrEnableStoredCredential() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        clearWebSearchPreferences(context)
        context.getSharedPreferences(
            TEST_CREDENTIAL_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        ).edit().putString("token_tavily", "opaque-secret-data").commit()
        context.getSharedPreferences(
            TEST_CONFIGURATION_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        ).edit()
            .putStringSet(EncryptedWebSearchPreferences.KEY_ENABLED_PROVIDERS, setOf(WebSearchProvider.Tavily.name))
            .commit()

        val store = testStore(context, FailingProviderTokenCipher)

        assertNull(store.token(WebSearchProvider.Tavily))
        assertFalse(store.settings.value.isConfigured(WebSearchProvider.Tavily))
        assertFalse(store.settings.value.isEnabled(WebSearchProvider.Tavily))
        assertTrue(store.settings.value.isUnreadable(WebSearchProvider.Tavily))
    }

    @Test
    fun deletedAndroidKeystoreKeyDisablesProviderUntilCredentialReplacement() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        clearWebSearchPreferences(context)
        val cipher = IsolatedAndroidKeystoreCipher()
        cipher.deleteKey()

        try {
            val store = testStore(context, cipher)
            store.saveToken(WebSearchProvider.Exa, "test-only-invalidated-key-token")
            store.setProviderEnabled(WebSearchProvider.Exa, true)
            assertTrue(store.settings.value.isEnabled(WebSearchProvider.Exa))

            cipher.deleteKey()

            val invalidated = testStore(context, cipher)
            assertNull(invalidated.token(WebSearchProvider.Exa))
            assertFalse(invalidated.settings.value.isConfigured(WebSearchProvider.Exa))
            assertFalse(invalidated.settings.value.isEnabled(WebSearchProvider.Exa))
            assertTrue(invalidated.settings.value.isUnreadable(WebSearchProvider.Exa))

            invalidated.saveToken(WebSearchProvider.Exa, "test-only-replacement-token")
            assertEquals("test-only-replacement-token", invalidated.token(WebSearchProvider.Exa))
            assertTrue(invalidated.settings.value.isConfigured(WebSearchProvider.Exa))
            assertFalse(invalidated.settings.value.isUnreadable(WebSearchProvider.Exa))
            assertFalse(invalidated.settings.value.isEnabled(WebSearchProvider.Exa))
        } finally {
            cipher.deleteKey()
            clearWebSearchPreferences(context)
        }
    }

    private fun clearWebSearchPreferences(context: android.content.Context) {
        context.getSharedPreferences(
            TEST_CREDENTIAL_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        ).edit().clear().commit()
        context.getSharedPreferences(
            TEST_CONFIGURATION_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun testStore(
        context: android.content.Context,
        cipher: ProviderTokenCipher = com.jesjobom.ararai.knowledge.AndroidProviderTokenCipher(),
    ) = EncryptedWebSearchPreferences(
        context = context,
        cipher = cipher,
        credentialPreferencesName = TEST_CREDENTIAL_PREFERENCES,
        configurationPreferencesName = TEST_CONFIGURATION_PREFERENCES,
    )

    private companion object {
        const val TEST_CREDENTIAL_PREFERENCES = "web_search_credentials_android_test"
        const val TEST_CONFIGURATION_PREFERENCES = "web_search_configuration_android_test"
    }
}

private class IsolatedAndroidKeystoreCipher : ProviderTokenCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.base64()}.${encrypted.base64()}"
    }

    override fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, parts[0].decodeBase64()))
        return cipher.doFinal(parts[1].decodeBase64()).toString(Charsets.UTF_8)
    }

    fun deleteKey() {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "ararai_android_test_invalidated_web_search_key"
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}

private data object FailingProviderTokenCipher : ProviderTokenCipher {
    override fun encrypt(value: String): String = error("Encryption is unavailable")

    override fun decrypt(value: String): String = error("Keystore key is unavailable")
}
