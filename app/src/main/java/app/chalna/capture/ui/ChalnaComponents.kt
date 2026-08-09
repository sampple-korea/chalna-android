package app.chalna.capture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import kotlin.math.cos
import kotlin.math.sin

@Composable internal fun ChalnaBackdrop(static: Boolean) {
    val phase = if (static) remember { mutableFloatStateOf(0f) } else {
        val transition = rememberInfiniteTransition(label = "backdrop")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000)), label = "backdropPhase")
    }
    val colors = ChalnaTheme.colors
    Canvas(
        Modifier.fillMaxSize().drawWithCache {
            val coolField = Brush.radialGradient(
                listOf(colors.accent.copy(.085f), colors.accent.copy(.022f), Color.Transparent),
                center = Offset(size.width * .20f, size.height * .12f),
                radius = size.minDimension * .76f,
            )
            val violetField = Brush.radialGradient(
                listOf(colors.accent2.copy(.068f), colors.accent2.copy(.018f), Color.Transparent),
                center = Offset(size.width * .78f, size.height * .78f),
                radius = size.minDimension * .82f,
            )
            val centerField = Brush.radialGradient(
                listOf(colors.surfaceHigh.copy(.20f), Color.Transparent),
                center = center,
                radius = size.minDimension * .60f,
            )
            onDrawBehind {
                val angle = phase.value * 6.283f
                withTransform({ translate(size.width * .025f * cos(angle), size.height * .018f * sin(angle)) }) {
                    drawRect(coolField)
                }
                withTransform({ translate(-size.width * .020f * sin(angle), size.height * .016f * cos(angle)) }) {
                    drawRect(violetField)
                }
                drawRect(centerField)
            }
        },
    ) {}
}

@Composable internal fun Hairline() = Spacer(Modifier.fillMaxWidth().height(1.dp).background(ChalnaTheme.colors.outline.copy(.55f)))

internal enum class ChalnaIcon { MARK, GALLERY, SETTINGS, BACK, CHECK, CAMERA, MIC, ASSISTANT, BELL, PLAY, PAUSE, VOLUME, MUTED, FULLSCREEN, SHARE, MORE, INFO, DELETE, EXPORT, EXTERNAL, CLOSE }

@Composable
internal fun Modifier.clickableNoRipple(role: Role? = null, enabled: Boolean = true, onClick: () -> Unit): Modifier = composedClickable(role, enabled, onClick)

@Composable
private fun Modifier.composedClickable(role: Role?, enabled: Boolean, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
internal fun ChalnaText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = ChalnaTheme.colors.text,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
) = BasicText(
    text = text,
    modifier = modifier,
    style = androidx.compose.ui.text.TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.38f).sp,
        fontFamily = ChalnaFontFamily,
        fontWeight = weight,
        textAlign = align ?: TextAlign.Start,
    ),
)

@Composable
internal fun ChalnaText(
    text: String,
    size: Int,
    color: Color = ChalnaTheme.colors.text,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
) = ChalnaText(text, Modifier, size, color, weight, align)

@Composable internal fun Heading(text: String, modifier: Modifier = Modifier) = ChalnaText(text, modifier.semantics { heading() }, 26, weight = FontWeight.Bold)
@Composable internal fun Body(text: String, modifier: Modifier = Modifier) = ChalnaText(text, modifier, 15, ChalnaTheme.colors.muted)
@Composable internal fun SectionTitle(text: String) = ChalnaText(text, Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() }, 13, ChalnaTheme.colors.accent, FontWeight.Bold)

@Composable internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    content = content,
)

@Composable internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ChalnaTheme.colors.surface)
        .border(1.dp, ChalnaTheme.colors.outline, RoundedCornerShape(22.dp)).padding(16.dp),
    content = content,
)

@Composable internal fun TopBar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) = Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
) {
    if (back != null) { IconButton(ChalnaIcon.BACK, title = androidx.compose.ui.res.stringResource(app.chalna.capture.R.string.back), onClick = back); Spacer(Modifier.width(8.dp)) }
    Heading(title, Modifier.weight(1f))
    actions()
}

@Composable internal fun IconButton(icon: ChalnaIcon, title: String, selected: Boolean = false, onClick: () -> Unit) = Box(
    Modifier.size(48.dp).clip(CircleShape).background(if (selected) ChalnaTheme.colors.surfaceHigh else Color.Transparent)
        .clickableNoRipple(Role.Button, onClick = onClick).semantics { role = Role.Button; contentDescription = title },
    contentAlignment = Alignment.Center,
) { ChalnaIconCanvas(icon, Modifier.size(24.dp), if (selected) ChalnaTheme.colors.accent else ChalnaTheme.colors.text) }

@Composable internal fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) = Box(
    Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp))
        .background(if (enabled) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline)
        .clickableNoRipple(Role.Button, enabled, onClick).semantics { role = Role.Button },
    contentAlignment = Alignment.Center,
) { ChalnaText(text, 16, if (enabled) Color(0xFF071018) else ChalnaTheme.colors.muted, FontWeight.Bold) }

@Composable internal fun SecondaryButton(text: String, onClick: () -> Unit) = Box(
    Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, ChalnaTheme.colors.outline, RoundedCornerShape(17.dp))
        .clickableNoRipple(Role.Button, onClick = onClick), contentAlignment = Alignment.Center,
) { ChalnaText(text, 15, weight = FontWeight.SemiBold) }

@Composable internal fun SettingRow(title: String, detail: String, icon: ChalnaIcon, active: Boolean? = null, onClick: () -> Unit) {
    val status = active?.let { androidx.compose.ui.res.stringResource(if (it) app.chalna.capture.R.string.allowed else app.chalna.capture.R.string.action_needed) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
            .clickableNoRipple(Role.Button, onClick = onClick)
            .semantics { status?.let { stateDescription = it } }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { ChalnaIconCanvas(icon, Modifier.size(21.dp), ChalnaTheme.colors.accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { ChalnaText(title, 16, weight = FontWeight.SemiBold); ChalnaText(detail, 13, ChalnaTheme.colors.muted) }
        if (active != null) StatusPill(active)
    }
}

@Composable internal fun StatusPill(done: Boolean) = Box(
    Modifier.size(28.dp).clip(CircleShape).background(if (done) ChalnaTheme.colors.positive.copy(.18f) else ChalnaTheme.colors.warning.copy(.16f)), contentAlignment = Alignment.Center,
) { if (done) ChalnaIconCanvas(ChalnaIcon.CHECK, Modifier.size(16.dp), ChalnaTheme.colors.positive) else Box(Modifier.size(6.dp).clip(CircleShape).background(ChalnaTheme.colors.warning)) }

@Composable internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) = Box(
    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) ChalnaTheme.colors.accent.copy(.18f) else ChalnaTheme.colors.surface)
        .border(1.dp, if (selected) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline, RoundedCornerShape(16.dp))
        .clickableNoRipple(Role.RadioButton, onClick = onClick).semantics { this.role = Role.RadioButton; this.selected = selected }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center,
) { ChalnaText(text, 14, if (selected) ChalnaTheme.colors.accent else ChalnaTheme.colors.text, FontWeight.SemiBold) }

@Composable internal fun ToggleRow(title: String, checked: Boolean, onClick: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, tween(170), label = "toggleThumb")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickableNoRipple(Role.Switch, onClick = { onClick(!checked) })
            .semantics { role = Role.Switch; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalnaText(title, Modifier.weight(1f), 16)
        Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(if (checked) ChalnaTheme.colors.accent else ChalnaTheme.colors.outline).padding(4.dp)) {
            Box(Modifier.offset { IntOffset(thumbOffset.roundToPx(), 0) }.size(20.dp).clip(CircleShape).background(if (checked) Color(0xFF071018) else ChalnaTheme.colors.muted))
        }
    }
}

@Composable internal fun ChalnaIconCanvas(icon: ChalnaIcon, modifier: Modifier = Modifier, color: Color = ChalnaTheme.colors.text) = Canvas(modifier) {
    val w = size.width; val h = size.height; val stroke = Stroke(width = size.minDimension * .085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun linePath(points: List<Offset>) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = stroke)
    }
    fun line(a: Offset, b: Offset) = linePath(listOf(a, b))
    fun line(a: Offset, b: Offset, c: Offset) = linePath(listOf(a, b, c))
    fun line(a: Offset, b: Offset, c: Offset, d: Offset) = linePath(listOf(a, b, c, d))
    fun line(a: Offset, b: Offset, c: Offset, d: Offset, e: Offset) = linePath(listOf(a, b, c, d, e))
    when (icon) {
        ChalnaIcon.MARK -> { drawArc(color, 38f, 286f, false, Offset(w*.14f,h*.14f), Size(w*.72f,h*.72f), style=stroke); drawCircle(color,w*.07f,Offset(w*.82f,h*.26f)) }
        ChalnaIcon.GALLERY -> { drawRoundRect(color.copy(.12f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.14f)); drawRoundRect(color, cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.14f), style=stroke); line(Offset(w*.14f,h*.76f),Offset(w*.40f,h*.50f),Offset(w*.57f,h*.65f),Offset(w*.78f,h*.40f),Offset(w*.90f,h*.53f)); drawCircle(color,w*.07f,Offset(w*.32f,h*.31f)) }
        ChalnaIcon.SETTINGS -> { drawCircle(color,w*.29f,style=stroke); drawCircle(color,w*.08f); repeat(8) { i -> val a=i*Math.PI/4; line(Offset(w*.5f+(kotlin.math.cos(a)*w*.34f).toFloat(),h*.5f+(kotlin.math.sin(a)*h*.34f).toFloat()),Offset(w*.5f+(kotlin.math.cos(a)*w*.43f).toFloat(),h*.5f+(kotlin.math.sin(a)*h*.43f).toFloat())) } }
        ChalnaIcon.BACK -> line(Offset(w*.68f,h*.18f),Offset(w*.34f,h*.5f),Offset(w*.68f,h*.82f))
        ChalnaIcon.CHECK -> line(Offset(w*.20f,h*.52f),Offset(w*.42f,h*.72f),Offset(w*.82f,h*.28f))
        ChalnaIcon.CAMERA -> { drawRoundRect(color,Offset(w*.08f,h*.24f),Size(w*.84f,h*.62f),androidx.compose.ui.geometry.CornerRadius(w*.12f),style=stroke); drawCircle(color,w*.17f,style=stroke); line(Offset(w*.28f,h*.24f),Offset(w*.38f,h*.12f),Offset(w*.62f,h*.12f),Offset(w*.72f,h*.24f)) }
        ChalnaIcon.MIC -> { drawRoundRect(color,Offset(w*.35f,h*.08f),Size(w*.30f,h*.52f),androidx.compose.ui.geometry.CornerRadius(w*.15f),style=stroke); drawArc(color,0f,180f,false,Offset(w*.20f,h*.35f),Size(w*.60f,h*.40f),style=stroke); line(Offset(w*.5f,h*.75f),Offset(w*.5f,h*.91f)); line(Offset(w*.34f,h*.91f),Offset(w*.66f,h*.91f)) }
        ChalnaIcon.ASSISTANT -> { drawCircle(color,w*.25f,Offset(w*.5f,h*.5f),style=stroke); drawCircle(color,w*.06f,Offset(w*.5f,h*.13f)); drawCircle(color,w*.06f,Offset(w*.87f,h*.5f)); drawCircle(color,w*.06f,Offset(w*.5f,h*.87f)); drawCircle(color,w*.06f,Offset(w*.13f,h*.5f)) }
        ChalnaIcon.BELL -> { drawArc(color,195f,150f,false,Offset(w*.20f,h*.15f),Size(w*.60f,h*.72f),style=stroke); line(Offset(w*.18f,h*.72f),Offset(w*.82f,h*.72f)); drawArc(color,0f,180f,false,Offset(w*.40f,h*.70f),Size(w*.20f,h*.18f),style=stroke) }
        ChalnaIcon.PLAY -> { val p=Path().apply { moveTo(w*.32f,h*.18f); lineTo(w*.80f,h*.5f); lineTo(w*.32f,h*.82f); close() }; drawPath(p,color) }
        ChalnaIcon.PAUSE -> { drawRoundRect(color,Offset(w*.25f,h*.18f),Size(w*.17f,h*.64f)); drawRoundRect(color,Offset(w*.58f,h*.18f),Size(w*.17f,h*.64f)) }
        ChalnaIcon.VOLUME, ChalnaIcon.MUTED -> { val p=Path().apply { moveTo(w*.12f,h*.40f);lineTo(w*.32f,h*.40f);lineTo(w*.52f,h*.22f);lineTo(w*.52f,h*.78f);lineTo(w*.32f,h*.60f);lineTo(w*.12f,h*.60f);close() };drawPath(p,color); if(icon==ChalnaIcon.MUTED){line(Offset(w*.66f,h*.37f),Offset(w*.88f,h*.63f));line(Offset(w*.88f,h*.37f),Offset(w*.66f,h*.63f))}else drawArc(color,-55f,110f,false,Offset(w*.48f,h*.25f),Size(w*.34f,h*.50f),style=stroke) }
        ChalnaIcon.FULLSCREEN -> { line(Offset(w*.12f,h*.38f),Offset(w*.12f,h*.12f),Offset(w*.38f,h*.12f));line(Offset(w*.62f,h*.12f),Offset(w*.88f,h*.12f),Offset(w*.88f,h*.38f));line(Offset(w*.88f,h*.62f),Offset(w*.88f,h*.88f),Offset(w*.62f,h*.88f));line(Offset(w*.38f,h*.88f),Offset(w*.12f,h*.88f),Offset(w*.12f,h*.62f)) }
        ChalnaIcon.SHARE -> { drawCircle(color,w*.10f,Offset(w*.22f,h*.5f),style=stroke);drawCircle(color,w*.10f,Offset(w*.76f,h*.22f),style=stroke);drawCircle(color,w*.10f,Offset(w*.76f,h*.78f),style=stroke);line(Offset(w*.31f,h*.45f),Offset(w*.67f,h*.27f));line(Offset(w*.31f,h*.55f),Offset(w*.67f,h*.73f)) }
        ChalnaIcon.MORE -> { drawCircle(color,w*.07f,Offset(w*.22f,h*.5f));drawCircle(color,w*.07f,Offset(w*.5f,h*.5f));drawCircle(color,w*.07f,Offset(w*.78f,h*.5f)) }
        ChalnaIcon.INFO -> { drawCircle(color,w*.38f,style=stroke);drawCircle(color,w*.055f,Offset(w*.5f,h*.31f));line(Offset(w*.5f,h*.46f),Offset(w*.5f,h*.72f)) }
        ChalnaIcon.DELETE -> { drawRoundRect(color,Offset(w*.25f,h*.28f),Size(w*.50f,h*.58f),androidx.compose.ui.geometry.CornerRadius(w*.06f),style=stroke);line(Offset(w*.18f,h*.22f),Offset(w*.82f,h*.22f));line(Offset(w*.38f,h*.22f),Offset(w*.42f,h*.12f),Offset(w*.58f,h*.12f),Offset(w*.62f,h*.22f)) }
        ChalnaIcon.EXPORT -> { drawRoundRect(color,Offset(w*.12f,h*.38f),Size(w*.76f,h*.50f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=stroke);line(Offset(w*.5f,h*.68f),Offset(w*.5f,h*.12f));line(Offset(w*.30f,h*.32f),Offset(w*.5f,h*.12f),Offset(w*.70f,h*.32f)) }
        ChalnaIcon.EXTERNAL -> { drawRoundRect(color,Offset(w*.12f,h*.25f),Size(w*.62f,h*.63f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=stroke);line(Offset(w*.45f,h*.12f),Offset(w*.88f,h*.12f),Offset(w*.88f,h*.55f));line(Offset(w*.88f,h*.12f),Offset(w*.45f,h*.55f)) }
        ChalnaIcon.CLOSE -> { line(Offset(w*.20f,h*.20f),Offset(w*.80f,h*.80f));line(Offset(w*.80f,h*.20f),Offset(w*.20f,h*.80f)) }
    }
}

internal fun formatDuration(ms: Long): String {
    val total = (ms / 1_000).coerceAtLeast(0)
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}
