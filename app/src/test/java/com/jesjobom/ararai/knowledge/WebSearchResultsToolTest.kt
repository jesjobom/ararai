package com.jesjobom.ararai.knowledge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchResultsToolTest {
    @Test
    fun `Exa returns at most ten ranked links without requesting contents`() = runTest {
        val requests = mutableListOf<WebSearchHttpRequest>()
        val transport = WebSearchHttpTransport { request ->
            requests += request
            jsonResponse(
                JsonObject().apply {
                    add(
                        "results",
                        JsonArray().apply {
                            (1..12).forEach { index ->
                                add(
                                    JsonObject().apply {
                                        addProperty("title", "Result $index")
                                        addProperty("url", "https://example.com/article/$index")
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        val result = ProviderWebSearchResultsTool(WebSearchProvider.Exa, { "token" }, transport) {
            42L
        }.search(WebSearchResultsRequest("Petrobras stocks", 10))

        assertTrue(result is WebSearchResultsResult.Success)
        assertEquals(10, (result as WebSearchResultsResult.Success).results.size)
        assertEquals("Result 1", result.results.first().title)
        assertEquals(42L, result.retrievedAtMillis)
        assertEquals(1, requests.size)
        assertEquals("https://api.exa.ai/search", requests.single().url)
        val body = JsonParser.parseString(requests.single().jsonBody).asJsonObject
        assertEquals(10, body.get("numResults").asInt)
        assertFalse(body.has("contents"))
    }

    @Test
    fun `Tavily performs only search and returns first ranked link`() = runTest {
        val requests = mutableListOf<WebSearchHttpRequest>()
        val transport = WebSearchHttpTransport { request ->
            requests += request
            jsonResponse(
                JsonObject().apply {
                    add(
                        "results",
                        JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    addProperty("title", "Election results")
                                    addProperty("url", "https://news.example.com/elections")
                                },
                            )
                        },
                    )
                },
            )
        }
        val result = ProviderWebSearchResultsTool(WebSearchProvider.Tavily, { "token" }, transport)
            .search(WebSearchResultsRequest("resultado das eleições", 1))

        assertTrue(result is WebSearchResultsResult.Success)
        assertEquals(1, (result as WebSearchResultsResult.Success).results.size)
        assertEquals("https://api.tavily.com/search", requests.single().url)
        val body = JsonParser.parseString(requests.single().jsonBody).asJsonObject
        assertEquals(1, body.get("max_results").asInt)
        assertFalse(body.get("include_raw_content").asBoolean)
        assertFalse(body.get("include_answer").asBoolean)
    }

    @Test
    fun `fallback preserves provider order and invalid requests do not use network`() = runTest {
        val requests = mutableListOf<WebSearchHttpRequest>()
        val exa = ProviderWebSearchResultsTool(
            WebSearchProvider.Exa,
            { "token" },
            WebSearchHttpTransport { request ->
                requests += request
                KnowledgeHttpResponse(429, "application/json", ByteArray(0))
            },
        )
        val tavily = ProviderWebSearchResultsTool(
            WebSearchProvider.Tavily,
            { "token" },
            WebSearchHttpTransport { request ->
                requests += request
                jsonResponse(
                    JsonObject().apply {
                        add(
                            "results",
                            JsonArray().apply {
                                add(
                                    JsonObject().apply {
                                        addProperty("title", "Fallback")
                                        addProperty("url", "https://example.com/fallback")
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        val tool = FallbackWebSearchResultsTool(listOf(exa, tavily))

        assertEquals(
            WebSearchResultsResult.Failure(ToolFailureReason.InvalidArguments),
            tool.search(WebSearchResultsRequest("query", 11)),
        )
        assertTrue(requests.isEmpty())
        assertTrue(tool.search(WebSearchResultsRequest("query", 1)) is WebSearchResultsResult.Success)
        assertEquals(listOf("https://api.exa.ai/search", "https://api.tavily.com/search"), requests.map { it.url })
    }

    @Test
    fun `web result link policy excludes unsafe destinations and preserves Wikipedia restriction`() {
        assertTrue(validWidgetExternalUrl("https://www.example.com/stocks?q=petrobras"))
        assertTrue(validWidgetExternalUrl("https://www.evilwikipedia.org/article"))
        listOf(
            "http://example.com/a",
            "https://user@example.com/a",
            "https://example.com:443/a",
            "https://localhost/a",
            "https://127.0.0.1/a",
            "https://en.wikipedia.org/w/api.php",
        ).forEach { assertFalse(validWidgetExternalUrl(it)) }
    }

    @Test
    fun `search filters invalid and repeated URLs without changing provider rank`() = runTest {
        val urls = listOf(
            "http://example.com/not-https",
            "https://en.wikipedia.org/w/api.php",
            "https://example.com/first",
            "https://example.com/first",
            "https://example.com/second",
        )
        val transport = WebSearchHttpTransport {
            jsonResponse(
                JsonObject().apply {
                    add(
                        "results",
                        JsonArray().apply {
                            urls.forEachIndexed { index, url ->
                                add(
                                    JsonObject().apply {
                                        addProperty("title", "Result $index")
                                        addProperty("url", url)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }

        val result = ProviderWebSearchResultsTool(WebSearchProvider.Exa, { "token" }, transport)
            .search(WebSearchResultsRequest("query", 10)) as WebSearchResultsResult.Success
        assertEquals(listOf("Result 2", "Result 4"), result.results.map { it.title })
    }

    private fun jsonResponse(value: JsonObject) = KnowledgeHttpResponse(
        status = 200,
        contentType = "application/json",
        body = value.toString().toByteArray(),
    )
}
