package dev.gaphunter.ansiblecompanion.licensing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFreeFeaturesTest {
    @Test
    fun `the stamp only counts before the cutoff`() {
        assertTrue(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF.minusDays(1).toString()))
        assertFalse(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF.toString()))
    }

    @Test
    fun `no stamp or an unreadable one counts as a new installation`() {
        assertFalse(LegacyFreeFeatures.keeps(null))
        assertFalse(LegacyFreeFeatures.keeps("not a date"))
    }
}
