package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoLogVaultIncludeDetectorTest {

    private fun task(moduleName: String?, parameters: Map<String, String> = emptyMap(), hasNoLog: Boolean = false) =
        AnsibleTask(moduleName, parameters, hasNoLog, isGroupingConstruct = false)

    @Test
    fun flagsVaultShapedFileWithoutNoLog() {
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.include_vars", mapOf("file" to "secrets/prod.vault.yml")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun flagsSecretShapedFileToo() {
        val finding = NoLogVaultIncludeDetector.check(
            task("include_vars", mapOf("file" to "config/secret_tokens.yml")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun doesNotFlagNonVaultShapedFileName() {
        // Known false-negative by design -- name-based heuristic, documented limitation.
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.include_vars", mapOf("file" to "credentials.yml")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenNoLogSet() {
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.include_vars", mapOf("file" to "secrets/vault.yml"), hasNoLog = true),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenNoLogInherited() {
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.include_vars", mapOf("file" to "secrets/vault.yml")),
            noLogInherited = true,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagUnrelatedModule() {
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.copy", mapOf("file" to "secrets/vault.yml")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun flagsFreeFormIncludeVars() {
        val finding = NoLogVaultIncludeDetector.check(
            AnsibleTask("include_vars", emptyMap(), hasNoLog = false, isGroupingConstruct = false, moduleFreeForm = "secrets/prod.vault.yml"),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun doesNotFlagFreeFormIncludeVarsProtectedByNoLog() {
        val finding = NoLogVaultIncludeDetector.check(
            AnsibleTask("include_vars", emptyMap(), hasNoLog = true, isGroupingConstruct = false, moduleFreeForm = "secrets/prod.vault.yml"),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenNoFileOrDirParam() {
        val finding = NoLogVaultIncludeDetector.check(
            task("ansible.builtin.include_vars", mapOf("name" to "x")),
            noLogInherited = false,
        )
        assertNull(finding)
    }
}
