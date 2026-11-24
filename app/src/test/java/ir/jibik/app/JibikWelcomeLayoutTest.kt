package ir.jibik.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JibikWelcomeLayoutTest {
    @Test fun standardTabletKeepsFullIllustration() {
        assertFalse(compactJibikWelcome(600f, 960f, 1f))
    }

    @Test fun shortPhoneKeepsCallToActionCloserToTop() {
        assertTrue(compactJibikWelcome(412f, 680f, 1f))
    }

    @Test fun narrowPhoneUsesSmallArtwork() {
        assertTrue(compactJibikWelcome(320f, 790f, 1f))
    }

    @Test fun systemLargeTextSwitchesToCompactSpacing() {
        assertTrue(compactJibikWelcome(420f, 830f, 1.5f))
    }
}
