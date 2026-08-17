package dev.gaphunter.ansiblecompanion.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.ansiblecompanion.completion.CheckLicense
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Ctrl+Click / Ctrl+B navigation from a role reference straight to that
 * role's entry point (`roles/<name>/tasks/main.yml`) -- part of
 * Ansible Companion Pro, same license gate as FQCN completion and
 * Jinja2 highlighting (see [dev.gaphunter.ansiblecompanion.completion.CheckLicense]).
 * "Role support" was promised but never built (see this plugin's own
 * CHANGELOG's "On hold for 0.2.0" list, dated 2026-07-23) -- this is the
 * first concrete slice of it.
 *
 * Recognizes two real Ansible shapes, both scalars inside a task-level
 * key:
 *  - a plain string in a `roles:` sequence (`roles:\n  - <caret>foo`)
 *  - `include_role`/`import_role`'s `name:` sub-key
 *    (`include_role:\n  name: <caret>foo`)
 *
 * Deliberately does NOT reuse [dev.gaphunter.ansiblecompanion.completion.YamlKeyPositionDetector]'s
 * "which YAMLKeyValue governs this position" walk -- role references
 * live at a different structural depth (a sequence item's own scalar,
 * or a key one level under a task key) than the FQCN completion's
 * task-key position, so a separate, narrower detection keeps the intent
 * clearer than forcing a shared abstraction over two genuinely
 * different shapes.
 *
 * Registered as our own [GotoDeclarationHandler] rather than a
 * `PsiReference`/`PsiReferenceContributor` pair -- same reasoning as
 * [dev.gaphunter.openapicompanion.reference.OpenApiGotoDeclarationHandler]
 * in the sibling openapi-companion plugin: `GotoDeclarationHandler`s are
 * consulted directly by the platform with no suppress-then-fallback
 * hand-off to coordinate, and there's no bundled handler here to
 * conflict with in the first place (unlike the OpenAPI/JSON Schema
 * case), so the simpler direct approach is enough.
 */
class AnsibleRoleGotoDeclarationHandler(
    private val isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (sourceElement == null || !isLicensed()) return null

        val roleName = roleNameAt(sourceElement) ?: return null
        val project = sourceElement.project
        val virtualFile = sourceElement.containingFile?.virtualFile ?: return null

        val target = findRoleTasksMain(virtualFile, roleName) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(target) ?: return null
        return arrayOf(psiFile)
    }

    /**
     * Returns the role name the caret is on, or null if this position
     * isn't a role reference at all. The caret can land on the scalar
     * itself (a `roles:` list item) or on the value of a `name:` key
     * nested under `include_role`/`import_role` -- both are
     * [YAMLScalar]s, just reached via a different PSI shape.
     */
    private fun roleNameAt(position: PsiElement): String? {
        val scalar = PsiTreeUtil.getParentOfType(position, YAMLScalar::class.java, false) ?: return null

        // Shape 1: `roles:\n  - <caret>foo` -- a bare scalar sequence item.
        val sequenceItem = PsiTreeUtil.getParentOfType(scalar, YAMLSequenceItem::class.java, false)
        if (sequenceItem != null && sequenceItem.value == scalar) {
            val ownerKey = PsiTreeUtil.getParentOfType(sequenceItem, YAMLKeyValue::class.java)
            if (ownerKey?.keyText == "roles") {
                return scalar.textValue.takeIf { it.isNotBlank() }
            }
        }

        // Shape 2: `include_role:\n  name: <caret>foo` / `import_role:\n  name: <caret>foo`.
        val keyValue = PsiTreeUtil.getParentOfType(scalar, YAMLKeyValue::class.java, false)
        if (keyValue != null && keyValue.value == scalar && keyValue.keyText == "name") {
            val moduleKey = PsiTreeUtil.getParentOfType(keyValue, YAMLKeyValue::class.java)
            if (moduleKey?.keyText == "include_role" || moduleKey?.keyText == "import_role") {
                return scalar.textValue.takeIf { it.isNotBlank() }
            }
        }

        return null
    }

    /**
     * Walks up from the current file to the nearest ancestor directory
     * containing a `roles/<roleName>/tasks/main.yml` (the project root
     * in a typical layout, but playbooks can live several levels deep,
     * e.g. `environments/prod/site.yml` alongside a top-level `roles/`).
     * `main.yml` is Ansible's own fixed entry-point convention for a
     * role's task list, not a guess (same convention `ansible-playbook`
     * itself resolves `include_role`/`roles:` against).
     *
     * Deliberately does NOT stop at `ProjectFileIndex.getContentRootForFile` --
     * confirmed via a real BasePlatformTestCase (2026-08-16) that it
     * returns null for a lightweight test fixture's temp files (no real
     * module/content-root wiring there), and even in a real project a
     * content root doesn't reliably sit exactly at the `roles/`
     * directory's parent. Walks to VFS root instead, bounded by
     * [MAX_ANCESTOR_DEPTH] so a pathological/cyclical VirtualFile setup
     * can't loop forever.
     */
    private fun findRoleTasksMain(from: VirtualFile, roleName: String): VirtualFile? {
        var dir: VirtualFile? = from.parent
        var depth = 0
        while (dir != null && depth < MAX_ANCESTOR_DEPTH) {
            val rolesDir = dir.findChild("roles")?.findChild(roleName)?.findChild("tasks")
            rolesDir?.findChild("main.yml")?.let { return it }
            rolesDir?.findChild("main.yaml")?.let { return it }
            dir = dir.parent
            depth++
        }
        return null
    }

    private companion object {
        /** Generous ceiling for how many directories up to search for `roles/` -- real playbook trees never nest this deep. */
        const val MAX_ANCESTOR_DEPTH = 32
    }
}
