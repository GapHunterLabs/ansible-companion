package dev.gaphunter.ansiblecompanion.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.gaphunter.ansiblecompanion.detection.AnsibleFileDetector

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
 * module key (`ansible.builtin.<caret>:`), including the brand-new-task
 * case where no `:` has been typed yet and YAML hasn't parsed a
 * `YAMLKeyValue` at all (confirmed by real `runIde` testing 2026-07-30 --
 * the original `?: return false` on the missing-YAMLKeyValue branch made
 * this fire NEVER, not even the unlicensed upsell item, for exactly the
 * most common case: `- ansible.builtin.<caret>` on a fresh task).
 *
 * Narrowed to a task's own key (`- ansible.builtin.<caret>` or
 * `- name: x\n  <caret>`), not a nested module-parameter key
 * (`copy:\n  <caret>`) -- see [YamlKeyPositionDetector].
 */
class AnsibleModuleCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    val virtualFile = parameters.originalFile.virtualFile ?: return
                    // Deliberately re-run the same path/content heuristic
                    // AnsibleFileTypeOverrider uses, instead of trusting
                    // parameters.originalFile.fileType == AnsibleYamlFileType:
                    // confirmed live (2026-07-30) that the overrider's result
                    // and the PSI file's cached FileType can disagree for the
                    // lifetime of an already-open FileViewProvider (the
                    // override applies at the VirtualFile level, but PSI
                    // completion sees a stale FileType), so the file-type
                    // identity check is not a reliable gate here.
                    if (!AnsibleFileDetector.pathAloneSignalsAnsible(virtualFile.path) &&
                        !AnsibleFileDetector.looksLikeAnsible(virtualFile.path, parameters.originalFile.text)
                    ) {
                        return
                    }
                    if (!YamlKeyPositionDetector.isCompletingTopLevelTaskKey(parameters.position)) return

                    // A CompletionResultSet's default matching requires the
                    // lookup string to START WITH whatever the user already
                    // typed. Confirmed live (2026-07-30) the prefix at
                    // "- ansible.builtin.<caret>" is literally
                    // "ansible.builtin." WITH the trailing dot -- so the
                    // upsell item's lookup string must also include it
                    // (module.fqcn already does: "ansible.builtin.$shortName").
                    if (CheckLicense.isLicensed() == false) {
                        result.addElement(upsellLookupElement(result.prefixMatcher.prefix))
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

    /**
     * The lookup STRING (what the platform's PrefixMatcher compares
     * against what the user already typed) must itself START WITH the
     * exact prefix already typed -- not just "look similar to" it.
     * Confirmed live (2026-07-30), across two separate bugs:
     * 1. `LookupElementBuilder.create("ansible.builtin (Ansible Companion
     *    Pro)")` -- the space+parenthesis broke the match even though
     *    `addElement()` was confirmed (via a debug log) to execute; the
     *    item was silently filtered out after being added, not lost in
     *    our own code.
     * 2. A follow-up fix using a fixed `"ansible.builtin"` (no trailing
     *    dot) STILL never rendered: the real prefix at
     *    `- ansible.builtin.<caret>` is `"ansible.builtin."`, WITH the
     *    dot (confirmed via `result.prefixMatcher.prefix` logging) --
     *    17 characters, one longer than the fixed lookup string, so it
     *    could never be a match no matter what came after.
     * Fix: build the lookup string FROM the real prefix instead of
     * guessing a fixed one, so it's always at least as long.
     */
    private fun upsellLookupElement(prefix: String): LookupElementBuilder =
        LookupElementBuilder.create(if (prefix.startsWith("ansible.builtin")) prefix else "ansible.builtin")
            .withPresentableText("ansible.builtin (Ansible Companion Pro)")
            .withTypeText("Upgrade for FQCN completion", true)
            .withInsertHandler { _, _ ->
                CheckLicense.requestLicense("FQCN-aware completion for ansible.builtin.* is part of Ansible Companion Pro.")
            }
}
