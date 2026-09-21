package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RiskyFilePermissionsDetectorTest {

    private fun task(moduleName: String?, parameters: Map<String, String> = emptyMap(), freeForm: String? = null) =
        AnsibleTask(moduleName, parameters, hasNoLog = false, isGroupingConstruct = false, moduleFreeForm = freeForm)

    private fun check(task: AnsibleTask) = RiskyFilePermissionsDetector.check(task, noLogInherited = false)

    @Test
    fun flagsCopyWithoutMode() {
        assertNotNull(check(task("ansible.builtin.copy", mapOf("src" to "a", "dest" to "/etc/b"))))
    }

    @Test
    fun flagsTemplateAndShortModuleNameToo() {
        assertNotNull(check(task("template", mapOf("src" to "a.j2", "dest" to "/etc/b"))))
    }

    @Test
    fun flagsCommunityGeneralArchive() {
        assertNotNull(check(task("community.general.archive", mapOf("path" to "/srv", "dest" to "/tmp/a.tgz"))))
    }

    @Test
    fun doesNotFlagWhenModeIsSet() {
        assertNull(check(task("ansible.builtin.copy", mapOf("src" to "a", "dest" to "/etc/b", "mode" to "0644"))))
    }

    @Test
    fun doesNotFlagFileModuleWithDefaultState() {
        // file with default state: file only modifies an existing path -- the most common use of `file`.
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/etc/b", "owner" to "app"))))
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/etc/b", "state" to "file"))))
    }

    @Test
    fun flagsFileModuleOnlyWhenItCreatesSomething() {
        assertNotNull(check(task("ansible.builtin.file", mapOf("path" to "/srv/app", "state" to "directory"))))
        assertNotNull(check(task("file", mapOf("path" to "/srv/app/.keep", "state" to "touch"))))
    }

    @Test
    fun doesNotFlagNonCreatingStates() {
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/etc/b", "state" to "absent"))))
        assertNull(check(task("ansible.builtin.file", mapOf("src" to "/a", "dest" to "/b", "state" to "link"))))
        assertNull(check(task("ansible.builtin.file", mapOf("src" to "/a", "dest" to "/b", "state" to "hard"))))
    }

    @Test
    fun doesNotFlagRecurse() {
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/srv", "state" to "directory", "recurse" to "true"))))
    }

    @Test
    fun doesNotFlagUnevaluableStateOrRecurse() {
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/srv", "state" to "{{ wanted_state }}"))))
        assertNull(check(task("ansible.builtin.file", mapOf("path" to "/srv", "state" to "directory", "recurse" to "{{ deep }}"))))
    }

    @Test
    fun doesNotFlagUnarchiveOrSynchronize() {
        // unarchive is disabled upstream on purpose (archives carry their own permissions).
        assertNull(check(task("ansible.builtin.unarchive", mapOf("src" to "a.tgz", "dest" to "/srv"))))
        assertNull(check(task("ansible.posix.synchronize", mapOf("src" to "a", "dest" to "/srv"))))
    }

    @Test
    fun doesNotFlagFreeFormArguments() {
        // A mode could be hiding in the unparsed free-form string.
        assertNull(check(task("copy", freeForm = "src=a dest=/etc/b mode=0644")))
    }

    @Test
    fun lineinfileOnlyFlagsWhenItWillCreateTheFile() {
        assertNull(check(task("ansible.builtin.lineinfile", mapOf("path" to "/etc/b", "line" to "x"))))
        assertNotNull(check(task("ansible.builtin.lineinfile", mapOf("path" to "/etc/b", "line" to "x", "create" to "yes"))))
    }

    @Test
    fun iniFileCreatesByDefaultUnlessToldNotTo() {
        assertNotNull(check(task("community.general.ini_file", mapOf("path" to "/etc/b.ini", "section" to "s"))))
        assertNull(check(task("community.general.ini_file", mapOf("path" to "/etc/b.ini", "create" to "false"))))
        assertNull(check(task("community.general.ini_file", mapOf("path" to "/etc/b.ini", "create" to "{{ make_it }}"))))
    }

    @Test
    fun doesNotFlagUnrelatedModule() {
        assertNull(check(task("ansible.builtin.debug", mapOf("msg" to "hi"))))
        assertNull(check(task(null, mapOf("dest" to "/etc/b"))))
    }
}
