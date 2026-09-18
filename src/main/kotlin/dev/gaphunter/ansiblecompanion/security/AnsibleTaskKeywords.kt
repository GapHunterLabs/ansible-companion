package dev.gaphunter.ansiblecompanion.security

/**
 * Ansible's own reserved task-level keywords -- fetched directly from
 * `docs.ansible.com/ansible/latest/reference_appendices/playbooks_keywords.html`
 * (Task section), not from memory. Any key on a task that is NOT one of
 * these (and doesn't match the `with_*` lookup-plugin pattern) is the
 * module being invoked -- this is the same rule Ansible's own parser
 * uses.
 */
object AnsibleTaskKeywords {
    val RESERVED: Set<String> = setOf(
        "action", "any_errors_fatal", "args", "async",
        "become", "become_exe", "become_flags", "become_method", "become_user",
        "changed_when", "check_mode", "collections", "connection",
        "debugger", "delay", "delegate_facts", "delegate_to", "diff",
        "environment", "failed_when",
        "ignore_errors", "ignore_unreachable",
        "local_action", "loop", "loop_control",
        "module_defaults", "name", "no_log", "notify",
        "poll", "port",
        "register", "remote_user", "retries", "run_once",
        "tags", "throttle", "timeout",
        "until", "vars", "when",
    )

    /** Grouping constructs: not a module invocation, but a nested list of tasks. */
    val GROUPING_CONSTRUCTS: Set<String> = setOf("block", "rescue", "always")

    fun isReservedKeyword(key: String): Boolean =
        key in RESERVED || key.startsWith("with_")

    /**
     * The module key of a task: the one key that is neither a reserved
     * task keyword nor a grouping construct. Returns null if there is
     * none (a task with only reserved keys, e.g. a bare `name:`/`when:`
     * block with nothing to run -- malformed Ansible, but not our job
     * to flag that) or more than one candidate (ambiguous; safer to
     * report nothing than to guess wrong for a security check).
     */
    fun moduleKeyAmong(keys: Collection<String>): String? {
        val candidates = keys.filter { !isReservedKeyword(it) && it !in GROUPING_CONSTRUCTS }
        return candidates.singleOrNull()
    }
}
