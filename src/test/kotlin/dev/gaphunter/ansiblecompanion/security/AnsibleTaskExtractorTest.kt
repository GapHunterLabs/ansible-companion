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
