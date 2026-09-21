package dev.gaphunter.ansiblecompanion.security

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.io.File

/**
 * Exercises the real production pipeline (`computeFindings`) against the
 * demo fixture file itself, read from disk -- the headless equivalent of
 * opening `demo/roles/webserver/tasks/security_examples.yml` in `runIde`
 * and looking at the warnings, with no copy of its content to drift out
 * of sync. No `AnnotationHolder`/`LicensingFacade` needed, per
 * [AnsibleSecurityAnnotator.computeFindings]'s own doc comment.
 */
class AnsibleSecurityAnnotatorTest : BasePlatformTestCase() {

    private val demoFixture = File("demo/roles/webserver/tasks/security_examples.yml")

    private fun loadDemoFixture(): PsiFile {
        assertTrue(
            "demo fixture not found at ${demoFixture.absolutePath} (working dir: ${System.getProperty("user.dir")})",
            demoFixture.isFile,
        )
        return myFixture.configureByText("main.yml", demoFixture.readText())
    }

    private fun findTaskNamed(file: PsiFile, taskName: String): YAMLSequenceItem {
        val items = PsiTreeUtil.findChildrenOfType(file, YAMLSequenceItem::class.java)
        return items.firstOrNull { item ->
            item.keysValues.any { it.keyText == "name" && (it.value as? YAMLScalar)?.textValue == taskName }
        } ?: error("no task named '$taskName' in the demo fixture")
    }

    private fun findingsFor(taskName: String, isBareRoleTasksFile: Boolean = true): List<SecurityFinding>? =
        AnsibleSecurityAnnotator().computeFindings(findTaskNamed(loadDemoFixture(), taskName), isBareRoleTasksFile)?.second

    private fun assertSingleFinding(taskName: String, expectedKind: String) {
        val findings = findingsFor(taskName)
        assertNotNull("'$taskName' must render a warning", findings)
        assertEquals("'$taskName' should show exactly one warning", listOf(expectedKind), findings!!.map { it.kind })
    }

    private fun assertNoFinding(taskName: String) {
        assertNull("'$taskName' must NOT be flagged", findingsFor(taskName))
    }

    fun testSecretExposurePositives() {
        assertSingleFinding("set app user password (missing no_log)", "NO_LOG_PASSWORD")
        assertSingleFinding("load secrets (missing no_log on vault-shaped include)", "NO_LOG_VAULT_INCLUDE")
        assertSingleFinding("load secrets, free-form style (missing no_log)", "NO_LOG_VAULT_INCLUDE")
        assertSingleFinding("insecure download (validate_certs disabled)", "VALIDATE_CERTS_DISABLED")
    }

    fun testDoesNotFlagPasswordProtectedByEnclosingBlockNoLog() {
        // The most important fixture case: a real false-positive here would
        // train users to ignore the feature entirely.
        assertNoFinding("set db password (protected by block-level no_log)")
    }

    fun testGroupingConstructItselfProducesNoFindings() {
        assertNoFinding("password set inside a protected block")
    }

    fun testFileAndShellHygienePositives() {
        assertSingleFinding("create config without explicit mode", "RISKY_FILE_PERMISSIONS")
        assertSingleFinding("decimal mode that looks octal", "RISKY_OCTAL")
        assertSingleFinding("pipeline without pipefail", "RISKY_SHELL_PIPE")
    }

    fun testFileAndShellHygieneFalsePositiveTraps() {
        assertNoFinding("fix ownership of an existing file (NOT flagged, default state creates nothing)")
        assertNoFinding("correctly quoted octal mode (NOT flagged)")
        assertNoFinding("Jinja filter is not a shell pipe (NOT flagged)")
    }

    fun testNotBareRoleTasksFileAndNoWrappingKeyMeansNotTaskPositioned() {
        // Same fixture, but as if it were a stray top-level sequence in a
        // non-tasks/non-handlers file -- must not be treated as tasks.
        assertNull(findingsFor("set app user password (missing no_log)", isBareRoleTasksFile = false))
    }

    fun testReportsEveryFindingWhenOneTaskHasSeveralProblems() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: two problems
              ansible.builtin.get_url:
                url: https://internal.example.com/a.tar.gz
                dest: /tmp/a.tar.gz
                validate_certs: false
            """.trimIndent(),
        )
        val findings = AnsibleSecurityAnnotator().computeFindings(findTaskNamed(file, "two problems"), isBareRoleTasksFile = true)
        assertNotNull(findings)
        assertEquals(
            setOf("VALIDATE_CERTS_DISABLED", "RISKY_FILE_PERMISSIONS"),
            findings!!.second.map { it.kind }.toSet(),
        )
    }
}
