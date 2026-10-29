package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCollectionListArtifactTest {
    @Test
    fun `ranked web results have a deterministic bounded render artifact`() {
        val algorithm = WidgetAlgorithmArtifact(
            steps = listOf(
                WidgetAlgorithmStep(
                    id = "search_call",
                    kind = WidgetAlgorithmStepKind.ToolCall,
                    objective = "Search",
                    dependencies = emptyList(),
                    tool = WidgetToolCapability("web_search_top_results", 1),
                ),
            ),
            presentationObjective = "Show up to ten results",
            renderCapability = WidgetRenderCapability.CollectionList,
        )

        val raw = requireNotNull(WidgetCollectionListArtifact.rankedWebResults(algorithm))
        val parsed = WidgetCollectionListArtifact.parse(raw, algorithm) as WidgetSourceFragmentParseResult.Valid

        assertTrue(parsed.artifact.source.contains("runtime.collectionList"))
        assertTrue(parsed.artifact.source.contains("\"items\":\"results\""))
        assertTrue(parsed.artifact.source.contains("\"url\":\"canonicalUrl\""))
        assertEquals(
            null,
            WidgetCollectionListArtifact.rankedWebResults(
                algorithm.copy(renderCapability = WidgetRenderCapability.CollectionCard),
            ),
        )
    }

    @Test
    fun `collection list artifact renders every returned item with provenance`() {
        val algorithm = WidgetAlgorithmArtifact(
            steps = listOf(
                WidgetAlgorithmStep(
                    id = "search_call",
                    kind = WidgetAlgorithmStepKind.ToolCall,
                    objective = "Search",
                    dependencies = emptyList(),
                    tool = WidgetToolCapability("web_search_top_results", 1),
                ),
            ),
            presentationObjective = "Show results",
            renderCapability = WidgetRenderCapability.CollectionList,
        )
        val raw = """{"alias":"search_call","items":"results","title":"title",""" +
            """"url":"canonicalUrl","fallback":"No results"}"""

        val parsed = WidgetCollectionListArtifact.parse(raw, algorithm) as WidgetSourceFragmentParseResult.Valid

        assertTrue(parsed.artifact.source.contains("runtime.collectionList(outcomes, \"search_call\""))
        assertTrue(parsed.artifact.source.contains("\"url\":\"canonicalUrl\""))
        assertEquals(
            WidgetSourceFragmentParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidPresentationFields),
            WidgetCollectionListArtifact.parse(raw.replace("search_call", "invented"), algorithm),
        )
        assertEquals(
            WidgetSourceFragmentParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidPresentationFields),
            WidgetCollectionListArtifact.parse(
                raw.replace("\"items\":\"results\"", "\"items\":\"results[0]\""),
                algorithm,
            ),
        )
    }
}
