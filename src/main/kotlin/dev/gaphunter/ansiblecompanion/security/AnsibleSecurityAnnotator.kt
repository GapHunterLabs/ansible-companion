package dev.gaphunter.ansiblecompanion.security

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import dev.gaphunter.ansiblecompanion.completion.CheckLicense
import dev.gaphunter.ansiblecompanion.detection.AnsibleFileDetector
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

private val TASK_LIST_OWNER_KEYS = setOf("tasks", "handlers", "pre_tasks", "post_tasks", "block", "rescue", "always")

/**
 * `LicensingFacade` calls aren't free -- same 60s-TTL cache pattern
 * already used by `JinjaHighlightingAnnotator`'s `LicenseCache`,
 * duplicated here rather than shared: it's a few lines, and a shared
 * cache object across two unrelated Annotators would couple their
 * re-check timing for no real benefit.
 */
private object SecurityLicenseCache {
    private const val TTL_MS = 60_000L
    private var cachedValue: Boolean = false
    private var lastCheckedAtMs: Long = 0L

    fun isLicensedNow(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckedAtMs > TTL_MS) {
            cachedValue = CheckLicense.isLicensed() == true
            lastCheckedAtMs = now
        }
        return cachedValue
    }
}

/**
 * Ansible Companion Pro: static secret/permission hygiene checks on
 * task-shaped YAML sequence items -- secret exposure
 * ([NoLogPasswordDetector], [NoLogVaultIncludeDetector],
 * [ValidateCertsDetector]) and file/shell hygiene
 * ([RiskyFilePermissionsDetector], [RiskyOctalModeDetector],
 * [RiskyShellPipeDetector]).
 *
 * Registered `language="yaml"`, same as `JinjaHighlightingAnnotator`.
 * Re-verifies [AnsibleFileDetector] directly against the real
 * `VirtualFile` on every invocation instead of trusting a cached
 * `FileType` -- same reasoning already documented twice in
 * `KNOWN_ISSUES.md` (Rounds 1/3): `AnsibleFileTypeOverrider`'s result
 * and a PSI file's own cached FileType can disagree for the lifetime of
 * an already-open file.
 */
class AnsibleSecurityAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val sequenceItem = element as? YAMLSequenceItem ?: return

        val file = element.containingFile ?: return
        val virtualFile = file.virtualFile ?: return
        if (!AnsibleFileDetector.pathAloneSignalsAnsible(virtualFile.path) &&
            !AnsibleFileDetector.looksLikeAnsible(virtualFile.path, file.text)
        ) {
            return
        }

        if (!SecurityLicenseCache.isLicensedNow()) return

        val (anchor, findings) = computeFindings(sequenceItem, isBareRoleTasksFile(virtualFile.path)) ?: return
        for (finding in findings) {
            holder.newAnnotation(HighlightSeverity.WARNING, finding.message)
                .range(anchor.textRange)
                .create()
        }
    }

    /**
     * Everything `annotate()` does except the license gate and the
     * `AnnotationHolder` calls -- split out so tests can exercise the
     * real task-positioning + extraction + detector pipeline against
     * real PSI without needing a real `LicensingFacade` (always
     * unlicensed in `BasePlatformTestCase`) or a real `AnnotationHolder`
     * (only obtainable from a live highlighting pass).
     */
    fun computeFindings(sequenceItem: YAMLSequenceItem, isBareRoleTasksFile: Boolean): Pair<PsiElement, List<SecurityFinding>>? {
        if (!isTaskPositioned(sequenceItem, isBareRoleTasksFile)) return null

        val task = AnsibleTaskExtractor.extract(sequenceItem)
        val moduleName = task.moduleName ?: return null
        val noLogInherited by lazy { AnsibleTaskExtractor.resolveNoLogInherited(sequenceItem) }

        val findings = listOfNotNull(
            NoLogPasswordDetector.check(task, noLogInherited),
            NoLogVaultIncludeDetector.check(task, noLogInherited),
            ValidateCertsDetector.check(task, noLogInherited),
            RiskyFilePermissionsDetector.check(task, noLogInherited),
            RiskyOctalModeDetector.check(task, noLogInherited),
            RiskyShellPipeDetector.check(task, noLogInherited),
        )
        if (findings.isEmpty()) return null

        val anchor = sequenceItem.keysValues.first { it.keyText == moduleName }.key ?: return null
        return anchor to findings
    }

    /**
     * A [sequenceItem] is task-positioned if its enclosing sequence is
     * the value of a recognized task-list key (`tasks:`/`handlers:`/
     * `pre_tasks:`/`post_tasks:`/`block:`/`rescue:`/`always:`), OR --
     * the real Ansible convention for `roles/<name>/tasks/main.yml` and
     * `roles/<name>/handlers/main.yml` -- the sequence IS the top-level
     * document content directly, no wrapping key at all.
     *
     * Deliberately does NOT treat every top-level bare sequence as
     * task-positioned: a playbook file under `playbooks` (any `.yml`
     * file there) has a top level that is a sequence of PLAYS
     * (`hosts:`/`tasks:`/`roles:`/... mappings), not tasks --
     * misclassifying a play as a task would let
     * `AnsibleTaskKeywords.moduleKeyAmong` see play-level keys it
     * doesn't recognize (`hosts`, `roles`, `gather_facts`, ...) and
     * potentially mis-flag one of them as a "module".
     * [isBareRoleTasksFile] gates that distinction on the file path,
     * not content -- confirmed cheap and reliable given
     * [AnsibleFileDetector]'s own path-first design.
     */
    private fun isTaskPositioned(sequenceItem: YAMLSequenceItem, isBareRoleTasksFile: Boolean): Boolean {
        val sequence = sequenceItem.parent as? YAMLSequence ?: return false
        val owningKeyValue = sequence.parent as? YAMLKeyValue
        if (owningKeyValue != null) return owningKeyValue.keyText in TASK_LIST_OWNER_KEYS
        return isBareRoleTasksFile
    }

    private fun isBareRoleTasksFile(path: String): Boolean {
        val segments = path.replace('\\', '/').split('/')
        return segments.contains("tasks") || segments.contains("handlers")
    }
}
