package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `risky-octal` (its `safety` profile): an unquoted
 * `mode: 755` is read by YAML as the DECIMAL integer 755, which Ansible
 * then applies as the permission bits of 755 decimal -- octal 01363,
 * not the 0755 the author meant.
 *
 * Mirrors the real rule's actual test, not just its title: upstream
 * only reports an integer mode whose resulting permission bits are
 * nonsensical ([isInvalidPermission], ported verbatim from
 * `ansiblelint/rules/risky_octal.py`). So `mode: 420` -- decimal 420 is
 * exactly octal 0644 -- is correctly NOT flagged, even though it has no
 * leading zero.
 *
 * One deliberate deviation toward fewer warnings: an unquoted value
 * WITH a leading zero (`mode: 0602`) is YAML 1.1 octal, and upstream
 * would still flag it if the bits are odd -- but with a message telling
 * the user to add a leading zero it already has. Only zero-less decimal
 * values are reported here, so every warning's advice is actually
 * correct. Quoted values are strings, never integers, and never flag.
 */
object RiskyOctalModeDetector {
    private val MODULES = setOf(
        "ansible.builtin.assemble", "assemble",
        "ansible.builtin.copy", "copy",
        "ansible.builtin.file", "file",
        "community.general.ini_file", "ini_file",
        "ansible.builtin.lineinfile", "lineinfile",
        "ansible.builtin.replace", "replace",
        "ansible.posix.synchronize", "synchronize",
        "ansible.builtin.template", "template",
        "ansible.builtin.unarchive", "unarchive",
    )
    private val ZERO_LESS_DECIMAL = Regex("[1-9][0-9]*")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        if (module !in MODULES) return null
        if ("mode" !in task.unquotedParameters) return null
        val raw = task.parameters["mode"]?.trim() ?: return null
        if (!ZERO_LESS_DECIMAL.matches(raw)) return null
        val mode = raw.toIntOrNull() ?: return null
        if (!isInvalidPermission(mode)) return null

        val fix = if (raw.all { it in '0'..'7' }) "mode: \"0$raw\"" else "a quoted octal string such as mode: \"0644\""
        return SecurityFinding(
            "RISKY_OCTAL",
            "'mode: $raw' is read as the decimal number $raw, which sets permissions 0${mode.toString(8)} -- not what it looks like. Use $fix, or symbolic mode.",
        )
    }

    /**
     * Sensible modes never set write without read, never set execute for
     * group/other without it for the user, and never make group/other
     * more generous than the user (or other more generous than group).
     */
    fun isInvalidPermission(mode: Int): Boolean {
        val other = mode % 8
        val group = (mode shr 3) % 8
        val user = (mode shr 6) % 8
        val userExec = user % 2 == 1
        val otherWriteWithoutRead = other != 0 && other < 4 && !(other == 1 && userExec)
        val groupWriteWithoutRead = group != 0 && group < 4 && !(group == 1 && userExec)
        val userWriteWithoutRead = user != 0 && user < 4 && user != 1
        return otherWriteWithoutRead || groupWriteWithoutRead || userWriteWithoutRead ||
            other > group || other > user || group > user
    }
}
