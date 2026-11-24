package ir.jibik.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure responsive rule: safe to run in JVM CI without an emulator. */
class JibikResponsiveLayoutTest {
    @Test fun wideNormalFont_keepsHeaderActionInline() {
        assertFalse(stackJibikSectionAction(360f, 1.0f))
    }

    @Test fun narrowScreen_stacksActionInsteadOfCrushingHeading() {
        assertTrue(stackJibikSectionAction(328f, 1.0f))
    }

    @Test fun largeSystemFont_stacksActionEvenWhenWidthIsWide() {
        assertTrue(stackJibikSectionAction(410f, 1.5f))
    }

    @Test fun thresholdIsStable() {
        assertFalse(stackJibikSectionAction(330f, 1.34f))
        assertTrue(stackJibikSectionAction(330f, 1.35f))
    }
}
