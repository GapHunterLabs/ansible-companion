package dev.gaphunter.ansiblecompanion.detection

/**
 * Addresses complaint #1 from out/evidence_7792.md: the leading free
 * plugin claims EVERY .yml/.yaml file as Ansible and breaks Kubernetes,
 * Helm, Cloudformation, and Docker-compose in the process.
 *
 * Pure, deliberately testable logic (no VirtualFile/PSI) so it can be
 * validated with plain JUnit without spinning up the platform. Wired to
 * the real platform via AnsibleFileTypeOverrider.
 */
object AnsibleFileDetector {

    private val ROLE_SUBDIRS = setOf("tasks", "handlers", "defaults", "vars", "meta", "library")
    private val PLAYBOOK_SHAPE_KEYS = listOf("hosts:", "roles:", "tasks:", "import_playbook:", "become:")
    private val TASK_SHAPE_KEYS = listOf("ansible.builtin.", "name:", "when:", "with_items:", "loop:", "register:")

    fun looksLikeAnsible(path: String, content: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (!normalized.endsWith(".yml") && !normalized.endsWith(".yaml")) return false
        return pathSignalsAnsible(normalized) || contentSignalsAnsible(content)
    }

    /** I/O-free check, so the overrider can skip reading the file when the path alone is already enough. */
    fun pathAloneSignalsAnsible(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (!normalized.endsWith(".yml") && !normalized.endsWith(".yaml")) return false
        return pathSignalsAnsible(normalized)
    }

    private fun pathSignalsAnsible(path: String): Boolean {
        val segments = path.split('/')
        if (segments.contains("playbooks")) return true

        val rolesIdx = segments.indexOf("roles")
        if (rolesIdx in 0..(segments.size - 3) && segments[rolesIdx + 2] in ROLE_SUBDIRS) return true

        return false
    }

    private fun contentSignalsAnsible(content: String): Boolean {
        val head = content.lineSequence().take(40).joinToString("\n")
        val hasPlaybookShape = PLAYBOOK_SHAPE_KEYS.any { head.contains(it) }
        val hasTaskShape = TASK_SHAPE_KEYS.count { head.contains(it) } >= 2
        return hasPlaybookShape || hasTaskShape
    }
}
