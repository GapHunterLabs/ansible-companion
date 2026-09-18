package dev.gaphunter.ansiblecompanion.security

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Exercises the real production pipeline (`computeFindings`) against the
 * EXACT content of `demo/roles/webserver/tasks/security_examples.yml` --
 * this is the automated equivalent of the manual `runIde` visual check
 * from the plan's Definition of Done, run headless (no screenshot, no
 * `AnnotationHolder`/`LicensingFacade` needed, per
 * [AnsibleSecurityAnnotator.computeFindings]'s own doc comment). If this
 * file and the demo fixture ever drift apart, update both together.
 */
class AnsibleSecurityAnnotatorTest : BasePlatformTestCase() {

    private val fixtureContent = """
        ---
        - name: set app user password (missing no_log)
          ansible.builtin.user:
            name: appuser
            password: "{{ vault_app_password }}"

        - name: load secrets (missing no_log on vault-shaped include)
          ansible.builtin.include_vars:
            file: secrets/prod.vault.yml

        - name: insecure download (validate_certs disabled)
          ansible.builtin.get_url:
            url: https://internal.example.com/artifact.tar.gz
            dest: /tmp/artifact.tar.gz
            validate_certs: false

        - name: password set inside a protected block
          block:
            - name: set db password (protected by block-level no_log)
              community.mysql.mysql_user:
                name: dbuser
                password: "{{ vault_db_password }}"
          no_log: true
    """.trimIndent()

    private fun findTaskNamed(taskName: String): YAMLSequenceItem {
        val file = myFixture.configureByText("main.yml", fixtureContent)
        val items = PsiTreeUtil.findChildrenOfType(file, YAMLSequenceItem::class.java)
        return items.first { item ->
            item.keysValues.any { it.keyText == "name" && (it.value as? YAMLScalar)?.textValue == taskName }
        }
    }

    fun testFlagsPasswordWithoutNoLog() {
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("set app user password (missing no_log)"),
            isBareRoleTasksFile = true,
        )
        assertNotNull("real runIde equivalent: this must render a warning squiggle", result)
        assertEquals(1, result!!.second.size)
        assertEquals("NO_LOG_PASSWORD", result.second.single().kind)
    }

    fun testFlagsVaultShapedIncludeVarsWithoutNoLog() {
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("load secrets (missing no_log on vault-shaped include)"),
            isBareRoleTasksFile = true,
        )
        assertNotNull(result)
        assertEquals(1, result!!.second.size)
    }

    fun testFlagsValidateCertsFalse() {
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("insecure download (validate_certs disabled)"),
            isBareRoleTasksFile = true,
        )
        assertNotNull(result)
        assertEquals(1, result!!.second.size)
    }

    fun testDoesNotFlagPasswordProtectedByEnclosingBlockNoLog() {
        // The most important fixture case: a real false-positive here would
        // train users to ignore the feature entirely -- see the plan's own
        // Definition of Done and "Riesgos técnicos" section.
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("set db password (protected by block-level no_log)"),
            isBareRoleTasksFile = true,
        )
        assertNull("password inside a block: ... no_log: true must NOT be flagged", result)
    }

    fun testGroupingConstructItselfProducesNoFindings() {
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("password set inside a protected block"),
            isBareRoleTasksFile = true,
        )
        assertNull("a block: task has no module key, nothing to anchor a finding to", result)
    }

    fun testNotBareRoleTasksFileAndNoWrappingKeyMeansNotTaskPositioned() {
        // Same fixture, but as if it were e.g. a stray top-level sequence in
        // a non-tasks/non-handlers file -- must not be treated as tasks.
        val result = AnsibleSecurityAnnotator().computeFindings(
            findTaskNamed("set app user password (missing no_log)"),
            isBareRoleTasksFile = false,
        )
        assertNull(result)
    }
}
