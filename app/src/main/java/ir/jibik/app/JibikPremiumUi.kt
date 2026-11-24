package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Shared visual foundation for the Jibik Android app. Geometry and gradients
 * are rendered by Compose; the mascot and brand logo remain transparent WebP.
 */
internal object JibikStyle {
    val ink = Color(0xFF102D31)
    val forest = Color(0xFF07574C)
    val forestDeep = Color(0xFF02443B)
    val mint = Color(0xFF21C79B)
    val orange = Color(0xFFFF8A1F)
    val warningText = Color(0xFFA75A09)
    val cream = Color(0xFFFFFAF5)
    val paper = Color.White
    val muted = Color(0xFF5D716F)
    val line = Color(0xFFEFECE6)
    val coral = Color(0xFFC44238)
    val greenSurface = Color(0xFFE9F6EF)
    val orangeSurface = Color(0xFFFFF0DD)
}


/**
 * Large totals must remain legible on 360dp devices and at high font scale.
 * Prefer a second line and smaller text over silently clipping money.
 */
@Composable
internal fun JibikHeroAmount(
    amount: String,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = amount,
) {
    val textSize = when {
        amount.length >= 27 -> 19.sp
        amount.length >= 22 -> 22.sp
        amount.length >= 18 -> 25.sp
        else -> 28.sp
    }
    Text(
        text = amount,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = accessibilityLabel },
        color = Color.White,
        fontFamily = JibikFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = textSize,
        lineHeight = (textSize.value * 1.36f).sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Readable high-contrast brand hero with quiet, vector-only decoration. */
@Composable
internal fun JibikHero(
    modifier: Modifier = Modifier,
    illustration: JiboArtwork? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            tone = GlassTone.EMERALD,
            radius = 27.dp,
            elevation = 14.dp,
        ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.065f),
                radius = size.width * 0.43f,
                center = Offset(size.width * 0.95f, -size.height * 0.05f),
            )
            drawCircle(
                color = JibikStyle.mint.copy(alpha = 0.09f),
                radius = size.width * 0.27f,
                center = Offset(size.width * 0.08f, size.height * 1.03f),
            )
        }
        illustration?.let { art ->
            // Actual 3D transparent WebP art: visible enough to recognize, still secondary to totals.
            JiboArt(
                illustration = art,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .size(118.dp).alpha(0.32f),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Shared top-level heading: same typography, spacing and brand placement on
 * home-adjacent pages. Back navigation is an explicit touch target, not a glyph
 * embedded in decorative text. Natural text wrapping supports font scaling.
 */
@Composable
private fun JibikPageTitleCopy(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title, color = JibikStyle.ink,
            fontSize = 24.sp, lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            subtitle, color = JibikStyle.muted,
            fontSize = 13.sp, lineHeight = 20.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JibikPageBackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("بازگشت", color = JibikStyle.forest,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
internal fun JibikPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (action != null && stackJibikSectionAction(maxWidth.value, fontScale)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JibikPageTitleCopy(title, subtitle, Modifier.weight(1f))
                    if (onBack != null) JibikPageBackButton(onBack)
                    else JibikBrandLogo(Modifier.size(44.dp))
                }
                Box(Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JibikPageTitleCopy(title, subtitle, Modifier.weight(1f))
                when {
                    action != null -> action()
                    onBack != null -> JibikPageBackButton(onBack)
                    else -> JibikBrandLogo(Modifier.size(44.dp))
                }
            }
        }
    }
}

/** Quiet status marker with an accessible text label; no decorative badge frame. */
@Composable
internal fun JibikStatusText(
    text: String,
    color: Color = JibikStyle.forest,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text, color = color, fontSize = 12.sp,
            lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

/** Shared readable title for all new and refactored finance dialogs. */
@Composable
internal fun JibikDialogHeading(
    title: String,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JibikBrandLogo(Modifier.size(38.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title, color = JibikStyle.ink,
                fontSize = 19.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, color = JibikStyle.muted,
                    fontSize = 12.sp, lineHeight = 18.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Section actions move below the heading only when space or text scaling demands it. */
internal fun stackJibikSectionAction(availableWidthDp: Float, fontScale: Float): Boolean =
    availableWidthDp < 330f || fontScale >= 1.35f

@Composable
private fun JibikSectionCopy(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title, color = JibikStyle.ink, fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                subtitle, color = JibikStyle.muted, fontSize = 12.sp,
                maxLines = 3, lineHeight = 19.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun JibikSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (trailing != null && stackJibikSectionAction(maxWidth.value, fontScale)) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                JibikSectionCopy(title, subtitle)
                Box(Modifier.align(Alignment.End)) { trailing() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JibikSectionCopy(title, subtitle, Modifier.weight(1f))
                trailing?.invoke()
            }
        }
    }
}

/**
 * Three-dimensional illustrations live in transparent WebP resources. Keep
 * action imagery free of badge frames; let the card itself be the hit target.
 */
@Composable
internal fun JibikQuickAction(
    title: String,
    subtitle: String,
    artwork: JiboArtwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        padding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp, vertical = 12.dp,
        ),
        onClick = onClick,
    ) {
        JiboArt(
            illustration = artwork,
            modifier = Modifier.size(54.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            title,
            color = JibikStyle.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            lineHeight = 20.sp,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = JibikStyle.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            lineHeight = 19.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A full-width action for compact phones, with the same illustration system. */
@Composable
internal fun JibikWideQuickAction(
    title: String,
    subtitle: String,
    artwork: JiboArtwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        padding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp, vertical = 10.dp,
        ),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JiboArt(artwork, Modifier.size(54.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title, color = JibikStyle.ink, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    subtitle, color = JibikStyle.muted, fontSize = 12.sp,
                    maxLines = 2, lineHeight = 18.sp,
                )
            }
            JibikChevronIcon(color = JibikStyle.forest)
        }
    }
}

/** A small, reusable financial surface; prevents page-to-page card drift. */
@Composable
internal fun JibikSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        content = content,
    )
}

/** A consistent recovery/empty state with a real action, not a dead placeholder. */
internal fun compactJibikStatePanel(widthDp: Float, fontScale: Float): Boolean =
    widthDp < 320f || fontScale >= 1.4f

/** The same accessible empty/recovery state across every financial workspace. */
@Composable
internal fun JibikStatePanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    JibikSurfaceCard(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (compactJibikStatePanel(maxWidth.value, fontScale)) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    JiboArt(JiboArtwork.EMPTY, Modifier.size(64.dp))
                    Text(
                        title, color = JibikStyle.ink,
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center, lineHeight = 24.sp,
                    )
                    Text(
                        description, color = JibikStyle.muted,
                        fontSize = 13.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JiboArt(JiboArtwork.EMPTY, Modifier.size(75.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            title, color = JibikStyle.ink,
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = 24.sp,
                        )
                        Text(
                            description, color = JibikStyle.muted,
                            fontSize = 13.sp, lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            LiquidGlassButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                tone = GlassTone.EMERALD,
            )
        }
    }
}
