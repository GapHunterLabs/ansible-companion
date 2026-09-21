package dev.gaphunter.ansiblecompanion.security

/**
 * CVE-2024-8775: Ansible Vault-encrypted variables loaded via
 * `include_vars` are shown in plaintext in console/CI output when the
 * including task doesn't set `no_log`.
 *
 * "Vault-shaped" is a deliberate, documented heuristic on the
 * referenced file's name/path (`vault`/`secret`, case-insensitive) --
 * this plugin has no way to know statically whether a given YAML file
 * is actually vault-encrypted without reading it, and reading arbitrary
 * project files from an `Annotator` is out of scope for v1. A file
 * named e.g. `credentials.yml` (no `vault`/`secret` in the name) is a
 * known, accepted false negative, not a bug -- see the plugin's own
 * README/CHANGELOG for the current list of known limitations.
 *
 * Reads the file reference from `file:`/`dir:`, or from the free-form
 * style `include_vars: secrets/prod.vault.yml` -- arguably the most
 * common way it's written.
 *
 * Scoped to `include_vars` only -- Ansible's other vars-loading
 * mechanism, `vars_files:`, is a PLAY-level keyword (a sibling of
 * `tasks:`, not something that appears as a task in the sequences this
 * plugin's Annotator walks), so it's structurally out of reach for a
 * task-level detector and not attempted here.
 */
object NoLogVaultIncludeDetector {
    private val INCLUDE_VARS_MODULES = setOf("ansible.builtin.include_vars", "include_vars")
    private val VAULT_SHAPED_NAME = Regex("(?i)vault|secret")

    fun check(task: AnsibleTask, noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        if (module !in INCLUDE_VARS_MODULES) return null
        if (task.hasNoLog || noLogInherited) return null
        val fileRef = task.parameters["file"] ?: task.parameters["dir"] ?: task.moduleFreeForm ?: return null
        if (!VAULT_SHAPED_NAME.containsMatchIn(fileRef)) return null
        return SecurityFinding(
            "NO_LOG_VAULT_INCLUDE",
            "This task loads '$fileRef' (looks like a vault/secrets file) via '$module' without no_log -- its contents may be exposed in console/CI logs (CVE-2024-8775).",
        )
    }
}
