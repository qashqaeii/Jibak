package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Isolated UI fixture: no real accounts, ledger or SMS are opened. */
class JibikModalLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longFormFooterRemainsVisibleAndShortModeResetsScroll() {
        compose.setContent {
            MaterialTheme(typography = JibikTypography) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var longMode by remember { mutableStateOf(true) }
                    JibikModalDialog(
                        onDismissRequest = {},
                        modifier = Modifier.jibikDialogSurface(),
                        properties = JibikWideDialogProperties,
                        bodyKey = longMode,
                        title = { JibikDialogHeading(if (longMode) "فرم بلند" else "مدیریت تکرارها") },
                        text = {
                            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (longMode) {
                                    repeat(35) { index ->
                                        Text("ردیف آزمون " + index, modifier = Modifier.heightIn(min = 48.dp))
                                    }
                                } else Text("هنوز تکراری نداری")
                            }
                        },
                        confirmButton = { LiquidGlassButton(if (longMode) "مدیریت" else "بازگشت",
                            onClick = { longMode = !longMode }) },
                    )
                }
            }
        }
        compose.onNodeWithText("مدیریت").assertIsDisplayed()
        compose.onNodeWithText("ردیف آزمون 34").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("مدیریت").assertIsDisplayed().performClick()
        compose.onNodeWithText("مدیریت تکرارها").assertIsDisplayed()
        compose.onNodeWithText("هنوز تکراری نداری").assertIsDisplayed()
        compose.onNodeWithText("بازگشت").assertIsDisplayed()
        compose.onNodeWithText("بازگشت").performClick()
        compose.onNodeWithText("ردیف آزمون 0").assertIsDisplayed()
    }
}
