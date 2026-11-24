package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single financial label/value layout, responsive to *available* width.
 * At narrow sizes we deliberately stack the amount instead of clipping
 * significant Persian digits, negative signs or units.
 */
internal fun stackJibikFinancialDetail(widthDp: Float, fontScale: Float, valueLength: Int): Boolean =
    widthDp < 300f || fontScale >= 1.35f || valueLength > 26

@Composable
internal fun JibikFinancialDetail(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    valueColor: Color = JibikStyle.ink,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (stackJibikFinancialDetail(maxWidth.value, fontScale, value.length)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = label, color = JibikStyle.muted, fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = if (emphasis) 16.sp else 13.sp,
                    fontWeight = if (emphasis) FontWeight.ExtraBold
                        else FontWeight.SemiBold,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    label, color = JibikStyle.muted, fontSize = 12.sp,
                    lineHeight = 19.sp, modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = if (emphasis) 16.sp else 13.sp,
                    fontWeight = if (emphasis) FontWeight.ExtraBold
                        else FontWeight.SemiBold,
                    modifier = Modifier.weight(1.2f),
                    maxLines = 3,
                    lineHeight = 21.sp,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
