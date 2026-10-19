package dev.gaphunter.ansiblecompanion.licensing

import dev.gaphunter.ansiblecompanion.completion.CheckLicense

/**
 * Features that moved from the free tier to Pro: a license, or an installation that had them
 * before the move (see [LegacyFreeFeatures]). Fail-closed: only an explicit `true` from the
 * license check counts. Both checks are injectable for tests.
 */
object ProGate {
    @Volatile
    var licenseCheck: () -> Boolean? = { CheckLicense.isLicensed() }

    @Volatile
    var legacyCheck: () -> Boolean = { LegacyFreeFeatures.keepsMovedFeatures() }

    /** The ansible-core 2.20 migration check. */
    fun allowsMovedFeatures(): Boolean = licenseCheck() == true || legacyCheck()
}
