package ir.jibik.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val backgroundTop = Color(0xFFF4F8F2)
    val backgroundMid = Color(0xFFE9F3E8)
    val backgroundBottom = Color(0xFFFFF3E5)
    val highlight = Color(0xFFFFFFFF)
    val outline = Color(0xFFBED6C9)
}

private fun toneStops(tone: GlassTone): List<Color> = when (tone) {
    GlassTone.CRYSTAL -> listOf(
        Color(0xFFFDFEFF), Color(0xF8F6FCFA), Color(0xF2E8F1EC),
    )
    // Full opacity is intentional for dialogs: finance data behind the modal
    // must never bleed into labels, input borders or validation messages.
    GlassTone.FROSTED -> listOf(
        Color(0xFFFFFFFF), Color(0xFFFBFDFC), Color(0xFFF0F6F2),
    )
    GlassTone.EMERALD -> listOf(
        Color(0xF42D9F83), Color(0xF4087467), Color(0xFF024D46),
    )
    GlassTone.HONEY -> listOf(
        Color(0xFFFFEDAD), Color(0xF9FFC76C), Color(0xF4EBA553),
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
                Color(0xFFC5FFDB).copy(alpha = 0.43f),
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
                Color(0xFFFFE0B6).copy(alpha = 0.42f),
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
            width = 1.2.dp,
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

/** Shared surface used for structural content cards on all finance screens. */
@Composable
internal fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.CRYSTAL,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(17.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = modifier.fillMaxWidth().liquidGlass(tone)
    val active = if (onClick == null) surface else surface.clickable(
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
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .liquidGlass(tone = if (enabled) tone else GlassTone.CRYSTAL, radius = 18.dp, elevation = 5.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Color(0xFF667971)
                tone == GlassTone.EMERALD -> Color.White
                else -> Color(0xFF143E36)
            },
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
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
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .liquidGlass(
                tone = if (selected && enabled) GlassTone.EMERALD else GlassTone.CRYSTAL,
                radius = 15.dp,
                elevation = 2.dp,
            )
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            maxLines = 1,
            color = when {
                !enabled -> Color(0xFF788985)
                selected -> Color.White
                else -> Color(0xFF31574F)
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
