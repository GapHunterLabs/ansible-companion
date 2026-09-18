package dev.gaphunter.ansiblecompanion.security

/**
 * Not a real ansible-lint rule -- this plugin's own addition, kept
 * separate in the docs/README from the ansible-lint-derived checks so
 * the catalog's honesty standard holds (never attribute a rule to
 * ansible-lint that it doesn't actually have). Trivially static and of
 * high perceived value: a literal `validate_certs: false`/`no` hardcoded
 * on a network-fetching task skips TLS certificate validation entirely.
 *
 * Only fires on a literal false/no -- `validate_certs: "{{ some_var }}"`
 * isn't evaluable statically and is deliberately left unflagged (fail
 * closed, same reasoning as every other detector in this package).
 * Absence of `validate_certs` is also never flagged: Ansible's own
 * default is `true`, so absence is not itself a problem.
 */
object ValidateCertsDetector {
    private val FALSE_LITERALS = setOf("false", "no")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        val value = task.parameters["validate_certs"] ?: return null
        if (value.trim().lowercase() !in FALSE_LITERALS) return null
        return SecurityFinding(
            "VALIDATE_CERTS_DISABLED",
            "'$module' has validate_certs disabled -- TLS certificate validation is skipped for this request.",
        )
    }
}
