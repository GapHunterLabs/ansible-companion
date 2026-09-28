package dev.gaphunter.ansiblecompanion.completion

import com.intellij.openapi.application.PathManager

/**
 * Local testing only: lets `./gradlew runIde -PdevSandbox=true` open the Pro
 * features without a Marketplace license, which cannot exist for a sandbox IDE.
 *
 * This file is compiled only when the build is started with
 * `-PdevSandbox=true` (see build.gradle.kts). Every other build -- tests,
 * buildPlugin, signPlugin, publishPlugin -- compiles the always-closed version
 * in `src/release/kotlin` instead, so the published plugin never contains it.
 *
 * It needs both the system property that build.gradle.kts passes to runIde
 * and an IDE whose configuration folder is a Gradle sandbox.
 *
 * The sandbox lives under `.intellijPlatform/sandbox/` with the current
 * IntelliJ Platform Gradle Plugin (2.x) and under `idea-sandbox/` with the
 * older one; both count.
 */
object DevSandbox {
    const val PROPERTY = "ansiblecompanion.pro.dev"

    private val SANDBOX_FOLDERS = listOf("/.intellijPlatform/sandbox/", "/idea-sandbox/")

    fun isForced(
        property: String? = System.getProperty(PROPERTY),
        configPath: () -> String = { PathManager.getConfigPath() },
    ): Boolean {
        if (property != "true") return false
        val path = configPath().replace('\\', '/')
        return SANDBOX_FOLDERS.any { path.contains(it) }
    }
}
