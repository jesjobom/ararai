package com.jesjobom.ararai.widget.managed

internal enum class WidgetAuthoringNotificationAction { Review, Continue, Retry, Cancel }

internal data class WidgetAuthoringNotificationState(
    val ongoing: Boolean,
    val status: WidgetAuthoringSessionStatus,
    val stage: WidgetAuthoringStageKey,
    val actions: Set<WidgetAuthoringNotificationAction>,
)

internal object WidgetAuthoringNotificationProjector {
    fun project(
        snapshot: WidgetAuthoringSessionSnapshot,
    ): WidgetAuthoringNotificationState = WidgetAuthoringNotificationState(
        ongoing = snapshot.session.status in ACTIVE_SESSION_STATUSES,
        status = snapshot.session.status,
        stage = snapshot.session.currentStage,
        actions = when (snapshot.session.status) {
            WidgetAuthoringSessionStatus.AttemptQueued,
            WidgetAuthoringSessionStatus.AttemptDeferred,
            WidgetAuthoringSessionStatus.AttemptRunning,
            -> setOf(WidgetAuthoringNotificationAction.Cancel)
            WidgetAuthoringSessionStatus.AwaitingReview -> setOf(
                WidgetAuthoringNotificationAction.Review,
                WidgetAuthoringNotificationAction.Continue,
            )
            WidgetAuthoringSessionStatus.AwaitingRetry -> setOf(
                WidgetAuthoringNotificationAction.Review,
                WidgetAuthoringNotificationAction.Retry,
            )
            WidgetAuthoringSessionStatus.AwaitingStage,
            WidgetAuthoringSessionStatus.ReadyForPreview,
            -> setOf(WidgetAuthoringNotificationAction.Review)
            WidgetAuthoringSessionStatus.Completed -> emptySet()
        },
    )

    private val ACTIVE_SESSION_STATUSES = setOf(
        WidgetAuthoringSessionStatus.AttemptQueued,
        WidgetAuthoringSessionStatus.AttemptDeferred,
        WidgetAuthoringSessionStatus.AttemptRunning,
    )
}
