package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.knowledge.validWikipediaArticleUrl
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode

internal fun decodeManagedWidgetPresentationOrNull(raw: String): WidgetPresentationNode? = runCatching {
    WidgetPresentationCodec.decodeCached(raw)
}.getOrNull()

internal fun allowedManagedWidgetArticleUrl(url: String): String? = url.takeIf(::validWikipediaArticleUrl)

@Composable
internal fun ManagedWidgetPresentation(
    presentation: WidgetPresentationNode,
    onOpenWikipediaArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WidgetPresentationContent(
        node = presentation,
        onOpenWikipediaArticle = onOpenWikipediaArticle,
        modifier = modifier,
    )
}

@Composable
private fun WidgetPresentationContent(
    node: WidgetPresentationNode,
    onOpenWikipediaArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is WidgetPresentationNode.Card -> Card(modifier = modifier) {
            WidgetPresentationContent(
                node = node.child,
                onOpenWikipediaArticle = onOpenWikipediaArticle,
                modifier = Modifier.padding(16.dp),
            )
        }
        is WidgetPresentationNode.Column -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.children.forEach { child ->
                WidgetPresentationContent(child, onOpenWikipediaArticle)
            }
        }
        is WidgetPresentationNode.Row -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                WidgetPresentationContent(child, onOpenWikipediaArticle)
            }
        }
        is WidgetPresentationNode.Text -> Text(
            text = node.text,
            color = node.tone.presentationColor(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        is WidgetPresentationNode.Value -> Text(
            text = node.text,
            color = node.tone.presentationColor(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier,
        )
        is WidgetPresentationNode.Icon -> Icon(
            imageVector = node.name.presentationIcon(),
            contentDescription = stringResource(node.name.presentationIconDescription()),
            modifier = modifier,
        )
        is WidgetPresentationNode.HttpsLink -> {
            val allowedUrl = allowedManagedWidgetArticleUrl(node.url)
            if (allowedUrl != null) {
                TextButton(
                    onClick = { onOpenWikipediaArticle(allowedUrl) },
                    modifier = modifier,
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Text(node.label)
                }
            }
        }
    }
}

@Composable
private fun String.presentationColor(): Color = when (this) {
    "muted" -> MaterialTheme.colorScheme.onSurfaceVariant
    "positive" -> MaterialTheme.colorScheme.primary
    "warning" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun String.presentationIcon(): ImageVector = when (this) {
    "calendar" -> Icons.Filled.CalendarMonth
    "clock" -> Icons.Filled.Schedule
    "link" -> Icons.Filled.Link
    "location" -> Icons.Filled.LocationOn
    "warning" -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}

private fun String.presentationIconDescription(): Int = when (this) {
    "calendar" -> R.string.widget_link_calendar
    "clock" -> R.string.widget_link_time
    "link" -> R.string.widget_link_link
    "location" -> R.string.widget_link_location
    "warning" -> R.string.widget_link_warning
    else -> R.string.widget_link_information
}
