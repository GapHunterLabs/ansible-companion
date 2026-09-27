package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `package-latest` (its `safety` profile): a package manager
 * task with `state: latest` upgrades the package on every run -- and,
 * unless told otherwise, pulls in extra packages -- so what a play does
 * depends on when it runs.
 *
 * Follows the rule's documented behavior: any package-manager module with
 * `state: latest` is reported, EXCEPT the two safeguards it names,
 * `update_only: true` (dnf/yum: upgrade only what is already installed)
 * and `only_upgrade: true` (apt).
 *
 * Deviations, both toward fewer warnings: a `state` written as a Jinja
 * expression (`state: "{{ pkg_state }}"`) can't be evaluated statically
 * and is never flagged; and only modules from `ansible.builtin`,
 * `ansible.windows` and `community.general` (or a bare module name) count,
 * so a same-named module from an unrelated collection isn't guessed at.
 * Unlike the other hygiene checks, the old `key=value` argument style
 * (`yum: name=httpd state=latest`) IS read here, since it is how a great
 * many package tasks are still written.
 */
object PackageLatestDetector {
    private val MODULES = setOf(
        "package", "apk", "apt", "apt_rpm", "bower", "bundler", "dnf", "dnf5", "easy_install", "gem", "homebrew",
        "jenkins_plugin", "npm", "openbsd_package", "pacman", "pip", "pkg5", "pkgutil", "portage", "slackpkg",
        "sorcery", "swdepot", "win_chocolatey", "yarn", "yum", "zypper",
    )
    private val NAMESPACES = setOf("ansible.builtin", "ansible.windows", "community.general")

    /** module short name -> the parameter that makes `state: latest` safe. */
    private val SAFEGUARD_PARAMETER = mapOf("dnf" to "update_only", "dnf5" to "update_only", "yum" to "update_only", "apt" to "only_upgrade")
    private val TRUTHY = setOf("true", "yes", "on", "1")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        val shortName = module.substringAfterLast('.')
        if (shortName !in MODULES) return null
        if (module.contains('.') && module.substringBeforeLast('.') !in NAMESPACES) return null

        val state = argument(task, "state")?.trim()?.lowercase() ?: return null
        if (state != "latest") return null
        val safeguard = SAFEGUARD_PARAMETER[shortName]
        if (safeguard != null && argument(task, safeguard)?.trim()?.lowercase() in TRUTHY) return null

        val safe = when (safeguard) {
            null -> "pin a version with state: present"
            else -> "pin a version with state: present, or add $safeguard: true so it only upgrades what is installed"
        }
        return SecurityFinding(
            "PACKAGE_LATEST",
            "'state: latest' upgrades this package on every run, so the play's result depends on when it runs -- $safe.",
        )
    }

    /** A module argument written as YAML (`state: latest`) or in the old `key=value` style (`state=latest`). */
    private fun argument(task: AnsibleTask, name: String): String? {
        task.parameters[name]?.let { return it }
        val freeForm = task.moduleFreeForm ?: return null
        val match = Regex("""(?:^|\s)${Regex.escape(name)}=("[^"]*"|'[^']*'|\S+)""").find(freeForm) ?: return null
        return match.groupValues[1].trim('"', '\'')
    }
}
