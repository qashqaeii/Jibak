package ir.jibik.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A real single-choice segmented control, not multiple competing raised cards.
 * Its selected state and radio-button role are announced by TalkBack.
 */
@Composable
internal fun <T> JibikSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, JibikStyle.line, RoundedCornerShape(18.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (value, label) ->
            val isSelected = value == selected
            val canSelect = enabled(value)
            val background by animateColorAsState(
                if (isSelected) JibikStyle.forest else Color.Transparent,
                animationSpec = tween(180), label = "segment-background")
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(
                        background,
                        RoundedCornerShape(12.dp),
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        enabled = canSelect,
                        onClick = { onSelected(value) },
                    )
                    .semantics {
                        stateDescription = if (isSelected) "انتخاب‌شده"
                            else "انتخاب‌نشده"
                    }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = when {
                        !canSelect -> JibikStyle.muted
                        isSelected -> Color.White
                        else -> JibikStyle.ink
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
