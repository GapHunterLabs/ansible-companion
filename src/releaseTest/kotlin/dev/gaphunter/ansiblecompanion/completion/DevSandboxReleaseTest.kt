package dev.gaphunter.ansiblecompanion.completion

import org.junit.Assert.assertFalse
import org.junit.Test

class DevSandboxReleaseTest {

    @Test
    fun `a release build never opens Pro without a license`() {
        assertFalse(DevSandbox.isForced())
    }

    @Test
    fun `the release license check is the real one`() {
        // Outside an IDE there is no LicensingFacade: the real check can only say "not licensed".
        val answer = try {
            CheckLicense.isLicensed()
        } catch (e: Throwable) {
            null
        }
        assertFalse(answer == true)
    }
}
