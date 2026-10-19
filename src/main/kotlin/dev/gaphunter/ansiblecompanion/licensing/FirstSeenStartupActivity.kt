package dev.gaphunter.ansiblecompanion.licensing

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Stamps the first day this version ran, for [LegacyFreeFeatures]. Writes once per IDE, never again. */
class FirstSeenStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LegacyFreeFeatures.recordFirstSeen()
    }
}
