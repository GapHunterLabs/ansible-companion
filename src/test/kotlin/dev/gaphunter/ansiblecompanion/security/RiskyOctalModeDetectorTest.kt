package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskyOctalModeDetectorTest {

    private fun task(moduleName: String?, mode: String, unquoted: Boolean = true) = AnsibleTask(
        moduleName,
        mapOf("dest" to "/etc/b", "mode" to mode),
        hasNoLog = false,
        isGroupingConstruct = false,
        unquotedParameters = if (unquoted) setOf("dest", "mode") else setOf("dest"),
    )

    private fun check(task: AnsibleTask) = RiskyOctalModeDetector.check(task, noLogInherited = false)

    @Test
    fun flagsUnquotedDecimalThatMeantOctal() {
        assertNotNull(check(task("ansible.builtin.file", "755")))
        assertNotNull(check(task("ansible.builtin.copy", "644")))
        assertNotNull(check(task("template", "777")))
        assertNotNull(check(task("ansible.builtin.template", "600")))
    }

    @Test
    fun messageStatesTheRealResultingPermissions() {
        val finding = check(task("ansible.builtin.file", "755"))!!
        assertTrue(finding.message.contains("01363"))
        assertTrue(finding.message.contains("mode: \"0755\""))
    }

    @Test
    fun doesNotSuggestAnInvalidOctalWhenTheDigitsArentOctal() {
        val finding = check(task("ansible.builtin.file", "689"))!!
        assertFalse(finding.message.contains("0689"))
    }

    @Test
    fun doesNotFlagDecimalThatHappensToBeSensible() {
        // 420 decimal is exactly octal 0644 -- the real rule doesn't flag it either.
        assertNull(check(task("ansible.builtin.file", "420")))
        assertNull(check(task("ansible.builtin.file", "493"))) // 0755
    }

    @Test
    fun doesNotFlagQuotedValues() {
        assertNull(check(task("ansible.builtin.file", "755", unquoted = false)))
        assertNull(check(task("ansible.builtin.file", "0755", unquoted = false)))
    }

    @Test
    fun doesNotFlagLeadingZeroOctal() {
        assertNull(check(task("ansible.builtin.file", "0755")))
        assertNull(check(task("ansible.builtin.file", "0602")))
    }

    @Test
    fun doesNotFlagSymbolicOrJinjaOrZero() {
        assertNull(check(task("ansible.builtin.file", "u+rwx,g-w")))
        assertNull(check(task("ansible.builtin.file", "{{ file_mode }}")))
        assertNull(check(task("ansible.builtin.file", "0")))
    }

    @Test
    fun doesNotFlagModuleOutsideTheRulesList() {
        assertNull(check(task("ansible.builtin.get_url", "755")))
        assertNull(check(task(null, "755")))
    }

    @Test
    fun isInvalidPermissionMatchesUpstreamOnKnownValues() {
        assertTrue(RiskyOctalModeDetector.isInvalidPermission(644))
        assertTrue(RiskyOctalModeDetector.isInvalidPermission(755))
        assertTrue(RiskyOctalModeDetector.isInvalidPermission(777))
        assertFalse(RiskyOctalModeDetector.isInvalidPermission(0b110_100_100)) // 0644
        assertFalse(RiskyOctalModeDetector.isInvalidPermission(0b111_101_101)) // 0755
        assertFalse(RiskyOctalModeDetector.isInvalidPermission(0b110_000_000)) // 0600
        assertFalse(RiskyOctalModeDetector.isInvalidPermission(0))
    }
}
