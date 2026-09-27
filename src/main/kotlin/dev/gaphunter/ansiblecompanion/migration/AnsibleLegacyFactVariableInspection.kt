package dev.gaphunter.ansiblecompanion.migration

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import dev.gaphunter.ansiblecompanion.detection.AnsibleFileDetector
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Reports gathered facts used as top-level variables (`ansible_os_family`),
 * which ansible-core 2.20 deprecates and 2.24 stops injecting -- see
 * [LegacyFactVariableDetector] for what counts and why. A weak warning with a
 * quick fix, so a playbook can be migrated in a few keystrokes before the
 * default flips. Free: it is a migration aid, not one of the Pro checks.
 *
 * An inspection rather than an annotator so it can be turned off, have its
 * severity changed, or be suppressed like any other, and so "fix all in file"
 * works.
 */
class AnsibleLegacyFactVariableInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is YAMLScalar) return
                for (hit in hitsIn(element)) {
                    holder.registerProblem(
                        element,
                        "'${hit.variable}' is injected as a top-level fact variable, which ansible-core 2.20 deprecates " +
                            "(INJECT_FACTS_AS_VARS defaults to False in 2.24). Use '${hit.replacement}'.",
                        ProblemHighlightType.WEAK_WARNING,
                        TextRange(hit.start, hit.end),
                        ReplaceWithAnsibleFactsFix(hit.variable),
                    )
                }
            }
        }

    private class ReplaceWithAnsibleFactsFix(private val variable: String) : LocalQuickFix {
        private val replacement = "ansible_facts." + variable.removePrefix("ansible_")

        override fun getFamilyName(): String = "Use ansible_facts instead of an injected fact variable"

        override fun getName(): String = "Replace '$variable' with '$replacement'"

        /**
         * Re-reads the scalar and replaces every use of this variable in it, so applying the fix for
         * one occurrence and then another (or "fix all in file") never works from stale offsets.
         */
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val scalar = descriptor.psiElement as? YAMLScalar ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(scalar.containingFile) ?: return
            val base = scalar.textRange.startOffset
            for (hit in hitsIn(scalar).filter { it.variable == variable }.sortedByDescending { it.start }) {
                document.replaceString(base + hit.start, base + hit.end, hit.replacement)
            }
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    companion object {
        /** Legacy fact variables in [scalar], as ranges relative to the scalar element (quotes included in the offsets). */
        fun hitsIn(scalar: YAMLScalar): List<LegacyFactHit> {
            val raw = scalar.text
            if (!raw.contains("ansible_")) return emptyList()
            val file = scalar.containingFile ?: return emptyList()
            val virtualFile = file.virtualFile ?: file.originalFile.virtualFile ?: return emptyList()
            if (!AnsibleFileDetector.pathAloneSignalsAnsible(virtualFile.path) &&
                !AnsibleFileDetector.looksLikeAnsible(virtualFile.path, file.text)
            ) {
                return emptyList()
            }
            val quoted = scalar is YAMLQuotedText && raw.length >= 2
            val inner = if (quoted) raw.substring(1, raw.length - 1) else raw
            val shift = if (quoted) 1 else 0
            return LegacyFactVariableDetector.find(inner, isBareExpression(scalar))
                .map { LegacyFactHit(it.start + shift, it.end + shift, it.variable) }
        }

        private fun isBareExpression(scalar: YAMLScalar): Boolean {
            val key = when (val parent = scalar.parent) {
                is YAMLKeyValue -> parent.keyText
                is YAMLSequenceItem -> ((parent.parent as? YAMLSequence)?.parent as? YAMLKeyValue)?.keyText
                else -> null
            }
            return key in LegacyFactVariableDetector.BARE_EXPRESSION_KEYS
        }
    }
}
