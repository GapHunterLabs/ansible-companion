package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NoLogPasswordDetectorTest {

    private fun task(moduleName: String?, parameters: Map<String, String> = emptyMap(), hasNoLog: Boolean = false) =
        AnsibleTask(moduleName, parameters, hasNoLog, isGroupingConstruct = false)

    @Test
    fun flagsPasswordArgWithoutNoLog() {
        val finding = NoLogPasswordDetector.check(
            task("ansible.builtin.user", mapOf("name" to "bob", "password" to "{{ vault_pw }}")),
            noLogInherited = false,
        )
        assertNotNull(finding)
        assertEquals("NO_LOG_PASSWORD", finding!!.kind)
    }

    @Test
    fun recognizesShortModuleNameToo() {
        val finding = NoLogPasswordDetector.check(
            task("user", mapOf("password" to "{{ vault_pw }}")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun doesNotFlagWhenNoLogSetOnTaskItself() {
        val finding = NoLogPasswordDetector.check(
            task("ansible.builtin.user", mapOf("password" to "{{ vault_pw }}"), hasNoLog = true),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenNoLogInherited() {
        val finding = NoLogPasswordDetector.check(
            task("ansible.builtin.user", mapOf("password" to "{{ vault_pw }}")),
            noLogInherited = true,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagModuleWithoutPasswordArg() {
        val finding = NoLogPasswordDetector.check(
            task("ansible.builtin.user", mapOf("name" to "bob", "state" to "present")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagUnknownModule() {
        val finding = NoLogPasswordDetector.check(
            task("ansible.builtin.copy", mapOf("password" to "irrelevant")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenModuleNameIsNull() {
        val finding = NoLogPasswordDetector.check(task(null), noLogInherited = false)
        assertNull(finding)
    }
}
