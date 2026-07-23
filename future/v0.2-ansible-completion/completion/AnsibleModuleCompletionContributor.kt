package dev.gaphunter.ansiblecompanion.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.gaphunter.ansiblecompanion.detection.AnsibleYamlFileType

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
 */
class AnsibleModuleCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    if (parameters.originalFile.fileType != AnsibleYamlFileType) return

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

    private fun upsellLookupElement(): LookupElementBuilder =
        LookupElementBuilder.create("ansible.builtin (Ansible Companion Pro)")
            .withTypeText("Upgrade for FQCN completion", true)
            .withInsertHandler { _, _ ->
                CheckLicense.requestLicense("FQCN-aware completion for ansible.builtin.* is part of Ansible Companion Pro.")
            }
}
