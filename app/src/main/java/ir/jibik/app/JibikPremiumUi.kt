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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** Readable high-contrast brand hero with quiet, vector-only decoration. */
@Composable
internal fun JibikHero(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    listOf(JibikStyle.forest, JibikStyle.forestDeep),
                    start = Offset.Zero,
                    end = Offset(900f, 650f),
                ),
                shape = shape,
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
            Text(title, color = JibikStyle.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(
                    subtitle, color = JibikStyle.muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = JibikStyle.paper),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier.size(35.dp).background(background, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(symbol, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
}

/** A small, reusable financial surface; prevents page-to-page card drift. */
@Composable
internal fun JibikSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = JibikStyle.paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
