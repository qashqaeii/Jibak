package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JibikProgressTrackTest {
    @Test fun normalizedValuesKeepTheOriginalRatio() {
        assertEquals(0.45f, normalizedJibikProgress(0.45f), 0.001f)
    }

    @Test fun outOfRangeRatiosAreClamped() {
        assertEquals(0f, normalizedJibikProgress(-1f), 0.001f)
        assertEquals(1f, normalizedJibikProgress(3.5f), 0.001f)
    }

    @Test fun invalidRatiosNeverReachTheCanvas() {
        assertEquals(0f, normalizedJibikProgress(Float.NaN), 0.001f)
        assertEquals(0f, normalizedJibikProgress(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(0f, normalizedJibikProgress(Float.NEGATIVE_INFINITY), 0.001f)
    }
}
