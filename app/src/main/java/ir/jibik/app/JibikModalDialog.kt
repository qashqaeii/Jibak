package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
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
                (maxHeight - 16.dp).coerceAtLeast(160.dp)
            } else 720.dp
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
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
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
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            text()
                        }
                    }
                    HorizontalDivider(
                        color = JibikStyle.line,
                        thickness = 1.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (dismissButton != null) {
                            Box(
                                modifier = Modifier.weight(0.9f),
                                contentAlignment = Alignment.Center,
                            ) {
                                dismissButton()
                            }
                        }
                        Box(
                            modifier = Modifier.weight(if (dismissButton != null) 1.55f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalJibikModalPrimaryAction provides true) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}
