package dev.gaphunter.ansiblecompanion.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AnsibleModuleIndexTest {

    @Test
    fun parsesASimpleFlatJsonObject() {
        val json = """{"copy": "Copy files", "debug": "Print statements"}"""
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertEquals(2, modules.size)
        assertEquals(AnsibleModule("copy", "Copy files"), modules.first { it.shortName == "copy" })
    }

    @Test
    fun modulesAreSortedByShortName() {
        val json = """{"zzz_last": "z", "aaa_first": "a"}"""
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertEquals(listOf("aaa_first", "zzz_last"), modules.map { it.shortName })
    }

    @Test
    fun fqcnPrependsAnsibleBuiltin() {
        assertEquals("ansible.builtin.copy", AnsibleModule("copy", "Copy files").fqcn)
    }

    @Test
    fun handlesEscapedCharactersInsideStrings() {
        val json = """{"foo": "Uses \"quotes\" and a\nnewline"}"""
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertEquals("Uses \"quotes\" and a\nnewline", modules.single().description)
    }

    @Test
    fun handlesEmptyObject() {
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8)))
        assertTrue(modules.isEmpty())
    }

    @Test
    fun realBundledResourceLoadsWithSaneContent() {
        val modules = AnsibleModuleIndex.modules

        assertTrue("expected a few dozen real ansible.builtin modules, got ${modules.size}", modules.size in 50..200)
        assertTrue(modules.any { it.shortName == "copy" })
        assertTrue(modules.any { it.shortName == "debug" })
        assertTrue(modules.any { it.shortName == "command" })
        for (module in modules) {
            assertTrue("description for ${module.shortName} should not be blank", module.description.isNotBlank())
            assertTrue("fqcn for ${module.shortName} should start with ansible.builtin.", module.fqcn.startsWith("ansible.builtin."))
        }
    }
}
