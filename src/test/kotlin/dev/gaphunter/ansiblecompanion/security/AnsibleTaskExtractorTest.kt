package dev.gaphunter.ansiblecompanion.security

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Real PSI (BasePlatformTestCase), not hand-built structures -- this is
 * the ONE file in the `security` package that touches YAML PSI, so it's
 * also the one that needs real-fixture coverage, same discipline as
 * AnsibleRoleGotoDeclarationHandlerTest/YamlKeyPositionDetectorTest.
 */
class AnsibleTaskExtractorTest : BasePlatformTestCase() {

    /** All top-level-or-nested YAMLSequenceItems whose own key-values include a `name:` matching [taskName]. */
    private fun findTaskNamed(file: com.intellij.psi.PsiFile, taskName: String): YAMLSequenceItem {
        val items = PsiTreeUtil.findChildrenOfType(file, YAMLSequenceItem::class.java)
        return items.first { item ->
            item.keysValues.any { it.keyText == "name" && it.value?.let { v -> (v as? org.jetbrains.yaml.psi.YAMLScalar)?.textValue } == taskName }
        }
    }

    fun testExtractsModuleNameAndParameters() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: set app password
              ansible.builtin.user:
                name: appuser
                password: "{{ vault_app_password }}"
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "set app password"))

        assertEquals("ansible.builtin.user", task.moduleName)
        assertEquals("appuser", task.parameters["name"])
        assertEquals("{{ vault_app_password }}", task.parameters["password"])
        assertFalse(task.hasNoLog)
        assertFalse(task.isGroupingConstruct)
    }

    fun testExtractsNoLogOnTaskItself() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: set app password
              ansible.builtin.user:
                password: "{{ vault_app_password }}"
              no_log: true
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "set app password"))
        assertTrue(task.hasNoLog)
    }

    fun testRecognizesNoLogYesAsTrueToo() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: legacy style
              ansible.builtin.debug:
                msg: hi
              no_log: yes
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "legacy style"))
        assertTrue(task.hasNoLog)
    }

    fun testReturnsNullModuleNameForAmbiguousTask() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: two candidates
              copy:
                src: a
              template:
                src: b
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "two candidates"))
        assertNull(task.moduleName)
        assertTrue(task.parameters.isEmpty())
    }

    fun testIdentifiesGroupingConstruct() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: a block
              block:
                - name: inner
                  ansible.builtin.debug:
                    msg: hi
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "a block"))
        assertTrue(task.isGroupingConstruct)
        assertNull(task.moduleName)
    }

    fun testOmitsNonScalarParametersInsteadOfGuessing() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: with a nested arg
              ansible.builtin.copy:
                src: a
                dest: b
                owner:
                  name: root
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "with a nested arg"))
        assertEquals("a", task.parameters["src"])
        assertEquals("b", task.parameters["dest"])
        assertFalse("a nested mapping value should be omitted, not stringified", task.parameters.containsKey("owner"))
    }

    fun testCapturesFreeFormModuleValue() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: free form shell
              ansible.builtin.shell: cat /var/log/app.log | grep ERROR
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "free form shell"))
        assertEquals("ansible.builtin.shell", task.moduleName)
        assertEquals("cat /var/log/app.log | grep ERROR", task.moduleFreeForm)
        assertTrue(task.parameters.isEmpty())
    }

    fun testCapturesBlockScalarFreeFormWithoutTheYamlIndicator() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: block shell
              ansible.builtin.shell: |
                set -o pipefail
                cat a | grep b
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "block shell"))
        val freeForm = task.moduleFreeForm!!
        assertFalse("the YAML block indicator is syntax, not command text", freeForm.trimStart().startsWith("|"))
        assertTrue(freeForm.contains("set -o pipefail"))
        assertTrue(freeForm.contains("cat a | grep b"))
    }

    fun testModuleFreeFormIsNullForAMappingValue() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: mapping form
              ansible.builtin.copy:
                src: a
                dest: b
            """.trimIndent(),
        )
        assertNull(AnsibleTaskExtractor.extract(findTaskNamed(file, "mapping form")).moduleFreeForm)
    }

    fun testDistinguishesQuotedFromUnquotedParameters() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: modes
              ansible.builtin.file:
                path: /srv/app
                mode: 755
                owner: "app"
                group: 'app'
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "modes"))
        assertEquals("755", task.parameters["mode"])
        assertTrue("mode" in task.unquotedParameters)
        assertTrue("path" in task.unquotedParameters)
        assertFalse("double-quoted must not count as unquoted", "owner" in task.unquotedParameters)
        assertFalse("single-quoted must not count as unquoted", "group" in task.unquotedParameters)
        assertEquals("app", task.parameters["owner"])
    }

    fun testMergesTaskLevelArgsWithInlineArgumentsWinning() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: with args
              ansible.builtin.get_url:
                url: https://example.com/a
                dest: /tmp/inline
              args:
                validate_certs: false
                dest: /tmp/from-args
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "with args"))
        assertEquals("ansible.builtin.get_url", task.moduleName)
        assertEquals("false", task.parameters["validate_certs"])
        assertEquals("/tmp/inline", task.parameters["dest"])
    }

    fun testTaskLevelArgsReachFreeFormModules() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: free form with args
              ansible.builtin.shell: Get-Process | Select-Object Name
              args:
                executable: /usr/bin/pwsh
            """.trimIndent(),
        )
        val task = AnsibleTaskExtractor.extract(findTaskNamed(file, "free form with args"))
        assertEquals("/usr/bin/pwsh", task.parameters["executable"])
        assertEquals("Get-Process | Select-Object Name", task.moduleFreeForm)
    }

    fun testCapturesIgnoreErrors() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: tolerant
              ansible.builtin.shell: cat a | grep b
              ignore_errors: yes
            - name: strict
              ansible.builtin.shell: cat a | grep b
            """.trimIndent(),
        )
        assertTrue(AnsibleTaskExtractor.extract(findTaskNamed(file, "tolerant")).ignoresErrors)
        assertFalse(AnsibleTaskExtractor.extract(findTaskNamed(file, "strict")).ignoresErrors)
    }

    fun testResolveNoLogInherited_trueWhenProtectedByEnclosingBlock() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: protected
              block:
                - name: set db password
                  community.mysql.mysql_user:
                    name: dbuser
                    password: "{{ vault_db_password }}"
              no_log: true
            """.trimIndent(),
        )
        val innerTask = findTaskNamed(file, "set db password")
        assertTrue(AnsibleTaskExtractor.resolveNoLogInherited(innerTask))
    }

    fun testResolveNoLogInherited_falseWhenBlockHasNoNoLog() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: unprotected
              block:
                - name: set db password
                  community.mysql.mysql_user:
                    name: dbuser
                    password: "{{ vault_db_password }}"
            """.trimIndent(),
        )
        val innerTask = findTaskNamed(file, "set db password")
        assertFalse(AnsibleTaskExtractor.resolveNoLogInherited(innerTask))
    }

    fun testResolveNoLogInherited_falseForATopLevelTaskWithNoEnclosingBlock() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: set db password
              community.mysql.mysql_user:
                name: dbuser
                password: "{{ vault_db_password }}"
            """.trimIndent(),
        )
        val task = findTaskNamed(file, "set db password")
        assertFalse(AnsibleTaskExtractor.resolveNoLogInherited(task))
    }

    fun testResolveNoLogInherited_walksTwoLevelsOfNestedBlocks() {
        val file = myFixture.configureByText(
            "main.yml",
            """
            - name: outer
              block:
                - name: inner protected
                  block:
                    - name: set db password
                      community.mysql.mysql_user:
                        name: dbuser
                        password: "{{ vault_db_password }}"
              no_log: true
            """.trimIndent(),
        )
        val innerTask = findTaskNamed(file, "set db password")
        assertTrue(AnsibleTaskExtractor.resolveNoLogInherited(innerTask))
    }
}
