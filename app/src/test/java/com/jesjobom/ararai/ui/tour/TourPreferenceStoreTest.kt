package com.jesjobom.ararai.ui.tour

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TourPreferenceStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("screen_tour_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshToursRemainIndependentUntilCompletedOrDismissed() {
        val store = InMemoryTourPreferenceStore()

        assertFalse(store.isTerminal(ScreenTour.Chat))
        assertFalse(store.isTerminal(ScreenTour.VoiceChat))

        store.markTerminal(ScreenTour.Chat)

        assertTrue(store.isTerminal(ScreenTour.Chat))
        assertFalse(store.isTerminal(ScreenTour.VoiceChat))
    }

    @Test
    fun newerContentVersionRemainsEligible() {
        val store = InMemoryTourPreferenceStore()

        store.markTerminal("chat", 1)

        assertTrue(store.isTerminal("chat", 1))
        assertFalse(store.isTerminal(ScreenTour.Chat))
    }

    @Test
    fun restoreAllMakesEveryTourEligibleAgain() {
        val store = InMemoryTourPreferenceStore()
        store.markTerminal(ScreenTour.Chat)
        store.markTerminal(ScreenTour.ModelManagement)

        store.restoreAll()

        assertFalse(store.isTerminal(ScreenTour.Chat))
        assertFalse(store.isTerminal(ScreenTour.ModelManagement))
    }

    @Test
    fun sharedPreferencesSurviveStoreRecreation() {
        SharedPreferencesTourPreferenceStore(context).markTerminal(ScreenTour.AssistantConfiguration)

        val recreated = SharedPreferencesTourPreferenceStore(context)

        assertTrue(recreated.isTerminal(ScreenTour.AssistantConfiguration))
        recreated.restoreAll()
        assertFalse(SharedPreferencesTourPreferenceStore(context).isTerminal(ScreenTour.AssistantConfiguration))
    }
}
