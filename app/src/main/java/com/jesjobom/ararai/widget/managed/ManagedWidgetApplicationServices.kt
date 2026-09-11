package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.tools.ApplicationToolRegistry

internal data class ManagedWidgetApplicationServices(
    val repository: ManagedWidgetRepository,
    val schedules: ManagedWidgetScheduleController,
    val manualRefresh: ManagedWidgetManualRefresh,
    val toolRegistry: ApplicationToolRegistry,
)
