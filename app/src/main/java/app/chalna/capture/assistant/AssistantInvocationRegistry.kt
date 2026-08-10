package app.chalna.capture.assistant

import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import app.chalna.capture.domain.CaptureCommandResult
import java.util.LinkedHashMap
import java.util.UUID

data class AssistantInvocation(
    val id: String,
    val result: CaptureCommandResult,
)

object AssistantInvocationRegistry {
    private val entries = LinkedHashMap<String, AssistantInvocation>()

    @Synchronized
    fun put(
        sessionKey: String,
        invocation: AssistantInvocation,
    ) {
        entries[sessionKey] = invocation
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun take(sessionKey: String): AssistantInvocation? = entries.remove(sessionKey)

    @Synchronized
    fun peek(sessionKey: String): AssistantInvocation? = entries[sessionKey]

    @Synchronized
    fun discard(sessionKey: String) {
        entries.remove(sessionKey)
    }

    fun sessionKey(args: Bundle?): String? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                args
                    ?.getInt(VoiceInteractionSession.KEY_SHOW_SESSION_ID, MISSING_SESSION_ID)
                    ?.takeIf { it != MISSING_SESSION_ID }
                    ?.let { "show-$it" }
            else ->
                args
                    ?.getLong(EXTRA_INVOCATION_TIME, Long.MIN_VALUE)
                    ?.takeIf { it != Long.MIN_VALUE }
                    ?.let { "time-$it" }
        }

    fun invocationId(args: Bundle?): String {
        val key = sessionKey(args)
        return if (key != null) "assistant-$key" else "anonymous-${UUID.randomUUID()}"
    }

    private const val MAX_ENTRIES = 32
    private const val MISSING_SESSION_ID = Int.MIN_VALUE
    private const val EXTRA_INVOCATION_TIME = "android.intent.extra.TIME"
}
