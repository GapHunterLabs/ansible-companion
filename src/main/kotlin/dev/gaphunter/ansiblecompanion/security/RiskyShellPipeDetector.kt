package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `risky-shell-pipe` (its `safety` profile): without
 * `set -o pipefail`, a pipeline's exit status is only its LAST command's
 * -- `cat missing.log | grep ERROR` reports success even though `cat`
 * failed, and Ansible treats the task as fine.
 *
 * Follows the real rule's source (`ansiblelint/rules/risky_shell_pipe.py`):
 * only the `shell` module (`command` doesn't run a shell, so pipes don't
 * work there at all); `||` is a logical OR, not a pipe; Jinja
 * expressions are stripped first, because `{{ x | default('y') }}` is a
 * filter, not a shell pipe; tasks with `ignore_errors` and PowerShell
 * executables are skipped.
 *
 * Two deliberate deviations, both toward fewer warnings: quoted
 * literals are stripped too, since the `|` in `grep -E 'a|b'` is regex
 * alternation, not a pipe; and `set ... pipefail` is accepted anywhere
 * in the command, not only at the start of a line, so
 * `cd /x && set -o pipefail && ...` counts.
 */
object RiskyShellPipeDetector {
    private val SHELL_MODULES = setOf("ansible.builtin.shell", "shell")
    private val JINJA = Regex("""\{\{.*?\}\}|\{%.*?%\}""", RegexOption.DOT_MATCHES_ALL)
    private val QUOTED_LITERAL = Regex("""'[^']*'|"[^"]*"""")
    private val PIPE = Regex("""(?<!\|)\|(?!\|)""")
    private val PIPEFAIL = Regex("""\bset\b[^\n]*[-+][A-Za-z]*o\s*pipefail""")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        if (module !in SHELL_MODULES) return null
        if (task.ignoresErrors) return null
        if (task.parameters["executable"]?.contains("pwsh") == true) return null
        val command = task.moduleFreeForm ?: task.parameters["cmd"] ?: return null

        val withoutJinja = JINJA.replace(command, "")
        if (PIPEFAIL.containsMatchIn(withoutJinja)) return null
        if (!PIPE.containsMatchIn(QUOTED_LITERAL.replace(withoutJinja, ""))) return null
        return SecurityFinding(
            "RISKY_SHELL_PIPE",
            "This shell pipeline doesn't set pipefail -- if any command before the last one fails, the task still reports success. Add 'set -o pipefail' (bash).",
        )
    }
}
