package com.jesjobom.ararai.knowledge

fun redactedProviderError(
    error: Throwable,
    secrets: Collection<String> = emptyList(),
): String {
    var message = error.message.orEmpty().ifBlank { "Provider request failed" }
    secrets
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { secret -> message = message.replace(secret, REDACTED) }
    message = AUTHORIZATION_PATTERN.replace(message) { match ->
        "${match.groupValues[1]}$REDACTED"
    }
    message = API_KEY_PATTERN.replace(message) { match ->
        "${match.groupValues[1]}$REDACTED"
    }
    return message.take(MAX_DIAGNOSTIC_LENGTH)
}

private const val REDACTED = "[REDACTED]"
private const val MAX_DIAGNOSTIC_LENGTH = 300
private val AUTHORIZATION_PATTERN =
    Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+")
private val API_KEY_PATTERN =
    Regex("(?i)((?:x-api-key|api[_-]?key)\\s*[:=]\\s*)[^\\s,;]+")
