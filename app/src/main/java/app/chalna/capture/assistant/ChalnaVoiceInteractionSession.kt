package app.chalna.capture.assistant

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.RoundedCorner
import android.view.animation.PathInterpolator
import app.chalna.capture.capture.CaptureRuntime
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.domain.CaptureState
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChalnaVoiceInteractionSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dispatchedSession: String? = null
    private var pulseView: ChalnaInvocationGlowView? = null
    private var pendingPulse: InvocationPulseKind? = null
    private var lastAnonymousShowAtMillis = Long.MIN_VALUE

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val platformSessionId = if (Build.VERSION.SDK_INT >= 34) args?.getString(KEY_SHOW_SESSION_ID) else null
        val now = SystemClock.elapsedRealtime()
        if (platformSessionId == null && lastAnonymousShowAtMillis != Long.MIN_VALUE &&
            now - lastAnonymousShowAtMillis < ANONYMOUS_DUPLICATE_WINDOW_MILLIS
        ) return
        if (platformSessionId == null) lastAnonymousShowAtMillis = now
        val id = platformSessionId ?: "session-${UUID.randomUUID()}"
        if (dispatchedSession == id) return
        dispatchedSession = id

        val requestedPulse = when (CaptureRuntime.state.value) {
            is CaptureState.Recording, is CaptureState.Starting,
            is CaptureState.Stopping, is CaptureState.Saving -> InvocationPulseKind.STOP
            else -> InvocationPulseKind.START
        }
        CaptureTelemetryRegistry.mark("assistant_callback")
        val accepted = CaptureService.dispatch(appContext, id)
        pendingPulse = if (accepted) requestedPulse else InvocationPulseKind.ERROR
        pulseView?.activate(requireNotNull(pendingPulse))
    }

    override fun onCreateContentView(): View = ChalnaInvocationGlowView(appContext) { finish() }.also { view ->
        pulseView = view
        pendingPulse?.let(view::activate)
        sessionScope.launch {
            CaptureRuntime.state.collectLatest { state ->
                when (state) {
                    is CaptureState.Recording -> view.resolve(InvocationPulseKind.START)
                    is CaptureState.Failed -> view.resolve(InvocationPulseKind.ERROR)
                    is CaptureState.Saving, is CaptureState.Saved -> view.resolve(InvocationPulseKind.STOP)
                    else -> Unit
                }
            }
        }
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        // Assist structure, screen content, and foreground-app context are deliberately ignored.
    }

    override fun onDestroy() {
        pulseView?.release()
        pulseView = null
        sessionScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ANONYMOUS_DUPLICATE_WINDOW_MILLIS = 320L
    }

}

internal enum class InvocationPulseKind { START, STOP, ERROR }

/**
 * Transient three-layer perimeter illumination. Geometry, shaders, matrices, and hotspot buffers
 * are cached per size; animation only mutates scalar phase/alpha values.
 */
internal class ChalnaInvocationGlowView(context: Context, private val finished: () -> Unit) : View(context) {
        constructor(context: Context) : this(context, {})

        private val density = resources.displayMetrics.density
        private val edgePath = Path()
        private val edgeBounds = RectF()
        private val pathMeasure = PathMeasure()
        private val shaderMatrix = Matrix()
        private val hotspotPosition = FloatArray(2)
        private val hotspotTangent = FloatArray(2)
        private val atmospherePaint = strokePaint(22f, 22)
        private val bloomPaint = strokePaint(10f, 72)
        private val corePaint = strokePaint(1.35f, 235)
        private val hotspotBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hotspotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var animator: ValueAnimator? = null
        private var atmosphereShader: SweepGradient? = null
        private var bloomShader: SweepGradient? = null
        private var coreShader: SweepGradient? = null
        private var progress = 0f
        private var resolveBoost = 0f
        private var kind = InvocationPulseKind.START
        private var measuredLength = 0f
        private var completionSent = false
        private var previewMode = false

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setLayerType(LAYER_TYPE_HARDWARE, null)
            if (Build.VERSION.SDK_INT >= 31) {
                atmospherePaint.setRenderEffect(
                    RenderEffect.createBlurEffect(density * 13f, density * 13f, Shader.TileMode.CLAMP),
                )
                bloomPaint.setRenderEffect(
                    RenderEffect.createBlurEffect(density * 4.5f, density * 4.5f, Shader.TileMode.CLAMP),
                )
                hotspotBloomPaint.setRenderEffect(
                    RenderEffect.createBlurEffect(density * 6f, density * 6f, Shader.TileMode.CLAMP),
                )
            }
        }

        fun activate(requestedKind: InvocationPulseKind) {
            kind = requestedKind
            completionSent = false
            resolveBoost = 0f
            previewMode = false
            installShaders(requestedKind)
            animator?.removeAllListeners()
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = when (requestedKind) {
                    InvocationPulseKind.START -> 650L
                    InvocationPulseKind.STOP -> 570L
                    InvocationPulseKind.ERROR -> 460L
                }
                interpolator = PathInterpolator(0.18f, 0.78f, 0.22f, 1f)
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    resolveBoost *= 0.88f
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = completeOnce()
                })
                start()
            }
        }

        fun resolve(resolvedKind: InvocationPulseKind) {
            if (animator?.isRunning != true) return
            if (resolvedKind != kind) {
                kind = resolvedKind
                installShaders(resolvedKind)
            }
            resolveBoost = 1f
            invalidate()
        }

        fun preview(requestedKind: InvocationPulseKind, frameProgress: Float) {
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
            kind = requestedKind
            progress = frameProgress.coerceIn(0f, 1f)
            resolveBoost = if (progress in 0.3f..0.52f) 0.65f else 0f
            previewMode = true
            installShaders(requestedKind)
            invalidate()
        }

        fun release() {
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
            previewMode = false
            atmospherePaint.shader = null
            bloomPaint.shader = null
            corePaint.shader = null
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            val opticalInset = max(1f, density * 0.8f)
            edgeBounds.set(opticalInset, opticalInset, width - opticalInset, height - opticalInset)
            val platformCorner = if (Build.VERSION.SDK_INT >= 31) {
                listOf(
                    RoundedCorner.POSITION_TOP_LEFT,
                    RoundedCorner.POSITION_TOP_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_LEFT,
                ).maxOfOrNull { rootWindowInsets?.getRoundedCorner(it)?.radius ?: 0 }?.toFloat() ?: 0f
            } else {
                0f
            }
            val corner = maxOf(resources.displayMetrics.density * 31f, platformCorner)
                .coerceAtMost(edgeBounds.shortSide() * 0.18f)
            edgePath.reset()
            edgePath.addRoundRect(edgeBounds, corner, corner, Path.Direction.CW)
            pathMeasure.setPath(edgePath, true)
            measuredLength = pathMeasure.length
            installShaders(kind)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if ((animator?.isRunning != true && !previewMode) || measuredLength <= 0f) return

            val activation = (progress / 0.16f).coerceIn(0f, 1f)
            val decay = when {
                progress < 0.56f -> 1f
                else -> (1f - (progress - 0.56f) / 0.44f).coerceIn(0f, 1f)
            }
            val envelope = activation * decay
            val asymmetry = 0.78f + 0.22f * activation
            val boost = 1f + resolveBoost * 0.28f
            val contraction = if (kind == InvocationPulseKind.STOP) 1f - progress * 0.18f else 1f

            rotateShaders(progress)
            atmospherePaint.alpha = (34f * envelope * asymmetry).toInt().coerceIn(0, 255)
            bloomPaint.alpha = (112f * envelope * boost * contraction).toInt().coerceIn(0, 255)
            corePaint.alpha = (238f * envelope * boost).toInt().coerceIn(0, 255)
            canvas.drawPath(edgePath, atmospherePaint)
            canvas.drawPath(edgePath, bloomPaint)
            canvas.drawPath(edgePath, corePaint)

            val direction = if (kind == InvocationPulseKind.STOP) -1f else 1f
            drawHotspot(canvas, (1.08f + direction * progress * 0.93f) % 1f, envelope, 1f)
            drawHotspot(canvas, (1.43f + direction * progress * 0.57f) % 1f, envelope, 0.72f)
            if (progress < 0.34f) drawHotspot(canvas, (1.74f + direction * progress * 0.38f) % 1f, envelope, 0.52f)
        }

        private fun drawHotspot(canvas: Canvas, fraction: Float, envelope: Float, energy: Float) {
            if (!pathMeasure.getPosTan(measuredLength * fraction, hotspotPosition, hotspotTangent)) return
            val palette = palette(kind)
            hotspotBloomPaint.color = palette.hot
            hotspotBloomPaint.alpha = (70f * envelope * energy).toInt().coerceIn(0, 255)
            hotspotCorePaint.color = Color.WHITE
            hotspotCorePaint.alpha = (230f * envelope * energy).toInt().coerceIn(0, 255)
            canvas.drawCircle(hotspotPosition[0], hotspotPosition[1], density * (9f + 4f * resolveBoost), hotspotBloomPaint)
            canvas.drawCircle(hotspotPosition[0], hotspotPosition[1], density * 1.65f, hotspotCorePaint)
        }

        private fun installShaders(pulseKind: InvocationPulseKind) {
            if (width <= 0 || height <= 0) return
            val palette = palette(pulseKind)
            val centerX = width * 0.5f
            val centerY = height * 0.5f
            atmosphereShader = SweepGradient(centerX, centerY, palette.atmosphere, palette.positions)
            bloomShader = SweepGradient(centerX, centerY, palette.bloom, palette.positions)
            coreShader = SweepGradient(centerX, centerY, palette.core, palette.positions)
            atmospherePaint.shader = atmosphereShader
            bloomPaint.shader = bloomShader
            corePaint.shader = coreShader
        }

        private fun rotateShaders(phase: Float) {
            val centerX = width * 0.5f
            val centerY = height * 0.5f
            shaderMatrix.setRotate(phase * 106f, centerX, centerY)
            atmosphereShader?.setLocalMatrix(shaderMatrix)
            shaderMatrix.setRotate(-phase * 83f + 9f, centerX, centerY)
            bloomShader?.setLocalMatrix(shaderMatrix)
            shaderMatrix.setRotate(phase * 137f - 17f, centerX, centerY)
            coreShader?.setLocalMatrix(shaderMatrix)
        }

        private fun strokePaint(widthDp: Float, initialAlpha: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = widthDp * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            alpha = initialAlpha
        }

        private fun completeOnce() {
            if (completionSent) return
            completionSent = true
            finished()
        }

        private data class PulsePalette(
            val hot: Int,
            val atmosphere: IntArray,
            val bloom: IntArray,
            val core: IntArray,
            val positions: FloatArray,
        )

        private fun palette(pulseKind: InvocationPulseKind): PulsePalette {
            val positions = floatArrayOf(0f, 0.13f, 0.31f, 0.55f, 0.73f, 0.89f, 1f)
            return when (pulseKind) {
                InvocationPulseKind.START -> PulsePalette(
                    hot = 0xFF79E5FF.toInt(),
                    atmosphere = intArrayOf(0x0058D7FF, 0x5058D7FF, 0x305F8BFF, 0x00896EFF, 0x44896EFF, 0x2058D7FF, 0x0058D7FF),
                    bloom = intArrayOf(0x7058D7FF, 0xD058D7FF.toInt(), 0xA05F8BFF.toInt(), 0x50896EFF, 0xC0896EFF.toInt(), 0x9058D7FF.toInt(), 0x7058D7FF),
                    core = intArrayOf(0xFFF2FDFF.toInt(), 0xFF8FEAFF.toInt(), 0xFFE9F0FF.toInt(), 0xFF896EFF.toInt(), 0xFFF4EEFF.toInt(), 0xFF78DEFF.toInt(), 0xFFF2FDFF.toInt()),
                    positions = positions,
                )
                InvocationPulseKind.STOP -> PulsePalette(
                    hot = 0xFFFF8CC3.toInt(),
                    atmosphere = intArrayOf(0x00896EFF, 0x40896EFF, 0x30D965F5, 0x00FF76AF, 0x48FF76AF, 0x20896EFF, 0x00896EFF),
                    bloom = intArrayOf(0x70896EFF, 0xB0896EFF.toInt(), 0xB0D965F5.toInt(), 0x70FF76AF, 0xC0FF76AF.toInt(), 0x90896EFF.toInt(), 0x70896EFF),
                    core = intArrayOf(0xFFF4EEFF.toInt(), 0xFFBFB0FF.toInt(), 0xFFFFEFFF.toInt(), 0xFFFF87BC.toInt(), 0xFFFFF2F8.toInt(), 0xFFB6A8FF.toInt(), 0xFFF4EEFF.toInt()),
                    positions = positions,
                )
                InvocationPulseKind.ERROR -> PulsePalette(
                    hot = 0xFFFFB35E.toInt(),
                    atmosphere = intArrayOf(0x00FFB35E, 0x48FFB35E, 0x30FF765F, 0x00FFB35E, 0x40FF765F, 0x20FFB35E, 0x00FFB35E),
                    bloom = intArrayOf(0x70FFB35E, 0xC0FFB35E.toInt(), 0xA0FF765F.toInt(), 0x60FFB35E, 0xB0FF765F.toInt(), 0x90FFB35E.toInt(), 0x70FFB35E),
                    core = intArrayOf(0xFFFFF4E5.toInt(), 0xFFFFC77F.toInt(), 0xFFFFF1E8.toInt(), 0xFFFF806B.toInt(), 0xFFFFF2EA.toInt(), 0xFFFFC178.toInt(), 0xFFFFF4E5.toInt()),
                    positions = positions,
                )
            }
        }

    private fun RectF.shortSide(): Float = minOf(width(), height())
}
