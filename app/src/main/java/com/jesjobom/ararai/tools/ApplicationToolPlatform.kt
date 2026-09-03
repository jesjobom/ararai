package com.jesjobom.ararai.tools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets

enum class ApplicationToolCategory { ExternalKnowledge, LocalCompute }

interface ApplicationTool<in Request, out Result> {
    val displayName: String
    val category: ApplicationToolCategory
    suspend fun execute(request: Request): Result
}

@Suppress("FunctionName")
fun <Request, Result> ApplicationTool(
    displayName: String,
    category: ApplicationToolCategory,
    execute: suspend (Request) -> Result,
): ApplicationTool<Request, Result> = object : ApplicationTool<Request, Result> {
    override val displayName = displayName
    override val category = category
    override suspend fun execute(request: Request): Result = execute.invoke(request)
}

enum class ApplicationToolConsumer { Model, Widget }

data class ApplicationToolContract(
    val id: String,
    val version: Int,
    val displayName: String,
    val category: ApplicationToolCategory,
    val consumers: Set<ApplicationToolConsumer>,
    val inputSchemaJson: String,
    val outputSchemaJson: String,
) {
    init {
        require(TOOL_ID_PATTERN.matches(id)) { "Invalid application tool id" }
        require(version > 0) { "Application tool version must be positive" }
        require(displayName.isNotBlank()) { "Application tool display name is required" }
        require(consumers.isNotEmpty()) { "Application tool must have an eligible consumer" }
        requireValidSchema(inputSchemaJson, "input")
        requireValidSchema(outputSchemaJson, "output")
    }

    private fun requireValidSchema(raw: String, label: String) {
        require(raw.utf8Size() <= MAX_SCHEMA_BYTES) { "Application tool $label schema is too large" }
        require(
            runCatching { JsonParser.parseString(raw) }
                .getOrNull()
                ?.isJsonObject == true,
        ) { "Application tool $label schema must be a JSON object" }
    }

    private companion object {
        val TOOL_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
        const val MAX_SCHEMA_BYTES = 16_384
    }
}

data class ApplicationToolOperationalState(
    val enabled: Boolean,
    val ready: Boolean,
)

data class ApplicationToolExecutionPolicy(
    val timeoutMillis: Long = 30_000L,
    val maxArgumentsBytes: Int = 16_384,
    val maxResultBytes: Int = 65_536,
) {
    init {
        require(timeoutMillis > 0) { "Application tool timeout must be positive" }
        require(maxArgumentsBytes > 0) { "Application tool argument limit must be positive" }
        require(maxResultBytes > 0) { "Application tool result limit must be positive" }
    }
}

data class ApplicationToolInvocation(
    val id: String,
    val version: Int,
    val consumer: ApplicationToolConsumer,
    val argumentsJson: String,
    val verifiedModelToolIds: Set<String> = emptySet(),
)

enum class ApplicationToolRejection {
    UnknownTool,
    UnsupportedVersion,
    Disabled,
    NotConfigured,
    IneligibleConsumer,
    UnsupportedModel,
    InvalidArguments,
    TimedOut,
    Cancelled,
    Unavailable,
}

sealed interface ApplicationToolDispatchResult {
    data class Executed(
        val payloadJson: String,
        internal val domainResult: Any,
    ) : ApplicationToolDispatchResult

    data class Rejected(val reason: ApplicationToolRejection) : ApplicationToolDispatchResult
}

class RegisteredApplicationTool internal constructor(
    val contract: ApplicationToolContract,
    val policy: ApplicationToolExecutionPolicy,
    private val stateProvider: () -> ApplicationToolOperationalState,
    private val invoke: suspend (JsonObject) -> ApplicationToolDispatchResult.Executed,
) {
    fun operationalState(): ApplicationToolOperationalState = stateProvider()

    internal suspend fun execute(arguments: JsonObject): ApplicationToolDispatchResult.Executed = invoke(arguments)
}

fun <Request : Any, Result : Any> applicationToolBinding(
    contract: ApplicationToolContract,
    state: () -> ApplicationToolOperationalState,
    executor: ApplicationTool<Request, Result>,
    decodeArguments: (JsonObject) -> Request?,
    encodeResult: (Result) -> String,
    policy: ApplicationToolExecutionPolicy = ApplicationToolExecutionPolicy(),
): RegisteredApplicationTool {
    require(executor.displayName == contract.displayName) { "Tool display metadata is inconsistent" }
    require(executor.category == contract.category) { "Tool category metadata is inconsistent" }
    return RegisteredApplicationTool(contract, policy, state) { arguments ->
        val request = decodeArguments(arguments) ?: throw InvalidApplicationToolArgumentsException()
        val result = executor.execute(request)
        ApplicationToolDispatchResult.Executed(
            payloadJson = encodeResult(result),
            domainResult = result,
        )
    }
}

class ApplicationToolRegistry(bindings: Collection<RegisteredApplicationTool>) {
    private val bindingsById: Map<String, Map<Int, RegisteredApplicationTool>>

    init {
        val duplicates = bindings
            .groupingBy { it.contract.id to it.contract.version }
            .eachCount()
            .filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate application tool registration" }
        bindingsById = bindings
            .groupBy { it.contract.id }
            .mapValues { (_, versions) -> versions.associateBy { it.contract.version } }
    }

    fun descriptors(): List<ApplicationToolContract> = bindingsById
        .values
        .flatMap { it.values }
        .map { it.contract }
        .sortedWith(compareBy(ApplicationToolContract::id, ApplicationToolContract::version))

    fun availableToolIds(consumer: ApplicationToolConsumer): Set<String> = bindingsById
        .values
        .flatMap { it.values }
        .filter { binding ->
            consumer in binding.contract.consumers &&
                binding.operationalState().let { it.enabled && it.ready }
        }
        .mapTo(sortedSetOf()) { it.contract.id }

    internal fun versions(id: String): Map<Int, RegisteredApplicationTool>? = bindingsById[id]
}

class ApplicationToolDispatcher(private val registry: ApplicationToolRegistry) {
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    suspend fun execute(invocation: ApplicationToolInvocation): ApplicationToolDispatchResult {
        val prepared = prepare(invocation)
        if (prepared is PreparedInvocation.Rejected) return prepared.result
        prepared as PreparedInvocation.Ready
        return executePrepared(prepared)
    }

    @Suppress("ReturnCount")
    private fun prepare(invocation: ApplicationToolInvocation): PreparedInvocation {
        val versions = registry.versions(invocation.id)
            ?: return PreparedInvocation.rejected(ApplicationToolRejection.UnknownTool)
        val binding = versions[invocation.version]
            ?: return PreparedInvocation.rejected(ApplicationToolRejection.UnsupportedVersion)
        if (invocation.consumer !in binding.contract.consumers) {
            return PreparedInvocation.rejected(ApplicationToolRejection.IneligibleConsumer)
        }
        if (
            invocation.consumer == ApplicationToolConsumer.Model &&
            invocation.id !in invocation.verifiedModelToolIds
        ) {
            return PreparedInvocation.rejected(ApplicationToolRejection.UnsupportedModel)
        }
        val operationalState = binding.operationalState()
        if (!operationalState.enabled) {
            return PreparedInvocation.rejected(ApplicationToolRejection.Disabled)
        }
        if (!operationalState.ready) {
            return PreparedInvocation.rejected(ApplicationToolRejection.NotConfigured)
        }
        if (invocation.argumentsJson.utf8Size() > binding.policy.maxArgumentsBytes) {
            return PreparedInvocation.rejected(ApplicationToolRejection.InvalidArguments)
        }
        val arguments = runCatching { JsonParser.parseString(invocation.argumentsJson) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return PreparedInvocation.rejected(ApplicationToolRejection.InvalidArguments)
        return PreparedInvocation.Ready(binding, arguments)
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun executePrepared(prepared: PreparedInvocation.Ready): ApplicationToolDispatchResult = try {
        val result = withTimeoutOrNull(prepared.binding.policy.timeoutMillis) {
            prepared.binding.execute(prepared.arguments)
        } ?: return ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.TimedOut)
        if (!result.isValid(prepared.binding.policy)) {
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Unavailable)
        } else {
            result
        }
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()
        ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Cancelled)
    } catch (_: InvalidApplicationToolArgumentsException) {
        ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.InvalidArguments)
    } catch (_: RuntimeException) {
        ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Unavailable)
    }

    private fun ApplicationToolDispatchResult.Executed.isValid(
        policy: ApplicationToolExecutionPolicy,
    ): Boolean = payloadJson.utf8Size() <= policy.maxResultBytes &&
        runCatching { JsonParser.parseString(payloadJson) }.getOrNull()?.isJsonObject == true

    private sealed interface PreparedInvocation {
        data class Ready(
            val binding: RegisteredApplicationTool,
            val arguments: JsonObject,
        ) : PreparedInvocation

        data class Rejected(val result: ApplicationToolDispatchResult.Rejected) : PreparedInvocation

        companion object {
            fun rejected(reason: ApplicationToolRejection) = Rejected(ApplicationToolDispatchResult.Rejected(reason))
        }
    }
}

private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

private class InvalidApplicationToolArgumentsException : IllegalArgumentException()
