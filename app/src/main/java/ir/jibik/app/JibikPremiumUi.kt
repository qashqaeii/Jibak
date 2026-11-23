package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val cream = Color(0xFFFFFAF5)
    val paper = Color.White
    val muted = Color(0xFF677A78)
    val line = Color(0xFFEFECE6)
    val coral = Color(0xFFE95C50)
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
) {
    val textSize = when {
        amount.length >= 27 -> 19.sp
        amount.length >= 22 -> 22.sp
        amount.length >= 18 -> 25.sp
        else -> 28.sp
    }
    Text(
        text = amount,
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        fontFamily = JibikFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = textSize,
        lineHeight = (textSize.value * 1.36f).sp,
        maxLines = 2,
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

@Composable
internal fun JibikSectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title, color = JibikStyle.ink, fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 23.sp,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle, color = JibikStyle.muted, fontSize = 12.sp,
                    maxLines = 2, lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun JibikQuickAction(
    title: String,
    subtitle: String,
    symbol: String,
    accent: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        padding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 11.dp, vertical = 13.dp,
        ),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.size(39.dp)
                .liquidGlass(
                    tone = if (accent == JibikStyle.coral) GlassTone.ROSE
                    else if (accent == JibikStyle.forest) GlassTone.EMERALD
                    else GlassTone.HONEY,
                    radius = 13.dp,
                    elevation = 4.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                color = if (accent == JibikStyle.forest) Color.White else accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            title, color = JibikStyle.ink, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle, color = JibikStyle.muted, fontSize = 10.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Wide third action on compact devices. The existing three-column layout
 * remains on larger phones, but on 320–390dp screens no action is clipped.
 */
@Composable
internal fun JibikWideQuickAction(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        padding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp, vertical = 12.dp,
        ),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(42.dp).liquidGlass(
                    tone = GlassTone.HONEY, radius = 14.dp, elevation = 3.dp,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(symbol, color = Color(0xFF8C531A),
                    fontWeight = FontWeight.Bold, fontSize = 21.sp)
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, color = JibikStyle.ink, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = JibikStyle.muted, fontSize = 12.sp,
                    maxLines = 2)
            }
            Text("‹", color = JibikStyle.forest, fontSize = 21.sp)
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
@Composable
internal fun JibikStatePanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    JibikSurfaceCard(modifier = modifier) {
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
                    title,
                    color = JibikStyle.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    description,
                    color = JibikStyle.muted,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
            ) { Text(actionLabel) }
        }
    }
}
