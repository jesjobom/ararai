package com.jesjobom.ararai.reporting

import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.chat.StoredChatMessage
import java.util.UUID

const val REPORT_SCHEMA_VERSION = 1
const val MAX_REPORT_COMMENT_LENGTH = 500
const val MAX_REPORT_TEXT_LENGTH = 8_000
const val MAX_REPORT_CONTEXT_ITEMS = 4

enum class ReportReason {
    HateOrHarassment,
    SexualContent,
    ViolenceOrSelfHarm,
    DangerousOrIllegal,
    Privacy,
    FalseOrMisleading,
    Other,
}

data class ReportTechnicalMetadata(
    val appVersion: String,
    val localeTag: String,
    val modelId: String,
    val runtime: String,
)

data class ReportMediaPresence(
    val image: Boolean,
    val audio: Boolean,
    val transcript: Boolean,
)

data class ReportContextItem(
    val messageId: String,
    val role: ChatRole,
    val text: String,
)

data class GeneratedContentReportDraft(
    val reportId: String,
    val reportedMessageId: String,
    val reportedResponse: String,
    val availableContext: List<ReportContextItem>,
    val initiallySelectedContextIds: Set<String>,
    val mediaPresence: ReportMediaPresence,
    val metadata: ReportTechnicalMetadata,
)

data class GeneratedContentReportPayload(
    val schemaVersion: Int,
    val reportId: String,
    val reportedResponse: String,
    val reason: ReportReason,
    val comment: String?,
    val context: List<ReportContextItem>,
    val mediaPresence: ReportMediaPresence,
    val metadata: ReportTechnicalMetadata,
    val reportedAtEpochMillis: Long,
)

class InvalidReportDraftException(message: String) : IllegalArgumentException(message)

class GeneratedContentReportProjector(
    private val newReportId: () -> String = { UUID.randomUUID().toString() },
) {
    fun isReportable(message: StoredChatMessage): Boolean {
        val content = message.content as? MessageContent.TextPrompt ?: return false
        return message.role == ChatRole.Assistant &&
            content.completionStatus == AssistantCompletionStatus.Complete &&
            content.text.isNotBlank()
    }

    fun createDraft(
        messages: List<StoredChatMessage>,
        reportedMessageId: String,
        metadata: ReportTechnicalMetadata,
    ): GeneratedContentReportDraft {
        val reportedIndex = messages.indexOfFirst { it.id == reportedMessageId }
        if (reportedIndex < 0 || !isReportable(messages[reportedIndex])) {
            throw InvalidReportDraftException("The selected response is not reportable")
        }
        val reported = messages[reportedIndex]
        val contextCandidates = messages.subList(0, reportedIndex)
            .asReversed()
            .mapNotNull(::toContextItem)
            .take(MAX_REPORT_CONTEXT_ITEMS)
            .reversed()
        val immediatelyPrecedingUser = contextCandidates.lastOrNull { it.role == ChatRole.User }
        val precedingAssistantIndex = messages.subList(0, reportedIndex)
            .indexOfLast { it.role == ChatRole.Assistant }
        val turnMessages = messages.subList(precedingAssistantIndex + 1, reportedIndex + 1)
        return GeneratedContentReportDraft(
            reportId = newReportId(),
            reportedMessageId = reported.id,
            reportedResponse = reported.content.displayTextForReport(),
            availableContext = contextCandidates,
            initiallySelectedContextIds = setOfNotNull(immediatelyPrecedingUser?.messageId),
            mediaPresence = ReportMediaPresence(
                image = turnMessages.any { it.content.hasImageForReport() },
                audio = turnMessages.any { it.content is MessageContent.AudioPromptContent },
                transcript = turnMessages.any { it.content.hasTranscriptForReport() },
            ),
            metadata = metadata,
        )
    }

    fun buildPayload(
        draft: GeneratedContentReportDraft,
        reason: ReportReason,
        comment: String?,
        selectedContextIds: Set<String>,
        reportedAtEpochMillis: Long,
    ): GeneratedContentReportPayload {
        val cleanComment = comment?.trim()?.takeIf(String::isNotEmpty)
        require(cleanComment == null || cleanComment.length <= MAX_REPORT_COMMENT_LENGTH)
        val availableById = draft.availableContext.associateBy { it.messageId }
        require(selectedContextIds.all(availableById::containsKey))
        val selected = draft.availableContext.filter { it.messageId in selectedContextIds }
        require(selected.size <= MAX_REPORT_CONTEXT_ITEMS)
        return GeneratedContentReportPayload(
            schemaVersion = REPORT_SCHEMA_VERSION,
            reportId = draft.reportId,
            reportedResponse = draft.reportedResponse,
            reason = reason,
            comment = cleanComment,
            context = selected,
            mediaPresence = draft.mediaPresence,
            metadata = draft.metadata,
            reportedAtEpochMillis = reportedAtEpochMillis,
        )
    }

    private fun toContextItem(message: StoredChatMessage): ReportContextItem? {
        val text = message.content.displayTextForReport().trim()
        if (text.isEmpty()) return null
        return ReportContextItem(message.id, message.role, text.take(MAX_REPORT_TEXT_LENGTH))
    }
}

private fun MessageContent.displayTextForReport(): String = when (this) {
    is MessageContent.TextPrompt -> text.trim().take(MAX_REPORT_TEXT_LENGTH)
    is MessageContent.AudioPromptContent -> transcript.orEmpty().trim().take(MAX_REPORT_TEXT_LENGTH)
}

private fun MessageContent.hasImageForReport(): Boolean = when (this) {
    is MessageContent.TextPrompt -> imageAttachments.isNotEmpty()
    is MessageContent.AudioPromptContent -> imageAttachments.isNotEmpty()
}

private fun MessageContent.hasTranscriptForReport(): Boolean = when (this) {
    is MessageContent.AudioPromptContent -> !transcript.isNullOrBlank()
    is MessageContent.TextPrompt -> false
}
