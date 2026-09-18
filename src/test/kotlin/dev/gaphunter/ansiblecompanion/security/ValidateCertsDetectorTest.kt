package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidateCertsDetectorTest {

    private fun task(moduleName: String?, parameters: Map<String, String> = emptyMap()) =
        AnsibleTask(moduleName, parameters, hasNoLog = false, isGroupingConstruct = false)

    @Test
    fun flagsLiteralFalse() {
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.get_url", mapOf("validate_certs" to "false")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun flagsLiteralNo() {
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.uri", mapOf("validate_certs" to "no")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun flagsCaseInsensitively() {
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.uri", mapOf("validate_certs" to "False")),
            noLogInherited = false,
        )
        assertNotNull(finding)
    }

    @Test
    fun doesNotFlagLiteralTrue() {
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.uri", mapOf("validate_certs" to "true")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagAbsentParam() {
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.uri", mapOf("url" to "https://example.com")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagNonLiteralValue() {
        // Can't evaluate a Jinja2 expression statically -- fail closed, no warning.
        val finding = ValidateCertsDetector.check(
            task("ansible.builtin.uri", mapOf("validate_certs" to "{{ skip_tls_check }}")),
            noLogInherited = false,
        )
        assertNull(finding)
    }

    @Test
    fun doesNotFlagWhenModuleNameIsNull() {
        val finding = ValidateCertsDetector.check(task(null, mapOf("validate_certs" to "false")), noLogInherited = false)
        assertNull(finding)
    }
}
