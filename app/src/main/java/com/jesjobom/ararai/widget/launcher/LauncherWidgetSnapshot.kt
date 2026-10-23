package com.jesjobom.ararai.widget.launcher

import com.jesjobom.ararai.knowledge.validWikipediaArticleUrl
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WidgetPresentationCache
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode

internal enum class LauncherWidgetState { Unconfigured, Unavailable, Empty, Ready, Disabled, Stale, NeedsAttention }

internal data class LauncherWidgetLink(val label: String, val url: String)

internal data class LauncherWidgetSnapshot(
    val widgetName: String,
    val headline: String,
    val body: String,
    val link: LauncherWidgetLink?,
    val state: LauncherWidgetState,
    val updatedAtMillis: Long? = null,
)

internal object LauncherWidgetSnapshotProjector {
    fun project(definition: ManagedWidgetDefinition?, cache: WidgetPresentationCache?): LauncherWidgetSnapshot {
        if (definition == null) return LauncherWidgetSnapshot("", "", "", null, LauncherWidgetState.Unavailable)
        val usableCache = cache?.takeIf { it.revision <= definition.activeRevision }
        val presentation = usableCache
            ?.let { runCatching { WidgetPresentationCodec.decodeCached(it.presentationJson) }.getOrNull() }
        val content = presentation?.let(::projectContent) ?: ProjectedContent("", "", null)
        val hasContent = content.headline.isNotBlank() || content.body.isNotBlank() || content.link != null
        val state = when {
            !definition.enabled -> LauncherWidgetState.Disabled
            definition.status == ManagedWidgetStatus.NeedsAttention -> LauncherWidgetState.NeedsAttention
            hasContent && (
                usableCache?.revision != definition.activeRevision ||
                    definition.lastAttemptAtMillis != definition.lastSuccessAtMillis
                ) -> LauncherWidgetState.Stale
            !hasContent -> LauncherWidgetState.Empty
            else -> LauncherWidgetState.Ready
        }
        return LauncherWidgetSnapshot(
            definition.displayName,
            content.headline,
            content.body,
            content.link,
            state,
            usableCache?.completedAtMillis,
        )
    }

    private fun projectContent(presentation: WidgetPresentationNode): ProjectedContent {
        val text = mutableListOf<String>()
        var firstLink: LauncherWidgetLink? = null
        fun visit(node: WidgetPresentationNode) {
            when (node) {
                is WidgetPresentationNode.Card -> visit(node.child)
                is WidgetPresentationNode.Column -> node.children.forEach(::visit)
                is WidgetPresentationNode.Row -> node.children.forEach(::visit)
                is WidgetPresentationNode.Text -> node.text.trim().takeIf(String::isNotEmpty)?.let(text::add)
                is WidgetPresentationNode.Value -> node.text.trim().takeIf(String::isNotEmpty)?.let(text::add)
                is WidgetPresentationNode.HttpsLink -> if (firstLink == null && validWikipediaArticleUrl(node.url)) {
                    firstLink = LauncherWidgetLink(node.label, node.url)
                }
                is WidgetPresentationNode.Icon -> Unit
            }
        }
        visit(presentation)
        return ProjectedContent(text.firstOrNull().orEmpty(), text.drop(1).joinToString("\n"), firstLink)
    }

    private data class ProjectedContent(val headline: String, val body: String, val link: LauncherWidgetLink?)
}
