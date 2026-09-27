package dev.gaphunter.ansiblecompanion.security

/**
 * ansible-lint `latest` (its `safety` profile): a source-control checkout
 * pinned to a moving reference -- `git` with `version: HEAD`, `hg` with
 * `revision: tip` -- gives a different result whenever the repository
 * moves, so two runs of the same play can deploy different code.
 *
 * Deviation toward fewer warnings: only an EXPLICIT `HEAD`/`tip` is
 * reported. A task that simply omits the version is not flagged here,
 * even though it checks out the default branch head.
 */
object LatestVersionDetector {
    private val GIT = setOf("ansible.builtin.git", "git")
    private val HG = setOf("community.general.hg", "ansible.builtin.hg", "hg")

    fun check(task: AnsibleTask, @Suppress("UNUSED_PARAMETER") noLogInherited: Boolean): SecurityFinding? {
        val module = task.moduleName ?: return null
        if (task.moduleFreeForm != null) return null
        return when (module) {
            in GIT -> if (task.parameters["version"]?.trim() == "HEAD") {
                SecurityFinding(
                    "LATEST_VERSION",
                    "'version: HEAD' checks out whatever HEAD is when the play runs, so two runs can deploy different code -- pin a tag or a commit hash.",
                )
            } else {
                null
            }
            in HG -> if ((task.parameters["revision"] ?: task.parameters["rev"])?.trim() == "tip") {
                SecurityFinding(
                    "LATEST_VERSION",
                    "'revision: tip' checks out whatever the tip is when the play runs, so two runs can deploy different code -- pin a revision or a tag.",
                )
            } else {
                null
            }
            else -> null
        }
    }
}
