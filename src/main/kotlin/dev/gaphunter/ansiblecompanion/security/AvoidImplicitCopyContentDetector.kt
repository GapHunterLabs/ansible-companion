package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `avoid-implicit[copy-content]` (its `safety` profile): the
 * `copy` module accepts a dict or list for `content` and silently writes
 * it out through an undocumented conversion. Explicit is better: convert
 * it in the template (`content: "{{ value | to_json }}"`).
 *
 * Only a literal mapping or sequence written under `content` is
 * reported; a string, a block scalar or a Jinja expression never is.
 */
object AvoidImplicitCopyContentDetector {
    private val MODULES = setOf("ansible.builtin.copy", "copy")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        if (module !in MODULES) return null
        if ("content" !in task.nonScalarParameters) return null
        return SecurityFinding(
            "AVOID_IMPLICIT_COPY_CONTENT",
            "'content' is a dict or list: copy writes it out with an undocumented implicit conversion -- convert it explicitly, e.g. content: \"{{ value | to_json }}\" (or to_nice_yaml).",
        )
    }
}
