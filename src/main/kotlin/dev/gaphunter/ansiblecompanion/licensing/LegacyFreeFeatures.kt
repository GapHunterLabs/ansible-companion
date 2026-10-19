package dev.gaphunter.ansiblecompanion.licensing

import com.intellij.ide.util.PropertiesComponent
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The ansible-core 2.20 migration check moved from the free tier to Pro in 2026.6.0. An
 * installation that runs 2026.6.0 or later before [CUTOFF] keeps it without a license:
 * [recordFirstSeen] (called from [FirstSeenStartupActivity]) stamps the first day this version
 * ran, and [keepsMovedFeatures] compares that stamp with the cutoff.
 */
object LegacyFreeFeatures {
    /** First day the moved features need a license on an installation stamped from then on. */
    val CUTOFF: LocalDate = LocalDate.of(2026, 11, 2)

    private const val FIRST_SEEN_KEY = "dev.gaphunter.ansiblecompanion.firstSeen"

    fun recordFirstSeen(today: LocalDate = LocalDate.now()) {
        val properties = PropertiesComponent.getInstance()
        if (properties.getValue(FIRST_SEEN_KEY) == null) properties.setValue(FIRST_SEEN_KEY, today.toString())
    }

    fun keepsMovedFeatures(): Boolean = keeps(PropertiesComponent.getInstance().getValue(FIRST_SEEN_KEY))

    /** The decision itself, testable without the platform. An unreadable stamp counts as none. */
    fun keeps(firstSeen: String?): Boolean {
        val date = try {
            firstSeen?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        } ?: return false
        return date.isBefore(CUTOFF)
    }
}
