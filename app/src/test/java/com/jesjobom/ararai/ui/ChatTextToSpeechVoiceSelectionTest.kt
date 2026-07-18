package com.jesjobom.ararai.ui

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatTextToSpeechVoiceSelectionTest {
    @Test
    fun selectsOnlyInstalledVoicesMatchingDetectedLanguage() {
        val portuguese = voice("pt-br", Locale.forLanguageTag("pt-BR"))
        val english = voice("en-us", Locale.US)
        val missingEnglish = voice(
            name = "en-gb-missing",
            locale = Locale.UK,
            features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
        )

        val result = compatibleVoices(
            voices = setOf(portuguese, missingEnglish, english),
            languageTag = "en",
            currentVoice = portuguese,
        )

        assertEquals(listOf(english), result)
    }

    @Test
    fun prefersLocalVoiceThenCurrentMatchQualityAndStableLocale() {
        val currentNetwork = voice("current", Locale.UK, quality = 100, network = true)
        val highQualityLocal = voice("local-us", Locale.US, quality = 500)
        val lowQualityLocal = voice("local-au", Locale.forLanguageTag("en-AU"), quality = 200)

        assertEquals(
            highQualityLocal,
            compatibleVoices(
                voices = setOf(lowQualityLocal, highQualityLocal, currentNetwork),
                languageTag = "en",
                currentVoice = currentNetwork,
            ).first(),
        )
        assertEquals(
            listOf(highQualityLocal, lowQualityLocal),
            compatibleVoices(
                voices = setOf(lowQualityLocal, highQualityLocal),
                languageTag = "en",
                currentVoice = null,
            ),
        )
    }

    @Test
    fun returnsNoCandidateForInvalidOrUnavailableLanguage() {
        val portuguese = voice("pt-br", Locale.forLanguageTag("pt-BR"))

        assertTrue(compatibleVoices(setOf(portuguese), "en", portuguese).isEmpty())
        assertTrue(compatibleVoices(setOf(portuguese), "und", portuguese).isEmpty())
        assertTrue(compatibleVoices(null, "en", null).isEmpty())
    }

    private fun voice(
        name: String,
        locale: Locale,
        quality: Int = Voice.QUALITY_NORMAL,
        latency: Int = Voice.LATENCY_NORMAL,
        network: Boolean = false,
        features: Set<String> = emptySet(),
    ): Voice = Voice(name, locale, quality, latency, network, features)
}
