package ir.jibik.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JibikEmptyStateLayoutTest {
    @Test fun standardPhoneKeepsCompactIllustrationBesideText() {
        assertFalse(compactJibikStatePanel(340f, 1f))
    }

    @Test fun tightModalStacksArtworkAndExplanation() {
        assertTrue(compactJibikStatePanel(290f, 1f))
    }

    @Test fun accessibilityFontsStackEmptyState() {
        assertTrue(compactJibikStatePanel(400f, 1.5f))
    }
}
