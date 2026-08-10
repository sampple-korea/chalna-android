package app.chalna.capture.assistant

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession
import android.view.RoundedCorner
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.withRotation
import app.chalna.capture.ChalnaApplication
import app.chalna.capture.capture.CaptureRuntime
import app.chalna.capture.capture.CaptureTelemetryRegistry
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureCommandResult
import app.chalna.capture.domain.CaptureState
import app.chalna.capture.domain.CaptureTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.max

class ChalnaVoiceInteractionSession(
    private val appContext: Context,
) : VoiceInteractionSession(appContext) {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dispatchedSession: String? = null
    private var pulseView: ChalnaInvocationGlowView? = null
    private var pendingPulse: InvocationPulseKind? = null
    private var activeInvocation: AssistantInvocation? = null
    private var failsafeJob: Job? = null
    private var stateJob: Job? = null

    override fun onShow(
        args: Bundle?,
        showFlags: Int,
    ) {
        super.onShow(args, showFlags)
        val sessionKey = AssistantInvocationRegistry.sessionKey(args)
        if (sessionKey != null && dispatchedSession == sessionKey) return
        val prepared = sessionKey?.let(AssistantInvocationRegistry::take)
        val invocation =
            prepared ?: run {
                val id = AssistantInvocationRegistry.invocationId(args)
                CaptureTelemetryRegistry.mark(id, "invocation_received")
                val result =
                    (appContext.applicationContext as ChalnaApplication).graph.captureCommands.dispatch(
                        id,
                        CaptureCommand.TOGGLE,
                        CaptureTrigger.ASSISTANT,
                    )
                AssistantInvocation(id, result)
            }
        dispatchedSession = sessionKey ?: invocation.id
        activeInvocation = invocation
        pendingPulse = invocation.result.toPulseKind()
        pulseView?.activate(requireNotNull(pendingPulse))
        observeResolution(invocation)
        failsafeJob?.cancel()
        failsafeJob =
            sessionScope.launch {
                delay(SESSION_FAILSAFE_MILLIS)
                pulseView?.release()
                finish()
            }
    }

    override fun onCreateContentView(): View =
        ChalnaInvocationGlowView(appContext) { finish() }.also { view ->
            pulseView = view
            pendingPulse?.let(view::activate)
        }

    private fun observeResolution(invocation: AssistantInvocation) {
        stateJob?.cancel()
        stateJob =
            sessionScope.launch {
                CaptureRuntime.state.collectLatest { state ->
                    val kind = invocation.result.toPulseKind()
                    val resolved =
                        when (kind) {
                            InvocationPulseKind.START -> state is CaptureState.Recording || state is CaptureState.Failed
                            InvocationPulseKind.STOP ->
                                state is CaptureState.Finalizing || state is CaptureState.Persisting ||
                                    state is CaptureState.Saved || state is CaptureState.Failed
                            InvocationPulseKind.CANCEL ->
                                state is CaptureState.Idle || state is CaptureState.Finalizing ||
                                    state is CaptureState.Failed
                            InvocationPulseKind.BUSY -> true
                            InvocationPulseKind.ERROR -> true
                        }
                    if (resolved) {
                        viewOrPendingResolve(if (state is CaptureState.Failed) InvocationPulseKind.ERROR else kind)
                        return@collectLatest
                    }
                }
            }
    }

    private fun viewOrPendingResolve(kind: InvocationPulseKind) {
        pendingPulse = kind
        pulseView?.resolve(kind)
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?,
    ) {
        // Assist structure, screen content, and foreground-app context are deliberately ignored.
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) = Unit

    override fun onHide() {
        failsafeJob?.cancel()
        stateJob?.cancel()
        pulseView?.release()
        activeInvocation = null
        pendingPulse = null
        super.onHide()
    }

    override fun onDestroy() {
        pulseView?.release()
        pulseView = null
        failsafeJob?.cancel()
        stateJob?.cancel()
        sessionScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SESSION_FAILSAFE_MILLIS = 1_200L
    }
}

internal enum class InvocationPulseKind { START, STOP, CANCEL, BUSY, ERROR }

private fun CaptureCommandResult.toPulseKind(): InvocationPulseKind =
    when (this) {
        is CaptureCommandResult.AcceptedStart -> InvocationPulseKind.START
        is CaptureCommandResult.AcceptedStop -> InvocationPulseKind.STOP
        is CaptureCommandResult.AcceptedCancelStart -> InvocationPulseKind.CANCEL
        is CaptureCommandResult.AlreadyStopping, is CaptureCommandResult.BusySaving,
        is CaptureCommandResult.NoActiveCapture,
        -> InvocationPulseKind.BUSY
        is CaptureCommandResult.Duplicate -> original?.toPulseKind() ?: InvocationPulseKind.BUSY
        is CaptureCommandResult.Rejected, is CaptureCommandResult.FailedToDispatch -> InvocationPulseKind.ERROR
    }

/**
 * Transient three-layer perimeter illumination. Geometry, shaders, matrices, and hotspot buffers
 * are cached per size; animation only mutates scalar phase/alpha values.
 */
internal class ChalnaInvocationGlowView(
    context: Context,
    private val finished: () -> Unit,
) : View(context) {
    constructor(context: Context) : this(context, {})

    private val density = resources.displayMetrics.density
    private val edgePath = Path()
    private val edgeBounds = RectF()
    private val pathMeasure = PathMeasure()
    private val shaderMatrix = Matrix()
    private val hotspotShaderMatrix = Matrix()
    private val hotspotPosition = FloatArray(2)
    private val hotspotTangent = FloatArray(2)
    private val cornerRadii = FloatArray(8)
    private val atmospherePaint = strokePaint(22f, 22)
    private val bloomPaint = strokePaint(10f, 72)
    private val corePaint = strokePaint(1.35f, 235)
    private val hotspotBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hotspotTailPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val hotspotCorePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val atmosphereNode = RenderNode("chalna-atmosphere")
    private val bloomNode = RenderNode("chalna-bloom")
    private var animator: ValueAnimator? = null
    private var atmosphereShader: SweepGradient? = null
    private var bloomShader: SweepGradient? = null
    private var coreShader: SweepGradient? = null
    private var hotspotShader: RadialGradient? = null
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
            installBlurEffects()
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
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration =
                    when (requestedKind) {
                        InvocationPulseKind.START -> 650L
                        InvocationPulseKind.STOP -> 570L
                        InvocationPulseKind.CANCEL -> 430L
                        InvocationPulseKind.BUSY -> 420L
                        InvocationPulseKind.ERROR -> 460L
                    }
                interpolator = PathInterpolator(0.18f, 0.78f, 0.22f, 1f)
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    resolveBoost *= 0.88f
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = completeOnce()
                    },
                )
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

    fun preview(
        requestedKind: InvocationPulseKind,
        frameProgress: Float,
    ) {
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
        hotspotBloomPaint.shader = null
        hotspotTailPaint.shader = null
        hotspotShader = null
        atmosphereNode.discardDisplayList()
        bloomNode.discardDisplayList()
    }

    // RoundedCorner position constants are inlined integers and are only passed to the
    // API-31 call inside the guarded branch below. The fallback never touches that API.
    @SuppressLint("InlinedApi")
    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        val opticalInset = max(1f, density * 0.8f)
        edgeBounds.set(opticalInset, opticalInset, width - opticalInset, height - opticalInset)
        val fallbackCorner = resources.displayMetrics.density * 31f
        val maximumCorner = edgeBounds.shortSide() * 0.18f

        fun corner(position: Int): Float =
            max(
                fallbackCorner,
                if (Build.VERSION.SDK_INT >= 31) {
                    rootWindowInsets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
                } else {
                    0f
                },
            ).coerceAtMost(maximumCorner)
        val topLeft = corner(RoundedCorner.POSITION_TOP_LEFT)
        val topRight = corner(RoundedCorner.POSITION_TOP_RIGHT)
        val bottomRight = corner(RoundedCorner.POSITION_BOTTOM_RIGHT)
        val bottomLeft = corner(RoundedCorner.POSITION_BOTTOM_LEFT)
        cornerRadii[0] = topLeft
        cornerRadii[1] = topLeft
        cornerRadii[2] = topRight
        cornerRadii[3] = topRight
        cornerRadii[4] = bottomRight
        cornerRadii[5] = bottomRight
        cornerRadii[6] = bottomLeft
        cornerRadii[7] = bottomLeft
        edgePath.reset()
        edgePath.addRoundRect(edgeBounds, cornerRadii, Path.Direction.CW)
        pathMeasure.setPath(edgePath, true)
        measuredLength = pathMeasure.length
        atmosphereNode.setPosition(0, 0, width, height)
        bloomNode.setPosition(0, 0, width, height)
        installShaders(kind)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if ((animator?.isRunning != true && !previewMode) || measuredLength <= 0f) return

        val activation = (progress / 0.16f).coerceIn(0f, 1f)
        val decay =
            when {
                progress < 0.56f -> 1f
                else -> (1f - (progress - 0.56f) / 0.44f).coerceIn(0f, 1f)
            }
        val envelope = activation * decay
        val asymmetry = 0.78f + 0.22f * activation
        val boost = 1f + resolveBoost * 0.28f
        val contraction = if (kind == InvocationPulseKind.STOP || kind == InvocationPulseKind.CANCEL) 1f - progress * 0.18f else 1f

        rotateShaders(progress)
        atmospherePaint.alpha = (34f * envelope * asymmetry).toInt().coerceIn(0, 255)
        bloomPaint.alpha = (112f * envelope * boost * contraction).toInt().coerceIn(0, 255)
        corePaint.alpha = (238f * envelope * boost).toInt().coerceIn(0, 255)
        drawOpticalLayer(canvas, atmosphereNode, atmospherePaint)
        drawOpticalLayer(canvas, bloomNode, bloomPaint)
        canvas.drawPath(edgePath, corePaint)

        val direction = if (kind == InvocationPulseKind.STOP || kind == InvocationPulseKind.CANCEL) -1f else 1f
        drawHotspot(canvas, (1.08f + direction * progress * 0.93f) % 1f, envelope, 1f)
        drawHotspot(canvas, (1.43f + direction * progress * 0.57f) % 1f, envelope, 0.72f)
        if (progress < 0.34f) drawHotspot(canvas, (1.74f + direction * progress * 0.38f) % 1f, envelope, 0.52f)
    }

    private fun drawHotspot(
        canvas: Canvas,
        fraction: Float,
        envelope: Float,
        energy: Float,
    ) {
        if (!pathMeasure.getPosTan(measuredLength * fraction, hotspotPosition, hotspotTangent)) return
        hotspotShaderMatrix.setTranslate(hotspotPosition[0], hotspotPosition[1])
        hotspotShader?.setLocalMatrix(hotspotShaderMatrix)
        hotspotBloomPaint.alpha = (185f * envelope * energy).toInt().coerceIn(0, 255)
        hotspotTailPaint.alpha = (138f * envelope * energy).toInt().coerceIn(0, 255)
        hotspotTailPaint.strokeWidth = density * (4.2f + resolveBoost)
        hotspotCorePaint.color = Color.WHITE
        hotspotCorePaint.alpha = (230f * envelope * energy).toInt().coerceIn(0, 255)
        hotspotCorePaint.strokeWidth = density * 1.35f
        val tangentLength =
            kotlin.math
                .sqrt(
                    hotspotTangent[0] * hotspotTangent[0] + hotspotTangent[1] * hotspotTangent[1],
                ).coerceAtLeast(0.001f)
        val tangentX = hotspotTangent[0] / tangentLength
        val tangentY = hotspotTangent[1] / tangentLength
        val centerX = hotspotPosition[0]
        val centerY = hotspotPosition[1]
        val bloomRadius = density * (10f + 2f * resolveBoost)
        canvas.withRotation(
            Math.toDegrees(atan2(tangentY, tangentX).toDouble()).toFloat(),
            centerX,
            centerY,
        ) {
            scale(2.15f, 0.78f, centerX, centerY)
            drawCircle(centerX, centerY, bloomRadius, hotspotBloomPaint)
        }
        canvas.drawLine(
            centerX - tangentX * density * 19f,
            centerY - tangentY * density * 19f,
            centerX + tangentX * density * 3f,
            centerY + tangentY * density * 3f,
            hotspotTailPaint,
        )
        canvas.drawLine(
            centerX - tangentX * density * 2.2f,
            centerY - tangentY * density * 2.2f,
            centerX + tangentX * density * 3.8f,
            centerY + tangentY * density * 3.8f,
            hotspotCorePaint,
        )
    }

    private fun drawOpticalLayer(
        canvas: Canvas,
        node: RenderNode,
        paint: Paint,
    ) {
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val recordingCanvas = node.beginRecording()
            recordingCanvas.drawPath(edgePath, paint)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            canvas.drawPath(edgePath, paint)
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun installBlurEffects() {
        atmosphereNode.setRenderEffect(
            RenderEffect.createBlurEffect(density * 13f, density * 13f, Shader.TileMode.CLAMP),
        )
        bloomNode.setRenderEffect(
            RenderEffect.createBlurEffect(density * 4.5f, density * 4.5f, Shader.TileMode.CLAMP),
        )
    }

    private fun installShaders(pulseKind: InvocationPulseKind) {
        if (width <= 0 || height <= 0) return
        val palette = palette(pulseKind)
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        atmosphereShader = SweepGradient(centerX, centerY, palette.atmosphere, palette.positions)
        bloomShader = SweepGradient(centerX, centerY, palette.bloom, palette.positions)
        coreShader = SweepGradient(centerX, centerY, palette.core, palette.positions)
        hotspotShader =
            RadialGradient(
                0f,
                0f,
                density * 17f,
                intArrayOf(
                    Color.argb(112, Color.red(palette.hot), Color.green(palette.hot), Color.blue(palette.hot)),
                    Color.argb(38, Color.red(palette.hot), Color.green(palette.hot), Color.blue(palette.hot)),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .34f, 1f),
                Shader.TileMode.CLAMP,
            )
        atmospherePaint.shader = atmosphereShader
        bloomPaint.shader = bloomShader
        corePaint.shader = coreShader
        hotspotBloomPaint.shader = hotspotShader
        hotspotTailPaint.shader = hotspotShader
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

    private fun strokePaint(
        widthDp: Float,
        initialAlpha: Int,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            InvocationPulseKind.START ->
                PulsePalette(
                    hot = 0xFF79E5FF.toInt(),
                    atmosphere = intArrayOf(0x0058D7FF, 0x5058D7FF, 0x305F8BFF, 0x00896EFF, 0x44896EFF, 0x2058D7FF, 0x0058D7FF),
                    bloom =
                        intArrayOf(
                            0x7058D7FF,
                            0xD058D7FF.toInt(),
                            0xA05F8BFF.toInt(),
                            0x50896EFF,
                            0xC0896EFF.toInt(),
                            0x9058D7FF.toInt(),
                            0x7058D7FF,
                        ),
                    core =
                        intArrayOf(
                            0xFFF2FDFF.toInt(),
                            0xFF8FEAFF.toInt(),
                            0xFFE9F0FF.toInt(),
                            0xFF896EFF.toInt(),
                            0xFFF4EEFF.toInt(),
                            0xFF78DEFF.toInt(),
                            0xFFF2FDFF.toInt(),
                        ),
                    positions = positions,
                )
            InvocationPulseKind.STOP ->
                PulsePalette(
                    hot = 0xFFFF8CC3.toInt(),
                    atmosphere = intArrayOf(0x00896EFF, 0x40896EFF, 0x30D965F5, 0x00FF76AF, 0x48FF76AF, 0x20896EFF, 0x00896EFF),
                    bloom =
                        intArrayOf(
                            0x70896EFF,
                            0xB0896EFF.toInt(),
                            0xB0D965F5.toInt(),
                            0x70FF76AF,
                            0xC0FF76AF.toInt(),
                            0x90896EFF.toInt(),
                            0x70896EFF,
                        ),
                    core =
                        intArrayOf(
                            0xFFF4EEFF.toInt(),
                            0xFFBFB0FF.toInt(),
                            0xFFFFEFFF.toInt(),
                            0xFFFF87BC.toInt(),
                            0xFFFFF2F8.toInt(),
                            0xFFB6A8FF.toInt(),
                            0xFFF4EEFF.toInt(),
                        ),
                    positions = positions,
                )
            InvocationPulseKind.CANCEL ->
                PulsePalette(
                    hot = 0xFFD6D4E0.toInt(),
                    atmosphere = intArrayOf(0x00A9A8B6, 0x34A9A8B6, 0x246D7C92, 0x00A9A8B6, 0x306D7C92, 0x18A9A8B6, 0x00A9A8B6),
                    bloom = intArrayOf(0x50A9A8B6, 0x88C7C5D1.toInt(), 0x706D7C92, 0x40A9A8B6, 0x806D7C92.toInt(), 0x68A9A8B6, 0x50A9A8B6),
                    core =
                        intArrayOf(
                            0xFFF5F4F8.toInt(),
                            0xFFD6D4E0.toInt(),
                            0xFFF0F4FA.toInt(),
                            0xFFAEBBCB.toInt(),
                            0xFFF5F4F8.toInt(),
                            0xFFD6D4E0.toInt(),
                            0xFFF5F4F8.toInt(),
                        ),
                    positions = positions,
                )
            InvocationPulseKind.BUSY ->
                PulsePalette(
                    hot = 0xFFFFC36B.toInt(),
                    atmosphere = intArrayOf(0x00FFC36B, 0x38FFC36B, 0x287C6D93, 0x00FFC36B, 0x347C6D93, 0x18FFC36B, 0x00FFC36B),
                    bloom = intArrayOf(0x50FFC36B, 0x90FFC36B.toInt(), 0x787C6D93, 0x40FFC36B, 0x887C6D93.toInt(), 0x68FFC36B, 0x50FFC36B),
                    core =
                        intArrayOf(
                            0xFFFFF7EA.toInt(),
                            0xFFFFD59A.toInt(),
                            0xFFF4F0FF.toInt(),
                            0xFFC7B9E8.toInt(),
                            0xFFFFF7EA.toInt(),
                            0xFFFFD59A.toInt(),
                            0xFFFFF7EA.toInt(),
                        ),
                    positions = positions,
                )
            InvocationPulseKind.ERROR ->
                PulsePalette(
                    hot = 0xFFFFB35E.toInt(),
                    atmosphere = intArrayOf(0x00FFB35E, 0x48FFB35E, 0x30FF765F, 0x00FFB35E, 0x40FF765F, 0x20FFB35E, 0x00FFB35E),
                    bloom =
                        intArrayOf(
                            0x70FFB35E,
                            0xC0FFB35E.toInt(),
                            0xA0FF765F.toInt(),
                            0x60FFB35E,
                            0xB0FF765F.toInt(),
                            0x90FFB35E.toInt(),
                            0x70FFB35E,
                        ),
                    core =
                        intArrayOf(
                            0xFFFFF4E5.toInt(),
                            0xFFFFC77F.toInt(),
                            0xFFFFF1E8.toInt(),
                            0xFFFF806B.toInt(),
                            0xFFFFF2EA.toInt(),
                            0xFFFFC178.toInt(),
                            0xFFFFF4E5.toInt(),
                        ),
                    positions = positions,
                )
        }
    }

    private fun RectF.shortSide(): Float = minOf(width(), height())
}
