package com.jesjobom.ararai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jesjobom.ararai.knowledge.EncryptedWebSearchPreferences
import com.jesjobom.ararai.knowledge.WebSearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WebSearchCredentialStoreTest {
    @Test
    fun tokenIsEncryptedAtRestRestoredPrivatelyAndRemoved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val token = "test-only-provider-token-123"
        val store = EncryptedWebSearchPreferences(context)
        store.removeToken(WebSearchProvider.Tavily)

        store.saveToken(WebSearchProvider.Tavily, token)
        store.setProviderEnabled(WebSearchProvider.Tavily, true)

        val restored = EncryptedWebSearchPreferences(context)
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
}
