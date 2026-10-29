package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWikipediaSelectedPageCardTest {
    private val capability = WidgetToolCapability("wikipedia_pages", 2)
    private val algorithm = WidgetAlgorithmArtifact(
        steps = listOf(
            WidgetAlgorithmStep(
                id = "page_lookup",
                kind = WidgetAlgorithmStepKind.ToolCall,
                objective = "Get one selected page and its introduction",
                dependencies = emptyList(),
                tool = capability,
                selectionMode = WidgetToolSelectionMode.SeededRandom,
            ),
        ),
        presentationObjective = "Show the selected page",
        renderCapability = WidgetRenderCapability.CollectionCard,
    )

    @Test
    fun `version two selected page renders its title complete introduction and proven URL`() {
        val raw = requireNotNull(WidgetCollectionCardArtifact.selectedWikipediaPage(algorithm))
        val parsed = WidgetCollectionCardArtifact.parse(raw, algorithm) as WidgetSourceFragmentParseResult.Valid
        val source = parsed.artifact.source
        assertTrue(source.contains("runtime.collectionCard"))
        assertTrue(source.contains("\"items\":\"pages\""))
        assertTrue(source.contains("\"title\":\"title\""))
        assertTrue(source.contains("\"body\":\"extract\""))
        assertTrue(source.contains("\"url\":\"canonicalUrl\""))
        assertTrue(source.contains("\"selection\":\"first\""))
    }

    @Test
    fun `shortcut preserves older tool versions and custom presentation paths`() {
        val legacy = algorithm.copy(
            steps = algorithm.steps.map {
                it.copy(tool = WidgetToolCapability("wikipedia_pages", 1))
            },
        )
        assertNull(
            WidgetCollectionCardArtifact.selectedWikipediaPage(legacy),
        )
        assertNull(
            WidgetCollectionCardArtifact.selectedWikipediaPage(
                algorithm.copy(renderCapability = WidgetRenderCapability.CustomSource),
            ),
        )
        assertNull(
            WidgetCollectionCardArtifact.selectedWikipediaPage(
                algorithm.copy(
                    steps = algorithm.steps + WidgetAlgorithmStep(
                        id = "transform",
                        kind = WidgetAlgorithmStepKind.Transform,
                        objective = "Filter",
                        dependencies = listOf("page_lookup"),
                        tool = null,
                    ),
                ),
            ),
        )
        assertEquals(1, algorithm.toolCallSteps.size)
    }
}
