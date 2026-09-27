package dev.gaphunter.ansiblecompanion.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFactVariableDetectorTest {

    private fun names(text: String, bare: Boolean = false) = LegacyFactVariableDetector.find(text, bare).map { it.variable }

    @Test
    fun findsAFactInsideAnExpression() {
        assertEquals(listOf("ansible_os_family"), names("{{ ansible_os_family }}"))
        assertEquals(listOf("ansible_distribution", "ansible_distribution_major_version"),
            names("{{ ansible_distribution }}-{{ ansible_distribution_major_version }}"))
    }

    @Test
    fun findsAFactInsideAStatementBlock() {
        assertEquals(listOf("ansible_os_family"), names("{% if ansible_os_family == 'Debian' %}apt{% endif %}"))
    }

    @Test
    fun reportsTheExactRangeOfTheIdentifier() {
        val text = "path: /srv/{{ ansible_hostname }}/data"
        val hit = LegacyFactVariableDetector.find(text, bareExpression = false).single()
        assertEquals("ansible_hostname", text.substring(hit.start, hit.end))
        assertEquals("ansible_facts.hostname", hit.replacement)
    }

    @Test
    fun plainTextOutsideBracesIsNeverScanned() {
        assertTrue(names("the variable ansible_os_family is a fact").isEmpty())
        assertTrue(names("ansible_os_family").isEmpty())
    }

    @Test
    fun aBareExpressionIsScannedWhole() {
        assertEquals(listOf("ansible_os_family"), names("ansible_os_family == 'Debian'", bare = true))
        assertEquals(listOf("ansible_distribution", "ansible_distribution_major_version"),
            names("ansible_distribution == 'RedHat' and ansible_distribution_major_version | int >= 8", bare = true))
    }

    @Test
    fun connectionAndMagicVariablesAreNotFacts() {
        for (name in listOf("ansible_host", "ansible_user", "ansible_connection", "ansible_port", "ansible_python_interpreter",
            "ansible_check_mode", "ansible_diff_mode", "ansible_version", "ansible_play_hosts", "ansible_facts", "ansible_local",
            "ansible_become", "ansible_ssh_private_key_file", "ansible_verbosity", "ansible_run_tags")) {
            assertTrue(name, names("{{ $name }}").isEmpty())
        }
    }

    @Test
    fun aLongerNameThatStartsWithAFactIsNotThatFact() {
        // ansible_python is a fact; ansible_python_interpreter is not.
        assertEquals(listOf("ansible_python"), names("{{ ansible_python.version.major }}"))
        assertTrue(names("{{ ansible_python_interpreter }}").isEmpty())
        assertTrue(names("{{ my_ansible_os_family }}").isEmpty())
        assertTrue(names("{{ ansible_os_family_custom }}").isEmpty())
    }

    @Test
    fun theCorrectFormIsNotReported() {
        assertTrue(names("{{ ansible_facts['os_family'] }}").isEmpty())
        assertTrue(names("{{ ansible_facts.os_family }}").isEmpty())
        assertTrue(names("ansible_facts['distribution'] == 'Ubuntu'", bare = true).isEmpty())
    }

    @Test
    fun aVariableReachedThroughAnotherObjectIsLeftAlone() {
        assertTrue(names("{{ hostvars[inventory_hostname].ansible_os_family }}").isEmpty())
        assertTrue(names("{{ item.ansible_hostname }}").isEmpty())
    }

    @Test
    fun textInsideAQuotedStringInAnExpressionIsLeftAlone() {
        assertTrue(names("{{ 'ansible_os_family' in group_names }}").isEmpty())
        assertTrue(names("{{ \"ansible_hostname\" }}").isEmpty())
        assertEquals(listOf("ansible_hostname"), names("{{ 'x' ~ ansible_hostname }}"))
    }

    @Test
    fun anEscapedQuoteDoesNotEndTheString() {
        assertTrue(names("{{ 'it\\'s ansible_hostname' }}").isEmpty())
    }

    @Test
    fun commentsAreSkippedAndUnterminatedBlocksDoNotCrash() {
        assertTrue(names("{# ansible_os_family #}").isEmpty())
        assertTrue(names("{{ ansible_os_family").isEmpty())
        assertTrue(names("").isEmpty())
    }

    @Test
    fun attributeAccessOnAFactKeepsTheRest() {
        val text = "{{ ansible_default_ipv4.address }}"
        val hit = LegacyFactVariableDetector.find(text, false).single()
        assertEquals("ansible_default_ipv4", hit.variable)
        assertEquals("{{ ansible_facts.default_ipv4.address }}", text.replaceRange(hit.start, hit.end, hit.replacement))
    }

    @Test
    fun theFactListLooksSane() {
        val all = LegacyFactVariableDetector.LEGACY_VARIABLES
        assertTrue(all.size > 80)
        assertTrue(all.all { it.startsWith("ansible_") })
        for (notAFact in listOf("ansible_host", "ansible_user", "ansible_python_interpreter", "ansible_facts")) assertFalse(notAFact, notAFact in all)
    }
}
