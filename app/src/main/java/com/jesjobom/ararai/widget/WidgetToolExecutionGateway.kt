package com.jesjobom.ararai.widget

import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRejection

data class WidgetToolInvocation(
    val toolId: String,
    val contractVersion: Int,
    val argumentsJson: String,
)

sealed interface WidgetToolExecutionResult {
    data class Success(val payloadJson: String) : WidgetToolExecutionResult
    data class Failure(val reason: ApplicationToolRejection) : WidgetToolExecutionResult
}

class WidgetToolExecutionGateway(private val dispatcher: ApplicationToolDispatcher) {
    suspend fun execute(invocation: WidgetToolInvocation): WidgetToolExecutionResult = when (
        val result = dispatcher.execute(
            ApplicationToolInvocation(
                id = invocation.toolId,
                version = invocation.contractVersion,
                consumer = ApplicationToolConsumer.Widget,
                argumentsJson = invocation.argumentsJson,
            ),
        )
    ) {
        is ApplicationToolDispatchResult.Executed -> WidgetToolExecutionResult.Success(result.payloadJson)
        is ApplicationToolDispatchResult.Rejected -> WidgetToolExecutionResult.Failure(result.reason)
    }
}
