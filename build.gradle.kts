import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")

        // v0.1.0 is vault-only (pure javax.crypto) -> no YAML/LSP4IJ
        // dependency. That code still lives in future/v0.2-ansible-lsp/,
        // held out of compilation so verifyPlugin doesn't flag a reference
        // to another plugin's classes without declaring the dependency.
        // See future/v0.2-ansible-lsp/README.md to reactivate it.

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 243 = 2024.3, so as not to exclude the real installed base.
            sinceBuild = "243"
            // No ceiling: a narrow untilBuild is the most common way to die
            // in this marketplace (it only gets disabled on the next release).
            untilBuild = provider { null }
        }
    }

    // The bytecode instrumenter (adds @NotNull/@Nullable asserts at
    // runtime) fails on this Gradle 9.5/plugin 2.16/IDE 2025.2.6.2
    // combination with "instrumentIdeaExtensions doesn't support the
    // nested element" -> a tooling bug documented across several plugin
    // versions, not our code's fault (compileKotlin/compileTestKotlin
    // compile fine). Not required for build/test/verifyPlugin; reactivate
    // if a future plugin version fixes it and the extra runtime check is
    // wanted.
    instrumentCode = false
}
