package com.jesjobom.ararai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPreferencesTest {
    @Test
    fun `token configuration and provider enablement are separate`() {
        val preferences = InMemoryWebSearchPreferences()

        preferences.saveToken(WebSearchProvider.Tavily, "tvly-user-token")

        assertTrue(preferences.settings.value.isConfigured(WebSearchProvider.Tavily))
        assertFalse(preferences.settings.value.isEnabled(WebSearchProvider.Tavily))
        assertEquals("tvly-user-token", preferences.token(WebSearchProvider.Tavily))

        preferences.setProviderEnabled(WebSearchProvider.Tavily, true)

        assertTrue(preferences.settings.value.isEnabled(WebSearchProvider.Tavily))
        assertTrue(preferences.settings.value.isPreferred(WebSearchProvider.Tavily))
    }

    @Test
    fun `Exa is always preferred when both providers are enabled`() {
        val preferences =
            InMemoryWebSearchPreferences(
                initialTokens =
                mapOf(
                    WebSearchProvider.Tavily to "tavily",
                    WebSearchProvider.Exa to "exa",
                ),
                enabledProviders = WebSearchProvider.entries.toSet(),
            )

        assertEquals(
            listOf(WebSearchProvider.Exa, WebSearchProvider.Tavily),
            preferences.settings.value.orderedEnabledProviders,
        )

        preferences.removeToken(WebSearchProvider.Exa)
        assertEquals(WebSearchProvider.Tavily, preferences.settings.value.preferredProvider)
        assertNull(preferences.token(WebSearchProvider.Exa))
        assertTrue(preferences.settings.value.isConfigured(WebSearchProvider.Tavily))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot enable an unconfigured provider`() {
        InMemoryWebSearchPreferences().setProviderEnabled(WebSearchProvider.Exa, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects token containing whitespace`() {
        InMemoryWebSearchPreferences().saveToken(WebSearchProvider.Exa, "bad token")
    }
}
