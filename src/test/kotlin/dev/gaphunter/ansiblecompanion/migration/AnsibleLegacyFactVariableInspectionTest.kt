package dev.gaphunter.ansiblecompanion.migration

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** The migration inspection through the real highlighting pipeline, including its quick fix. */
class AnsibleLegacyFactVariableInspectionTest : BasePlatformTestCase() {

    private val demoFixture = File("demo/roles/webserver/tasks/facts_migration_examples.yml")

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AnsibleLegacyFactVariableInspection::class.java)
    }

    private fun highlights(text: String): List<HighlightInfo> {
        myFixture.configureByText("main.yml", text)
        return myFixture.doHighlighting().filter { it.description?.contains("top-level fact variable") == true }.sortedBy { it.startOffset }
    }

    private fun flaggedNames(text: String): List<String> {
        val infos = highlights(text) // configures the file, so the editor exists only after this call
        val doc = myFixture.editor.document
        return infos.map { doc.getText(com.intellij.openapi.util.TextRange(it.startOffset, it.endOffset)) }
    }

    fun testTheDemoFixtureIsReportedExactlyWhereExpected() {
        assertTrue("fixture not found at ${demoFixture.absolutePath}", demoFixture.isFile)
        val found = flaggedNames(demoFixture.readText())
        assertEquals(
            listOf("ansible_os_family", "ansible_hostname", "ansible_default_ipv4", "ansible_distribution", "ansible_distribution_major_version"),
            found,
        )
    }

    fun testItIsAWeakWarningNotAnError() {
        val info = highlights("- name: a\n  ansible.builtin.debug:\n    msg: \"{{ ansible_hostname }}\"\n  when: true\n").single()
        assertEquals(HighlightSeverity.WEAK_WARNING, info.severity)
    }

    fun testANonAnsibleYamlFileIsLeftAlone() {
        // A Kubernetes-looking file that happens to mention the word: not an Ansible file, nothing reported.
        myFixture.configureByText("deployment.yml", "apiVersion: v1\nkind: ConfigMap\ndata:\n  note: \"{{ ansible_hostname }}\"\n")
        val found = myFixture.doHighlighting().filter { it.description?.contains("top-level fact variable") == true }
        assertTrue(found.toString(), found.isEmpty())
    }

    fun testTheQuickFixRewritesABareWhenExpression() {
        myFixture.configureByText("main.yml", "- name: a\n  ansible.builtin.debug:\n    msg: hi\n  when: ansible_os_family == \"Debian\"\n")
        applyFixAtFirstHit("Replace 'ansible_os_family' with 'ansible_facts.os_family'")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("when: ansible_facts.os_family == \"Debian\""))
    }

    fun testTheQuickFixKeepsTheQuotingOfASingleQuotedString() {
        myFixture.configureByText("main.yml", "- name: a\n  ansible.builtin.debug:\n    msg: '{{ ansible_hostname }}-{{ ansible_hostname }}'\n  when: true\n")
        applyFixAtFirstHit("Replace 'ansible_hostname' with 'ansible_facts.hostname'")
        val text = myFixture.editor.document.text
        // Both uses in the value are rewritten by the one fix, and the YAML quoting is untouched.
        assertTrue(text, text.contains("msg: '{{ ansible_facts.hostname }}-{{ ansible_facts.hostname }}'"))
    }

    fun testTheQuickFixKeepsTheRestOfTheExpression() {
        myFixture.configureByText("main.yml", "- name: a\n  ansible.builtin.debug:\n    msg: \"{{ ansible_default_ipv4.address }}\"\n  when: true\n")
        applyFixAtFirstHit("Replace 'ansible_default_ipv4' with 'ansible_facts.default_ipv4'")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("\"{{ ansible_facts.default_ipv4.address }}\""))
    }

    fun testAfterTheFixNothingIsReportedAnymore() {
        myFixture.configureByText("main.yml", "- name: a\n  ansible.builtin.debug:\n    msg: hi\n  when: ansible_os_family == \"Debian\"\n")
        applyFixAtFirstHit("Replace 'ansible_os_family' with 'ansible_facts.os_family'")
        val left = myFixture.doHighlighting().filter { it.description?.contains("top-level fact variable") == true }
        assertTrue(left.toString(), left.isEmpty())
    }

    private fun applyFixAtFirstHit(intentionName: String) {
        val info = myFixture.doHighlighting().first { it.description?.contains("top-level fact variable") == true }
        myFixture.editor.caretModel.moveToOffset(info.startOffset)
        myFixture.launchAction(myFixture.findSingleIntention(intentionName))
    }
}
