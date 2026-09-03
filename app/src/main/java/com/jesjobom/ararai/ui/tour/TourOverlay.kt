package com.jesjobom.ararai.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class TourStep(
    val id: String,
    val anchorId: String,
    val title: String,
    val body: String,
    val targetDescription: String,
)

internal class TourAnchorRegistry internal constructor() {
    internal val anchors = mutableStateMapOf<String, TourAnchor>()
}

internal data class TourAnchor(
    val boundsInWindow: Rect,
    val bringIntoViewRequester: BringIntoViewRequester,
)

@Composable
internal fun rememberTourAnchorRegistry(): TourAnchorRegistry = remember { TourAnchorRegistry() }

@Composable
internal fun Modifier.tourAnchor(
    registry: TourAnchorRegistry,
    anchorId: String,
): Modifier {
    val requester = remember(anchorId) { BringIntoViewRequester() }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            registry.anchors[anchorId] = TourAnchor(coordinates.boundsInWindow(), requester)
        }
}

@Composable
@Suppress("LongMethod")
internal fun TourOverlay(
    tour: ScreenTour,
    store: TourPreferenceStore,
    steps: List<TourStep>,
    anchors: TourAnchorRegistry,
    progressText: @Composable (current: Int, total: Int) -> String,
    previousLabel: String,
    nextLabel: String,
    completeLabel: String,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    val revision by store.revision.collectAsState()
    val stepKey = steps.joinToString(separator = "|") { it.id }
    var currentIndex by remember(tour, stepKey, revision) { mutableIntStateOf(0) }
    var visible by remember(tour, revision) { mutableStateOf(!store.isTerminal(tour)) }
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(revision, tour) {
        visible = !store.isTerminal(tour)
    }
    if (!visible || steps.isEmpty()) return

    val safeIndex = currentIndex.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val anchor = anchors.anchors[step.anchorId] ?: return

    LaunchedEffect(step.id, anchor.bringIntoViewRequester) {
        anchor.bringIntoViewRequester.bringIntoView()
    }

    fun finish() {
        store.markTerminal(tour)
        visible = false
    }

    BackHandler(enabled = visible, onBack = ::finish)

    val overlayRect = overlayBounds
    val localTarget = overlayRect?.let {
        Rect(
            left = anchor.boundsInWindow.left - it.left,
            top = anchor.boundsInWindow.top - it.top,
            right = anchor.boundsInWindow.right - it.left,
            bottom = anchor.boundsInWindow.bottom - it.top,
        )
    }
    val showCardAtTop =
        localTarget?.center?.y?.let { center ->
            center > requireNotNull(overlayRect).height / 2f
        } == true
    val scrim = Color.Black.copy(alpha = 0.68f)
    val highlight = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = ::finish,
        properties =
        DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier =
            modifier
                .fillMaxSize()
                .testTag("tour-overlay-${tour.storageId}")
                .onGloballyPositioned { overlayBounds = it.boundsInWindow() },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
            localTarget?.let { target ->
                val padding = 8.dp
                Canvas(Modifier.fillMaxSize()) {
                    val padded =
                        Rect(
                            left = (target.left - padding.toPx()).coerceAtLeast(0f),
                            top = (target.top - padding.toPx()).coerceAtLeast(0f),
                            right = (target.right + padding.toPx()).coerceAtMost(size.width),
                            bottom = (target.bottom + padding.toPx()).coerceAtMost(size.height),
                        )
                    drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, padded.top))
                    drawRect(
                        scrim,
                        topLeft = Offset(0f, padded.bottom),
                        size = Size(size.width, (size.height - padded.bottom).coerceAtLeast(0f)),
                    )
                    drawRect(
                        scrim,
                        topLeft = Offset(0f, padded.top),
                        size = Size(padded.left, padded.height),
                    )
                    drawRect(
                        scrim,
                        topLeft = Offset(padded.right, padded.top),
                        size = Size((size.width - padded.right).coerceAtLeast(0f), padded.height),
                    )
                    drawRoundRect(
                        color = highlight,
                        topLeft = padded.topLeft,
                        size = padded.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Card(
                modifier =
                Modifier
                    .align(if (showCardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 32.dp)
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .semantics { paneTitle = step.title }
                    .testTag("tour-step-${step.id}"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = progressText(safeIndex + 1, steps.size),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = ::finish) {
                            Icon(Icons.Filled.Close, contentDescription = closeDescription)
                        }
                    }
                    Column(
                        modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .testTag("tour-scroll-content"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(step.body, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            step.targetDescription,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (safeIndex > 0) {
                            TextButton(onClick = { currentIndex = safeIndex - 1 }) { Text(previousLabel) }
                        }
                        TextButton(
                            onClick = {
                                if (safeIndex == steps.lastIndex) finish() else currentIndex = safeIndex + 1
                            },
                        ) {
                            Text(if (safeIndex == steps.lastIndex) completeLabel else nextLabel)
                        }
                    }
                }
            }
        }
    }
}
