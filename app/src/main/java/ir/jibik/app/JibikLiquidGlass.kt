package ir.jibik.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Compose-only optical glass vocabulary.
 *
 * References: transparent/chrome rim, bright specular streaks, softly coloured
 * translucent layers and floating, pill-like controls. This is an *approximation*
 * of refractive glass, not real-time screen-content refraction. Avoid offscreen
 * blur of sensitive financial content and expensive shader loops.
 */
internal enum class GlassTone {
    CRYSTAL, FROSTED, EMERALD, HONEY, LILAC, ROSE,
}

internal object GlassPalette {
    val backgroundTop = Color(0xFFF8FAF7)
    val backgroundMid = Color(0xFFF1F7F2)
    val backgroundBottom = Color(0xFFFFF8F0)
    val highlight = Color(0xFFFFFFFF)
    val outline = Color(0xFFBED6C9)
}

private fun toneStops(tone: GlassTone): List<Color> = when (tone) {
    GlassTone.CRYSTAL -> listOf(
        Color(0xFFFDFEFD), Color(0xFCF9FCFA), Color(0xF8EDF5EF),
    )
    // Full opacity is intentional for dialogs: finance data behind the modal
    // must never bleed into labels, input borders or validation messages.
    GlassTone.FROSTED -> listOf(
        Color(0xFFFFFFFF), Color(0xFFFBFDFC), Color(0xFFF0F6F2),
    )
    GlassTone.EMERALD -> listOf(
        Color(0xF41A806F), Color(0xF406665B), Color(0xFF02483F),
    )
    GlassTone.HONEY -> listOf(
        Color(0xFFFFF6E8), Color(0xFAFFE4BE), Color(0xF5EBC38C),
    )
    GlassTone.LILAC -> listOf(
        Color(0xFFF5EDFF), Color(0xF3DCCDFC), Color(0xF3CBB9EC),
    )
    GlassTone.ROSE -> listOf(
        Color(0xFFFFF1E9), Color(0xF8FFD6C5), Color(0xF0FFC0A4),
    )
}

private fun glassShadow(tone: GlassTone): Color = when (tone) {
    GlassTone.EMERALD -> Color(0x80285F50)
    GlassTone.HONEY -> Color(0x768B6A38)
    GlassTone.LILAC -> Color(0x6C8A70B5)
    GlassTone.ROSE -> Color(0x678F665B)
    GlassTone.CRYSTAL -> Color(0x69617D74)
    GlassTone.FROSTED -> Color(0x885D7971)
}

/** A light mesh wash that brings out glass depth without a giant bitmap. */
internal fun Modifier.liquidBackdrop(): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                GlassPalette.backgroundTop,
                GlassPalette.backgroundMid,
                GlassPalette.backgroundBottom,
            )
        )
    )
    val cornerGlow = size.width * 0.9f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFC5FFDB).copy(alpha = 0.27f),
                Color.Transparent,
            ),
            center = Offset(size.width * .03f, size.height * .12f),
            radius = cornerGlow,
        ),
        center = Offset(size.width * .03f, size.height * .12f),
        radius = cornerGlow,
    )
    val warmGlow = size.width * 0.82f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFE0B6).copy(alpha = 0.27f),
                Color.Transparent,
            ),
            center = Offset(size.width * .95f, size.height * .74f),
            radius = warmGlow,
        ),
        center = Offset(size.width * .95f, size.height * .74f),
        radius = warmGlow,
    )
}

/**
 * Everything stays fully native and resolution-independent, with no bitmap
 * background on cards. Reduce GPU pressure by avoiding animated blur or
 * repeated offscreen RenderEffect operations.
 */
internal fun Modifier.liquidGlass(
    tone: GlassTone = GlassTone.CRYSTAL,
    radius: Dp = 23.dp,
    elevation: Dp = 10.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = glassShadow(tone),
            spotColor = glassShadow(tone),
        )
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = toneStops(tone),
                start = Offset.Zero,
                end = Offset(750f, 900f),
            )
        )
        .border(
            width = 0.9.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.White.copy(alpha = 0.22f),
                    Color(0xFF8BAFA4).copy(alpha = 0.49f),
                    Color.White.copy(alpha = 0.7f),
                )
            ),
            shape = shape,
        )
        .drawWithContent {
            drawContent()
            val r = radius.toPx()
            // Two concentric reflective rims imitate a moulded glass edge.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.73f),
                    0.31f to Color.White.copy(alpha = 0.12f),
                    0.69f to Color(0xFF26594E).copy(alpha = 0.08f),
                    1f to Color.White.copy(alpha = 0.50f),
                ),
                topLeft = Offset(2.2.dp.toPx(), 2.2.dp.toPx()),
                size = Size(
                    (size.width - 4.4.dp.toPx()).coerceAtLeast(0f),
                    (size.height - 4.4.dp.toPx()).coerceAtLeast(0f),
                ),
                cornerRadius = CornerRadius((r - 2.dp.toPx()).coerceAtLeast(0f)),
                style = Stroke(width = 1.15.dp.toPx()),
            )
            // Specular near-top and near-bottom light, clipped inside surface.
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.80f),
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent,
                    )
                ),
                start = Offset(size.width * .10f, 3.6.dp.toPx()),
                end = Offset(size.width * .90f, 3.6.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
            )
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = .18f),
                        Color.White.copy(alpha = .58f),
                        Color.Transparent,
                    )
                ),
                start = Offset(size.width * .18f, size.height - 3.dp.toPx()),
                end = Offset(size.width * .82f, size.height - 3.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
}

/**
 * Unified, opaque modal chrome for all finance forms.
 * max width keeps tablets readable; the host Dialog still handles screen edges.
 * Do not use translucent CRYSTAL for sensitive form content.
 */
internal fun Modifier.jibikDialogSurface(): Modifier =
    this.widthIn(max = 520.dp)
        .liquidGlass(tone = GlassTone.FROSTED, radius = 26.dp, elevation = 11.dp)

/** Shared surface used for structural content cards on all finance screens. */
@Composable
internal fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.CRYSTAL,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(17.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.993f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "jibik-card-press",
    )
    val surface = modifier.fillMaxWidth()
        .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
        .liquidGlass(tone)
    val active = if (onClick == null) surface else surface
        .heightIn(min = 52.dp)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
    Column(
        modifier = active.padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Tactile glass pill; role, size and contrast remain accessible. */
@Composable
internal fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.EMERALD,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "jibik-button-press",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .heightIn(min = 52.dp)
            .liquidGlass(tone = if (enabled) tone else GlassTone.CRYSTAL, radius = 18.dp, elevation = 4.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            color = when {
                !enabled -> Color(0xFF667971)
                tone == GlassTone.EMERALD -> Color.White
                else -> Color(0xFF143E36)
            },
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/**
 * Shared, gently animated progress track for budgets, goals, reporting and
 * installments. Non-finite data must never reach the drawing layer.
 */
internal fun normalizedJibikProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

@Composable
internal fun JibikProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = JibikStyle.forest,
    trackColor: Color = Color(0xFFE6F0E9),
) {
    val target = normalizedJibikProgress(progress)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 420),
        label = "jibik-financial-progress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = trackColor,
    )
}

/** Low-profile glass chips with a clear selected state and an accessible target. */
@Composable
internal fun JibikGlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "jibik-chip-press",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .heightIn(min = 52.dp)
            .liquidGlass(
                tone = if (selected && enabled) GlassTone.EMERALD else GlassTone.CRYSTAL,
                radius = 15.dp,
                elevation = 2.dp,
            )
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "انتخاب‌شده" else "انتخاب‌نشده"
            }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            maxLines = 3,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !enabled -> Color(0xFF788985)
                selected -> Color.White
                else -> Color(0xFF31574F)
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
