package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsibleTaskKeywordsTest {

    @Test
    fun recognizesReservedKeywords() {
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("name"))
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("when"))
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("no_log"))
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("become"))
    }

    @Test
    fun recognizesWithLookupPluginPattern() {
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("with_items"))
        assertTrue(AnsibleTaskKeywords.isReservedKeyword("with_dict"))
    }

    @Test
    fun doesNotTreatModuleNamesAsReserved() {
        assertTrue(!AnsibleTaskKeywords.isReservedKeyword("ansible.builtin.user"))
        assertTrue(!AnsibleTaskKeywords.isReservedKeyword("copy"))
    }

    @Test
    fun findsTheSingleModuleKeyAmongTaskKeys() {
        val keys = listOf("name", "when", "ansible.builtin.user", "no_log")
        assertEquals("ansible.builtin.user", AnsibleTaskKeywords.moduleKeyAmong(keys))
    }

    @Test
    fun returnsNullWhenOnlyReservedKeysPresent() {
        val keys = listOf("name", "when", "become")
        assertNull(AnsibleTaskKeywords.moduleKeyAmong(keys))
    }

    @Test
    fun returnsNullWhenMultipleCandidates() {
        // Ambiguous -- safer to report nothing than guess wrong for a security check.
        val keys = listOf("name", "copy", "template")
        assertNull(AnsibleTaskKeywords.moduleKeyAmong(keys))
    }

    @Test
    fun treatsGroupingConstructsAsNonModule() {
        val keys = listOf("name", "block", "no_log")
        assertNull(AnsibleTaskKeywords.moduleKeyAmong(keys))
    }
}
