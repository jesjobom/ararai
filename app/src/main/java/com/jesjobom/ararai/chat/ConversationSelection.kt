package com.jesjobom.ararai.chat

class ConversationSelection(
    initialSessionId: String? = null,
) {
    @Volatile
    var currentSessionId: String? = initialSessionId
        private set

    fun select(sessionId: String) {
        currentSessionId = sessionId
    }

    fun clear(sessionId: String? = null) {
        if (sessionId == null || currentSessionId == sessionId) {
            currentSessionId = null
        }
    }
}
