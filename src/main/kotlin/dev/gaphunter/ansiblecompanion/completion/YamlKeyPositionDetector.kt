package dev.gaphunter.ansiblecompanion.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Decides whether [position] is a *task-level* module key
 * (`- ansible.builtin.<caret>` or `- name: x\n  <caret>`), as opposed to
 * a module-parameter key nested one level deeper (`copy:\n  <caret>`,
 * where completion should NOT suggest FQCN module names).
 *
 * Walks every [YAMLKeyValue] between [position] and the nearest
 * enclosing [YAMLSequenceItem] (the task boundary). If [position] sits
 * inside any of those key-values' VALUE subtree -- not just its key --
 * it's nested under that key (a module parameter, or a parameter of a
 * parameter), so it isn't the task's own key. This correctly classifies
 * both "typing the nested key's name" and "typing the nested key's
 * value" as nested, because either way the walk eventually reaches the
 * outer key (e.g. `copy`) and finds position inside *its* value.
 */
object YamlKeyPositionDetector {
    fun isCompletingTopLevelTaskKey(position: PsiElement): Boolean {
        val sequenceItem = PsiTreeUtil.getParentOfType(position, YAMLSequenceItem::class.java, false) ?: return false
        var element: PsiElement? = position
        while (element != null && element != sequenceItem) {
            if (element is YAMLKeyValue) {
                val value = element.value
                if (value != null && PsiTreeUtil.isAncestor(value, position, false)) {
                    return false
                }
            }
            element = element.parent
        }
        return true
    }
}
