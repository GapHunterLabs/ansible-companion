package dev.gaphunter.ansiblecompanion.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AnsibleModuleIndexTest {

    @Test
    fun parsesASimpleFlatJsonObject() {
        val json = """{"ansible.builtin.copy": "Copy files", "ansible.builtin.debug": "Print statements"}"""
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertEquals(2, modules.size)
        assertEquals(
            AnsibleModule("ansible.builtin", "copy", "Copy files"),
            modules.first { it.shortName == "copy" },
        )
    }

    @Test
    fun modulesAreSortedByNamespaceThenShortName() {
        val json = """
            {"ansible.builtin.zzz_last": "z", "ansible.builtin.aaa_first": "a",
             "community.general.mid": "m"}
        """.trimIndent()
        val modules = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertEquals(
            listOf("ansible.builtin.aaa_first", "ansible.builtin.zzz_last", "community.general.mid"),
            modules.map { it.fqcn },
        )
    }

    @Test
    fun fqcnJoinsNamespaceAndShortName() {
        assertEquals("ansible.builtin.copy", AnsibleModule("ansible.builtin", "copy", "Copy files").fqcn)
        assertEquals(
            "community.general.docker_container",
            AnsibleModule("community.general", "docker_container", "Manage containers").fqcn,
        )
    }

    @Test
    fun splitsOnTheLastDotSinceNamespacesThemselvesContainADot() {
        val json = """{"community.general.docker_container": "Manage containers"}"""
        val module = AnsibleModuleIndex.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))).single()

        assertEquals("community.general", module.namespace)
        assertEquals("docker_container", module.shortName)
    }

    @Test
    fun handlesEscapedCharactersInsideStrings() {
        val json = """{"ansible.builtin.foo": "Uses \"quotes\" and a\nnewline"}"""
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

        assertTrue(
            "expected several hundred real modules across builtin/community.general/ansible.posix, got ${modules.size}",
            modules.size in 500..1000,
        )
        assertTrue(modules.any { it.fqcn == "ansible.builtin.copy" })
        assertTrue(modules.any { it.fqcn == "ansible.builtin.debug" })
        assertTrue(modules.any { it.fqcn == "ansible.builtin.command" })
        assertTrue(modules.any { it.fqcn == "community.general.alternatives" })
        assertTrue(modules.any { it.fqcn == "ansible.posix.mount" })
        val namespaces = modules.map { it.namespace }.toSet()
        assertEquals(setOf("ansible.builtin", "community.general", "ansible.posix"), namespaces)
        for (module in modules) {
            assertTrue("description for ${module.fqcn} should not be blank", module.description.isNotBlank())
            assertTrue(
                "fqcn for ${module.fqcn} should be namespace + '.' + shortName",
                module.fqcn == "${module.namespace}.${module.shortName}",
            )
        }
    }
}
