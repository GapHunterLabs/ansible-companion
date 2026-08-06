import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

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

        // v0.2.0 adds FQCN-aware completion + Jinja2 highlighting, both
        // built on top of the bundled YAML plugin's PSI (YAMLKeyValue,
        // AnsibleYamlFileType extends YAMLFileType). See
        // future/v0.2-ansible-completion/README.md for the full history.
        bundledPlugin("org.jetbrains.plugins.yaml")

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

    // Catch experimental/internal API usage locally, before Marketplace's
    // own verifier flags it post-upload.
    //
    // EXPERIMENTAL_API_USAGES deliberately NOT included: this plugin's
    // Ansible file-type detection (AnsibleFileTypeOverrider) has no
    // non-experimental alternative -- confirmed 2026-08-02 via a real
    // verifyPlugin run flagging com.intellij.openapi.fileTypes.impl.FileTypeOverrider
    // itself as `@ApiStatus.Experimental`. The only other mechanism for
    // content-based FileType detection, FileTypeIdentifiableByVirtualFile
    // (used by nginx-companion), doesn't fit here: Ansible files need to
    // stay recognized as YAML (same Language, different FileType) for the
    // bundled YAML plugin's PSI/completion to keep working, whereas
    // FileTypeIdentifiableByVirtualFile fully replaces the FileType with
    // no such constraint. Accepting the Experimental-API risk is a
    // conscious tradeoff, not an oversight -- see KNOWN_ISSUES.md.
    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )
    }

    // Publish token read from a LOCAL, non-repo Gradle property
    // (~/.gradle/gradle.properties, never committed) -- never hardcoded
    // here. Falls back to null (task fails loudly asking for the token)
    // if that file doesn't define it, rather than silently no-op-ing.
    publishing {
        token.set(providers.gradleProperty("gapHunterLabs.marketplace.token"))
    }

    // Same pattern: signing material lives only in the local, non-repo
    // gradle.properties (self-signed cert generated once for the whole
    // catalog, 10-year validity).
    signing {
        certificateChain.set(providers.gradleProperty("gapHunterLabs.marketplace.certificateChain"))
        privateKey.set(providers.gradleProperty("gapHunterLabs.marketplace.privateKey"))
        password.set(providers.gradleProperty("gapHunterLabs.marketplace.privateKeyPassword"))
    }
}
