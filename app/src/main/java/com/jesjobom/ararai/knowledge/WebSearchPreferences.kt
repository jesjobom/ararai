package com.jesjobom.ararai.knowledge

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WebSearchSettings(
    val enabledProviders: Set<WebSearchProvider> = emptySet(),
    val configuredProviders: Set<WebSearchProvider> = emptySet(),
    val unreadableProviders: Set<WebSearchProvider> = emptySet(),
) {
    fun isConfigured(provider: WebSearchProvider): Boolean = provider in configuredProviders
    fun isEnabled(provider: WebSearchProvider): Boolean = provider in enabledProviders
    fun isUnreadable(provider: WebSearchProvider): Boolean = provider in unreadableProviders
    fun isPreferred(provider: WebSearchProvider): Boolean = preferredProvider == provider

    val orderedEnabledProviders: List<WebSearchProvider>
        get() = WEB_SEARCH_PROVIDER_PRIORITY.filter(enabledProviders::contains)

    val preferredProvider: WebSearchProvider?
        get() = orderedEnabledProviders.firstOrNull()
}

interface WebSearchPreferences {
    val settings: StateFlow<WebSearchSettings>

    fun saveToken(
        provider: WebSearchProvider,
        token: String,
    )

    fun removeToken(provider: WebSearchProvider)

    fun setProviderEnabled(
        provider: WebSearchProvider,
        enabled: Boolean,
    )

    fun token(provider: WebSearchProvider): String?
}

class InMemoryWebSearchPreferences(
    initialTokens: Map<WebSearchProvider, String> = emptyMap(),
    enabledProviders: Set<WebSearchProvider> = emptySet(),
) : WebSearchPreferences {
    private val tokens = initialTokens.toMutableMap()
    private val mutableSettings =
        MutableStateFlow(
            WebSearchSettings(
                enabledProviders = enabledProviders.filter(tokens::containsKey).toSet(),
                configuredProviders = tokens.keys.toSet(),
            ).normalized(),
        )
    override val settings = mutableSettings.asStateFlow()

    override fun saveToken(
        provider: WebSearchProvider,
        token: String,
    ) {
        tokens[provider] = validateProviderToken(token)
        publish()
    }

    override fun removeToken(provider: WebSearchProvider) {
        tokens.remove(provider)
        mutableSettings.value =
            mutableSettings.value.copy(
                enabledProviders = mutableSettings.value.enabledProviders - provider,
            ).normalized()
        publish()
    }

    override fun setProviderEnabled(
        provider: WebSearchProvider,
        enabled: Boolean,
    ) {
        require(!enabled || tokens.containsKey(provider)) {
            "Provider must have a configured credential before enablement"
        }
        val enabledProviders =
            if (enabled) {
                mutableSettings.value.enabledProviders + provider
            } else {
                mutableSettings.value.enabledProviders - provider
            }
        mutableSettings.value =
            mutableSettings.value.copy(enabledProviders = enabledProviders).normalized()
    }

    override fun token(provider: WebSearchProvider): String? = tokens[provider]

    private fun publish() {
        mutableSettings.value =
            mutableSettings.value.copy(configuredProviders = tokens.keys.toSet())
    }
}

@Suppress("TooManyFunctions")
class EncryptedWebSearchPreferences internal constructor(
    context: Context,
    private val cipher: ProviderTokenCipher,
    credentialPreferencesName: String = CREDENTIAL_PREFERENCES,
    configurationPreferencesName: String = CONFIGURATION_PREFERENCES,
) : WebSearchPreferences {
    constructor(context: Context) : this(context, AndroidProviderTokenCipher())

    private val credentials =
        context.getSharedPreferences(credentialPreferencesName, Context.MODE_PRIVATE)
    private val configuration =
        context.getSharedPreferences(configurationPreferencesName, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableSettings = MutableStateFlow(loadSettings())
    override val settings = mutableSettings.asStateFlow()

    override fun saveToken(
        provider: WebSearchProvider,
        token: String,
    ) {
        val validated = validateProviderToken(token)
        synchronized(lock) {
            credentials.edit().putString(provider.tokenKey(), cipher.encrypt(validated)).commit()
            publishLocked()
        }
    }

    override fun removeToken(provider: WebSearchProvider) {
        synchronized(lock) {
            credentials.edit().remove(provider.tokenKey()).commit()
            val current = loadSettings()
            persistConfigurationLocked(
                current.copy(
                    enabledProviders = current.enabledProviders - provider,
                ).normalized(),
            )
            publishLocked()
        }
    }

    override fun setProviderEnabled(
        provider: WebSearchProvider,
        enabled: Boolean,
    ) {
        synchronized(lock) {
            require(!enabled || credentialStateLocked(provider) is CredentialState.Readable) {
                "Provider must have a configured credential before enablement"
            }
            val current = loadSettings()
            persistConfigurationLocked(
                current.copy(
                    enabledProviders =
                    if (enabled) {
                        current.enabledProviders + provider
                    } else {
                        current.enabledProviders - provider
                    },
                ).normalized(),
            )
            publishLocked()
        }
    }

    override fun token(provider: WebSearchProvider): String? = synchronized(lock) {
        when (val state = credentialStateLocked(provider)) {
            CredentialState.Absent -> null
            is CredentialState.Readable -> state.token
            CredentialState.Unreadable -> {
                disableProviderLocked(provider)
                publishLocked()
                null
            }
        }
    }

    private fun loadSettings(): WebSearchSettings = synchronized(lock) {
        val credentialStates = WebSearchProvider.entries.associateWith(::credentialStateLocked)
        val configured = credentialStates.filterValues { it is CredentialState.Readable }.keys
        val unreadable = credentialStates.filterValues { it == CredentialState.Unreadable }.keys
        val selectedProvider = selectedProviderLocked()
        val legacySelected = selectedProvider?.takeIf(configured::contains)
        val storedEnabled =
            configuration.getStringSet(KEY_ENABLED_PROVIDERS, null)
                ?.mapNotNull { name -> WebSearchProvider.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: legacySelected?.let(::setOf).orEmpty()
        val settings = WebSearchSettings(
            enabledProviders = storedEnabled,
            configuredProviders = configured,
            unreadableProviders = unreadable,
        ).normalized()
        if (settings.enabledProviders != storedEnabled || selectedProvider in unreadable) {
            persistConfigurationLocked(settings)
        }
        settings
    }

    private fun publishLocked() {
        mutableSettings.value = loadSettings()
    }

    private fun selectedProviderLocked(): WebSearchProvider? = configuration.getString(KEY_SELECTED_PROVIDER, null)
        ?.let { stored -> WebSearchProvider.entries.firstOrNull { it.name == stored } }

    private fun persistConfigurationLocked(settings: WebSearchSettings) {
        configuration.edit()
            .putStringSet(KEY_ENABLED_PROVIDERS, settings.enabledProviders.map { it.name }.toSet())
            .remove(KEY_SELECTED_PROVIDER)
            .commit()
    }

    private fun credentialStateLocked(provider: WebSearchProvider): CredentialState {
        val encrypted = credentials.getString(provider.tokenKey(), null) ?: return CredentialState.Absent
        return runCatching { validateProviderToken(cipher.decrypt(encrypted)) }
            .fold(
                onSuccess = CredentialState::Readable,
                onFailure = { CredentialState.Unreadable },
            )
    }

    private fun disableProviderLocked(provider: WebSearchProvider) {
        val enabled = configuration.getStringSet(KEY_ENABLED_PROVIDERS, emptySet()).orEmpty()
        val selected = selectedProviderLocked()
        if (provider.name !in enabled && selected != provider) return
        configuration.edit()
            .putStringSet(KEY_ENABLED_PROVIDERS, enabled - provider.name)
            .remove(KEY_SELECTED_PROVIDER)
            .commit()
    }

    private fun WebSearchProvider.tokenKey(): String = "token_${name.lowercase()}"

    internal companion object {
        const val CREDENTIAL_PREFERENCES = "web_search_credentials"
        const val CONFIGURATION_PREFERENCES = "web_search_configuration"
        const val KEY_SELECTED_PROVIDER = "selected_provider"
        const val KEY_ENABLED_PROVIDERS = "enabled_providers"
    }
}

private sealed interface CredentialState {
    data object Absent : CredentialState

    data class Readable(
        val token: String,
    ) : CredentialState

    data object Unreadable : CredentialState
}

private fun WebSearchSettings.normalized(): WebSearchSettings {
    val enabled = enabledProviders.intersect(configuredProviders)
    val unreadable = unreadableProviders - configuredProviders
    return copy(enabledProviders = enabled, unreadableProviders = unreadable)
}

private val WEB_SEARCH_PROVIDER_PRIORITY = listOf(WebSearchProvider.Exa, WebSearchProvider.Tavily)

internal interface ProviderTokenCipher {
    fun encrypt(value: String): String

    fun decrypt(value: String): String
}

internal class AndroidProviderTokenCipher : ProviderTokenCipher {
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

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER).run {
            init(
                android.security.keystore.KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                            android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
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
        const val KEY_ALIAS = "ararai_web_search_tokens_v1"
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}

private fun validateProviderToken(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "Provider token cannot be empty" }
    require(normalized.length <= MAX_PROVIDER_TOKEN_LENGTH) { "Provider token is too long" }
    require(normalized.none(Char::isWhitespace)) { "Provider token cannot contain whitespace" }
    require(normalized.none(Char::isISOControl)) { "Provider token cannot contain control characters" }
    return normalized
}

private const val MAX_PROVIDER_TOKEN_LENGTH = 512
