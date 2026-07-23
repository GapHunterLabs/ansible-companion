package dev.gaphunter.ansiblecompanion.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import dev.gaphunter.ansiblecompanion.detection.AnsibleYamlFileType
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * FQCN-aware completion (`ansible.builtin.*`), the paid v0.2 feature.
 * Deliberately built as a static bundled index (AnsibleModuleIndex)
 * instead of wrapping `ansible-language-server` via LSP4IJ -- see this
 * folder's README for why that approach was abandoned (the language
 * server refuses to run outside WSL on Windows and shells out to real
 * `ansible`/`ansible-lint` for exactly this data).
 *
 * Gated behind CheckLicense.isLicensed(): unlicensed users get a single
 * upsell item instead of the real completions, rather than either a
 * silent no-op or blocking the whole editor -- consistent with how most
 * freemium IntelliJ plugins surface their paid tier.
 *
 * Scoped to YAML mapping-KEY positions (`isCompletingYamlKey`) so module
 * names don't clutter completion while typing a parameter value, a
 * string, or a comment -- only while typing what would become a task's
 * module key (`ansible.builtin.<caret>:`). NOT yet narrowed further to
 * "specifically a top-level task key, not a nested module-parameter key"
 * (e.g. it would still fire inside `copy:\n  <caret>`) -- that needs
 * checking the key's enclosing mapping is itself a sequence-item value,
 * which needs live runIde verification to get exactly right rather than
 * guessing PSI shapes untested, so it's left as a follow-up (see README).
 */
class AnsibleModuleCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    if (parameters.originalFile.fileType != AnsibleYamlFileType) return
                    if (!isCompletingYamlKey(parameters.position)) return

                    if (CheckLicense.isLicensed() == false) {
                        result.addElement(upsellLookupElement())
                        return
                    }

                    for (module in AnsibleModuleIndex.modules) {
                        result.addElement(
                            LookupElementBuilder.create(module.fqcn)
                                .withTypeText(module.description, true)
                                .withLookupString(module.shortName),
                        )
                    }
                }
            },
        )
    }

    private fun isCompletingYamlKey(position: PsiElement): Boolean {
        val keyValue = PsiTreeUtil.getParentOfType(position, YAMLKeyValue::class.java, false) ?: return false
        val key = keyValue.key ?: return true // no key parsed yet (e.g. right after typing "- ") -> treat as key position
        return PsiTreeUtil.isAncestor(key, position, false)
    }

    private fun upsellLookupElement(): LookupElementBuilder =
        LookupElementBuilder.create("ansible.builtin (Ansible Companion Pro)")
            .withTypeText("Upgrade for FQCN completion", true)
            .withInsertHandler { _, _ ->
                CheckLicense.requestLicense("FQCN-aware completion for ansible.builtin.* is part of Ansible Companion Pro.")
            }
}
