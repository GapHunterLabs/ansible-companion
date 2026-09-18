package dev.gaphunter.ansiblecompanion.security

/**
 * CVE-2021-20191: credentials passed as a password-shaped module
 * argument are disclosed in console/CI logs when `no_log` isn't set.
 * Both the short module name (bundled `ansible.builtin`/well-known
 * collections used without an FQCN, relying on a `collections:` block)
 * and the fully-qualified name are listed -- deliberately small and
 * explicit rather than exhaustive: a missed module is a silent false
 * negative, an over-eager list would be a noisy false positive, and
 * for a security-hygiene check the latter is worse (it erodes trust in
 * every other warning this plugin shows).
 */
object NoLogPasswordDetector {
    private val PASSWORD_SHAPED_PARAMS: Map<String, Set<String>> = mapOf(
        "ansible.builtin.user" to setOf("password"),
        "user" to setOf("password"),
        "ansible.builtin.htpasswd" to setOf("password"),
        "htpasswd" to setOf("password"),
        "community.mysql.mysql_user" to setOf("password"),
        "mysql_user" to setOf("password"),
        "community.postgresql.postgresql_user" to setOf("password"),
        "postgresql_user" to setOf("password"),
        "ansible.builtin.uri" to setOf("password"),
        "uri" to setOf("password"),
        "ansible.builtin.get_url" to setOf("password"),
        "get_url" to setOf("password"),
    )

    fun check(task: AnsibleTask, noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        val passwordParams = PASSWORD_SHAPED_PARAMS[module] ?: return null
        if (task.hasNoLog || noLogInherited) return null
        val hasPasswordArg = passwordParams.any { it in task.parameters }
        if (!hasPasswordArg) return null
        return SecurityFinding(
            "NO_LOG_PASSWORD",
            "This task sets a password-shaped argument for '$module' without no_log -- the value may be exposed in console/CI logs (CVE-2021-20191).",
        )
    }
}
