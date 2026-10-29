package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability

/** New drafts use current contracts; an edit may also use versions referenced by its saved revision. */
internal fun Collection<ApplicationToolContract>.authoringWidgetContracts(
    existingWidgetTools: Set<WidgetToolCapability> = emptySet(),
): List<ApplicationToolContract> = filter { ApplicationToolConsumer.Widget in it.consumers }
    .groupBy(ApplicationToolContract::id)
    .values
    .flatMap { versions ->
        val latest = versions.maxBy(ApplicationToolContract::version)
        versions.filter { it == latest || WidgetToolCapability(it.id, it.version) in existingWidgetTools }
    }

internal fun ApplicationToolRegistry.authoringToolCapabilities(
    existingWidgetTools: Set<WidgetToolCapability> = emptySet(),
): Set<WidgetToolCapability> = descriptors()
    .authoringWidgetContracts(existingWidgetTools)
    .mapTo(mutableSetOf()) { WidgetToolCapability(it.id, it.version) }
