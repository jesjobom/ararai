package com.jesjobom.ararai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedWidgetPresentationTest {
    @Test
    fun `allows only canonical language Wikipedia articles`() {
        val accepted = listOf(
            "https://en.wikipedia.org/wiki/Ada_Lovelace",
            "https://pt.wikipedia.org/wiki/Intelig%C3%AAncia_artificial",
        )

        accepted.forEach { url -> assertEquals(url, allowedManagedWidgetArticleUrl(url)) }
    }

    @Test
    fun `rejects unsafe schemes hosts ports credentials and non-article paths`() {
        val rejected = listOf(
            "javascript:alert(1)",
            "http://en.wikipedia.org/wiki/Ada_Lovelace",
            "https://wikipedia.org/wiki/Ada_Lovelace",
            "https://en.wikipedia.org.evil.test/wiki/Ada_Lovelace",
            "https://user@en.wikipedia.org/wiki/Ada_Lovelace",
            "https://en.wikipedia.org:443/wiki/Ada_Lovelace",
            "https://en.wikipedia.org/w/api.php",
            "https://en.wikipedia.org/wiki/",
            "https://en.wikipedia.org/wiki/Ada_Lovelace?oldid=1",
            "https://en.wikipedia.org/wiki/Ada_Lovelace#History",
        )

        rejected.forEach { url -> assertNull(url, allowedManagedWidgetArticleUrl(url)) }
    }

    @Test
    fun `returns controlled empty state for invalid cached tree`() {
        assertNull(decodeManagedWidgetPresentationOrNull("not-json"))
        assertNull(
            decodeManagedWidgetPresentationOrNull(
                """{"type":"html","html":"<script>alert(1)</script>"}""",
            ),
        )
        assertTrue(
            decodeManagedWidgetPresentationOrNull(
                """{"type":"text","text":"safe","tone":"neutral"}""",
            ) != null,
        )
    }
}
