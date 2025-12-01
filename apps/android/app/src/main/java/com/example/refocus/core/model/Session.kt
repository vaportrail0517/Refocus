package com.example.refocus.core.model

data class Session(
    val id: Long? = null,
    val packageName: String,
)

data class SessionEvent(
    val id: Long? = null,
    val sessionId: Long,
    val type: SessionEventType,
    val timestampMillis: Long,
)

enum class SessionEventType {
    Start,
    Pause,
    Resume,
    End,
}
