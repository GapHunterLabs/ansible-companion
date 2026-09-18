package dev.gaphunter.ansiblecompanion.security

import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Everything the pure detectors (`NoLogPasswordDetector`,
 * `NoLogVaultIncludeDetector`, `ValidateCertsDetector`, ...) need about
 * a single task, already extracted from PSI. Detectors never see a
 * [YAMLSequenceItem]/[YAMLMapping] directly -- this is the ONLY file in
 * this package that touches YAML PSI, so a future PSI-shape bug (the
 * kind that already bit this plugin twice, see `KNOWN_ISSUES.md`
 * Rounds 1/3) is contained to one place, not scattered across 6 files.
 *
 * `parameters` only ever holds simple scalar values (`key: value`) --
 * a module argument that's itself a nested map/list is omitted, not
 * stringified. A detector that needs to see a real value here should
 * fail closed (treat it as absent) rather than guess at a
 * representation of complex YAML.
 */
data class AnsibleTask(
    val moduleName: String?,
    val parameters: Map<String, String>,
    val hasNoLog: Boolean,
    val isGroupingConstruct: Boolean,
)

object AnsibleTaskExtractor {
    private val TRUE_LITERALS = setOf("true", "yes")

    /** Generous ceiling for how many block/rescue/always levels to walk up -- real playbooks never nest this deep. */
    private const val MAX_ANCESTOR_DEPTH = 20

    fun extract(sequenceItem: YAMLSequenceItem): AnsibleTask {
        val keyValues = sequenceItem.keysValues
        val keys = keyValues.map { it.keyText }
        val moduleName = AnsibleTaskKeywords.moduleKeyAmong(keys)
        val isGrouping = keys.any { it in AnsibleTaskKeywords.GROUPING_CONSTRUCTS }
        val hasNoLog = isTrueLiteral(keyValues.firstOrNull { it.keyText == "no_log" })

        val parameters = if (moduleName != null) {
            val moduleValue = keyValues.first { it.keyText == moduleName }.value
            if (moduleValue is YAMLMapping) {
                moduleValue.keyValues.mapNotNull { kv -> scalarText(kv)?.let { kv.keyText to it } }.toMap()
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        return AnsibleTask(moduleName, parameters, hasNoLog, isGrouping)
    }

    /**
     * True if [sequenceItem] sits (directly or via nested block/rescue/
     * always) inside an outer task whose own `no_log: true` covers it.
     * Only walks block/rescue/always ancestors within the SAME file --
     * play-level/role-level `no_log` inheritance is a deliberate v1
     * limitation (would require cross-file/cross-scope resolution, the
     * exact kind of runtime context this feature avoids by design).
     *
     * PSI shape for the case this resolves
     * (`block:\n  - <task>\nno_log: true`): [sequenceItem]'s parent is
     * the [YAMLSequence] under `block:`; that sequence's parent is the
     * `block:` [YAMLKeyValue] itself; that key-value's enclosing
     * mapping is the OUTER task mapping, where `no_log` is a sibling
     * key of `block`, not a descendant of it.
     */
    fun resolveNoLogInherited(sequenceItem: YAMLSequenceItem): Boolean {
        var currentItem = sequenceItem
        var depth = 0
        while (depth < MAX_ANCESTOR_DEPTH) {
            val sequence = currentItem.parent as? YAMLSequence ?: return false
            val owningKeyValue = sequence.parent as? YAMLKeyValue ?: return false
            if (owningKeyValue.keyText !in AnsibleTaskKeywords.GROUPING_CONSTRUCTS) return false
            val outerMapping = owningKeyValue.parentMapping ?: return false
            if (isTrueLiteral(outerMapping.getKeyValueByKey("no_log"))) return true
            currentItem = outerMapping.parent as? YAMLSequenceItem ?: return false
            depth++
        }
        return false
    }

    private fun scalarText(keyValue: YAMLKeyValue): String? = (keyValue.value as? YAMLScalar)?.textValue

    private fun isTrueLiteral(keyValue: YAMLKeyValue?): Boolean =
        keyValue?.let { scalarText(it) }?.trim()?.lowercase() in TRUE_LITERALS
}
