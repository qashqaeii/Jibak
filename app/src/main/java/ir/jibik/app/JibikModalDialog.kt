package ir.jibik.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-width primary actions are specific to a dialog footer; buttons inside
 * the form retain their existing layout.
 */
internal val LocalJibikModalPrimaryAction = compositionLocalOf { false }

/**
 * An editorial, keyboard-aware dialog shell shared by every financial flow.
 *
 * Unlike AlertDialog's FlowRow action strip, the primary action has a stable
 * full-width target and the scrolling form cannot push its footer offscreen.
 * Header, body and footer use the same padding on every page.
 *
 * Form bodies that already own verticalScroll must keep it; adding a second
 * vertical scroller here would create unbounded nested scroll constraints.
 */
@Composable
internal fun JibikModalDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = JibikWideDialogProperties,
    containerColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(26.dp),
    tonalElevation: Dp = 0.dp,
    bodyKey: Any? = Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val availableHeight = if (maxHeight.value.isFinite()) {
                (maxHeight - 16.dp).coerceAtLeast(0.dp)
            } else 720.dp
            val compact = availableHeight < 400.dp
            val stackActions = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.35f
            Surface(
                modifier = modifier.heightIn(max = availableHeight),
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
            ) {
                Column {
                    if (title != null) {
                        Box(
                            Modifier.fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, top = if (compact) 10.dp else 16.dp, bottom = if (compact) 8.dp else 12.dp),
                        ) {
                            title()
                        }
                    }
                    if (text != null) {
                        HorizontalDivider(
                            color = JibikStyle.line.copy(alpha = 0.72f),
                            thickness = 1.dp,
                        )
                        Box(
                            Modifier.fillMaxWidth()
                                .weight(1f, fill = false)
                                .clipToBounds()
                                .padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 12.dp),
                        ) {
                            key(bodyKey) { text() }
                        }
                    }
                    HorizontalDivider(
                        color = JibikStyle.line,
                        thickness = 1.dp,
                    )
                    val footerModifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 10.dp)
                    if (stackActions && dismissButton != null && !compact) {
                        Column(footerModifier, verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            CompositionLocalProvider(LocalJibikModalPrimaryAction provides true) { confirmButton() }
                            dismissButton()
                        }
                    } else {
                        Row(footerModifier, verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dismissButton != null) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                dismissButton()
                            }
                            Box(Modifier.weight(if (dismissButton == null) 1f else 1.6f),
                                contentAlignment = Alignment.Center) {
                                CompositionLocalProvider(LocalJibikModalPrimaryAction provides true) { confirmButton() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Visible, non-interactive scroll cue on the trailing edge. The form remains the only scroll owner. */
@Composable
internal fun Modifier.jibikScrollableBody(state: ScrollState): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this.drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max > 0 && max != Int.MAX_VALUE && size.height > 0f) {
            val thumb = (size.height * size.height / (size.height + max.toFloat()))
                .coerceIn(minOf(18.dp.toPx(), size.height), size.height)
            val start = (size.height - thumb) * (state.value.toFloat() / max).coerceIn(0f, 1f)
            val x = if (rtl) 2.dp.toPx() else size.width - 2.dp.toPx()
            drawLine(JibikStyle.forest.copy(alpha = .28f), Offset(x, start),
                Offset(x, start + thumb), 2.dp.toPx(), StrokeCap.Round)
        }
    }.padding(end = 7.dp).verticalScroll(state)
}
