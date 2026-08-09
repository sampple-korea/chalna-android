package app.chalna.capture.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.animation.ValueAnimator
import android.os.Build
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.capture.CaptureRuntime
import java.util.UUID

class ChalnaVoiceInteractionSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    private var dispatchedSession: String? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val platformSessionId = if (Build.VERSION.SDK_INT >= 34) args?.getString(KEY_SHOW_SESSION_ID) else null
        val id = platformSessionId ?: "session-${UUID.randomUUID()}"
        if (dispatchedSession != id) {
            dispatchedSession = id
            CaptureRuntime.record("assistant_callback")
            CaptureService.dispatch(appContext, id)
        }
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        // Assist structure and screen content are intentionally ignored and never retained.
    }

    override fun onCreateContentView(): View = EdgePulseView(appContext) { finish() }

    private class EdgePulseView(context: Context, finished: () -> Unit) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 2f
        }
        private var alphaPhase = 0f
        private var edgeShader: Shader? = null
        private val edgeColors = intArrayOf(
            0xFF58D7FF.toInt(),
            0xFF5F8BFF.toInt(),
            0xFF896EFF.toInt(),
            0xFF58D7FF.toInt(),
        )

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 380
                addUpdateListener {
                    alphaPhase = it.animatedValue as Float
                    invalidate()
                }
                doOnEnd(finished)
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val fade = 1f - kotlin.math.abs(alphaPhase * 2f - 1f)
            paint.alpha = (fade * 190).toInt()
            paint.shader = edgeShader
            val inset = paint.strokeWidth
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 18f, 18f, paint)
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            edgeShader = SweepGradient(width / 2f, height / 2f, edgeColors, null)
        }
    }
}

private fun ValueAnimator.doOnEnd(block: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })
}
