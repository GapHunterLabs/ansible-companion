package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `risky-file-permissions` (its `safety` profile): a module
 * that creates a file or directory without an explicit `mode:` leaves
 * the resulting permissions up to the remote host's umask.
 *
 * Module list and exclusions follow the real rule's source
 * (`ansiblelint/rules/risky_file_permissions.py`), not a guess:
 * - `unarchive` is left out on purpose, same as upstream -- archives
 *   carry their own member permissions.
 * - The `file` module only creates something for `state: directory` or
 *   `state: touch`; its default `state: file` only modifies an existing
 *   path, so it's never flagged -- flagging it would hit the single most
 *   common use of `file` (changing owner/group of an existing file).
 * - `state: absent`/`link` create nothing with a meaningful mode, and
 *   `recurse` can't apply one uniform mode, so none of those flag.
 * - Line-editing modules only count when they will actually create the
 *   file: `lineinfile`/`blockinfile` default to `create: false`,
 *   `ini_file`/`htpasswd` default to `create: true`.
 *
 * Two deliberate deviations, both toward fewer warnings: `state: hard`
 * is also skipped (a hard link shares its target's inode permissions,
 * it doesn't mint new ones), and upstream's separate
 * "`mode: preserve` on a module that doesn't support it" case isn't
 * reported at all. Free-form arguments (`copy: src=a dest=b mode=0644`)
 * aren't parsed, so they're never flagged -- a `mode` could be hiding in
 * that string.
 */
object RiskyFilePermissionsDetector {
    private val MODULES = setOf(
        "ansible.builtin.assemble", "assemble",
        "ansible.builtin.copy", "copy",
        "ansible.builtin.file", "file",
        "ansible.builtin.get_url", "get_url",
        "ansible.builtin.template", "template",
        "community.general.archive", "archive",
    )
    private val FILE_MODULES = setOf("ansible.builtin.file", "file")
    private val FILE_CREATING_STATES = setOf("directory", "touch")
    private val NON_CREATING_STATES = setOf("absent", "link", "hard")

    /** Module -> its `create` default when the task doesn't set one. */
    private val MODULES_WITH_CREATE = mapOf(
        "ansible.builtin.blockinfile" to false, "blockinfile" to false,
        "ansible.builtin.lineinfile" to false, "lineinfile" to false,
        "community.general.ini_file" to true, "ini_file" to true,
        "community.general.htpasswd" to true, "htpasswd" to true,
    )

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        val createDefault = MODULES_WITH_CREATE[module]
        if (module !in MODULES && createDefault == null) return null
        if (task.moduleFreeForm != null) return null
        if ("mode" in task.parameters) return null

        if (createDefault != null) {
            val creates = task.parameters["create"]?.let { literalBool(it) ?: return null } ?: createDefault
            return if (creates) finding(module) else null
        }

        val state = task.parameters["state"]?.trim()?.lowercase()
        if (state != null && (state in NON_CREATING_STATES || "{{" in state)) return null
        task.parameters["recurse"]?.let { if (literalBool(it) != false) return null }
        if (module in FILE_MODULES && state !in FILE_CREATING_STATES) return null
        return finding(module)
    }

    /** true/yes -> true, false/no -> false, anything else (a Jinja expression) -> null: can't be evaluated statically. */
    private fun literalBool(value: String): Boolean? = when (value.trim().lowercase()) {
        "true", "yes" -> true
        "false", "no" -> false
        else -> null
    }

    private fun finding(module: String) = SecurityFinding(
        "RISKY_FILE_PERMISSIONS",
        "'$module' creates a file without an explicit mode -- the resulting permissions depend on the remote host's umask. Set mode (e.g. mode: \"0644\").",
    )
}
