package com.jesjobom.ararai.widget.launcher

import com.jesjobom.ararai.knowledge.validWidgetExternalUrl
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WEATHER_ATTRIBUTION_URL
import com.jesjobom.ararai.widget.managed.WidgetPresentationCache
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode

internal enum class LauncherWidgetState { Unconfigured, Unavailable, Empty, Ready, Disabled, Stale, NeedsAttention }

internal data class LauncherWidgetLink(val label: String, val url: String)

internal sealed interface LauncherWidgetRow {
    data class Text(val value: String) : LauncherWidgetRow
    data class Link(val value: LauncherWidgetLink) : LauncherWidgetRow
}

internal data class LauncherWidgetSnapshot(
    val widgetName: String,
    val headline: String,
    val body: String,
    val link: LauncherWidgetLink?,
    val state: LauncherWidgetState,
    val updatedAtMillis: Long? = null,
    val rows: List<LauncherWidgetRow> = emptyList(),
)

internal object LauncherWidgetSnapshotProjector {
    fun project(
        definition: ManagedWidgetDefinition?,
        cache: WidgetPresentationCache?,
        weatherAttribution: Boolean = false,
    ): LauncherWidgetSnapshot {
        if (definition == null) return LauncherWidgetSnapshot("", "", "", null, LauncherWidgetState.Unavailable)
        val usableCache = cache?.takeIf { it.revision <= definition.activeRevision }
        val presentation = usableCache
            ?.let { runCatching { WidgetPresentationCodec.decodeCached(it.presentationJson) }.getOrNull() }
        val content = presentation?.let(::projectContent) ?: ProjectedContent("", "", null, emptyList())
        val hasContent = content.headline.isNotBlank() || content.body.isNotBlank() || content.link != null
        val state = projectState(definition, usableCache, hasContent)
        val attribution = if (hasContent && weatherAttribution) {
            LauncherWidgetLink("Open-Meteo.com · CC BY 4.0", WEATHER_ATTRIBUTION_URL)
        } else {
            null
        }
        return LauncherWidgetSnapshot(
            definition.displayName,
            content.headline,
            content.body,
            content.link ?: attribution,
            state,
            usableCache?.completedAtMillis,
            content.rows + listOfNotNull(attribution?.let(LauncherWidgetRow::Link)),
        )
    }

    private fun projectState(
        definition: ManagedWidgetDefinition,
        usableCache: WidgetPresentationCache?,
        hasContent: Boolean,
    ): LauncherWidgetState = when {
        !definition.enabled -> LauncherWidgetState.Disabled
        definition.status == ManagedWidgetStatus.NeedsAttention -> LauncherWidgetState.NeedsAttention
        hasContent && (
            usableCache?.revision != definition.activeRevision ||
                definition.lastAttemptAtMillis != definition.lastSuccessAtMillis
            ) -> LauncherWidgetState.Stale
        !hasContent -> LauncherWidgetState.Empty
        else -> LauncherWidgetState.Ready
    }

    private fun projectContent(presentation: WidgetPresentationNode): ProjectedContent {
        val rows = mutableListOf<LauncherWidgetRow>()
        var linkCount = 0
        for (node in presentation.leaves()) {
            when (node) {
                is WidgetPresentationNode.Text -> node.text.trim().takeIf(String::isNotEmpty)?.let {
                    rows.add(LauncherWidgetRow.Text(it))
                }
                is WidgetPresentationNode.Value -> node.text.trim().takeIf(String::isNotEmpty)?.let {
                    rows.add(LauncherWidgetRow.Text(it))
                }
                is WidgetPresentationNode.HttpsLink -> {
                    if (linkCount < MAX_LAUNCHER_LINKS && validWidgetExternalUrl(node.url)) {
                        node.label.trim().takeIf(String::isNotEmpty)?.let { label ->
                            rows.add(LauncherWidgetRow.Link(LauncherWidgetLink(label, node.url)))
                            linkCount++
                        }
                    }
                }
                else -> Unit
            }
        }
        val text = rows.filterIsInstance<LauncherWidgetRow.Text>().map(LauncherWidgetRow.Text::value)
        val links = rows.filterIsInstance<LauncherWidgetRow.Link>().map(LauncherWidgetRow.Link::value)
        val linkLabels = links.map(LauncherWidgetLink::label)
        val visibleText = text.ifEmpty { linkLabels }
        return ProjectedContent(
            visibleText.firstOrNull().orEmpty(),
            visibleText.drop(1).joinToString("\n"),
            links.firstOrNull(),
            rows,
        )
    }

    private fun WidgetPresentationNode.leaves(): Sequence<WidgetPresentationNode> = sequence {
        when (this@leaves) {
            is WidgetPresentationNode.Card -> yieldAll(child.leaves())
            is WidgetPresentationNode.Column -> children.forEach { yieldAll(it.leaves()) }
            is WidgetPresentationNode.Row -> children.forEach { yieldAll(it.leaves()) }
            else -> yield(this@leaves)
        }
    }

    private data class ProjectedContent(
        val headline: String,
        val body: String,
        val link: LauncherWidgetLink?,
        val rows: List<LauncherWidgetRow>,
    )

    private const val MAX_LAUNCHER_LINKS = 10
}
