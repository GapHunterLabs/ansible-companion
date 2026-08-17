package dev.gaphunter.ansiblecompanion.navigation

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Real PSI + real VirtualFile project layout (BasePlatformTestCase),
 * not hand-built structures -- same discipline as
 * YamlKeyPositionDetectorTest and the openapi-companion GotoDeclaration
 * tests this class's production code is modeled after. `isLicensed` is
 * injected as a constructor parameter specifically so these tests don't
 * depend on a real LicensingFacade/product-descriptor state.
 */
class AnsibleRoleGotoDeclarationHandlerTest : BasePlatformTestCase() {

    /**
     * Unlike YamlKeyPositionDetectorTest's helper, `<caret>` here always
     * sits in the MIDDLE of real, already-typed text (a role name, never
     * blank/EOF) -- so, unlike that helper, inserting an extra character
     * at the marker would corrupt the very identifier under test
     * (`<caret>nginx` -> `Xnginx`, silently wrong). Just strip the
     * marker and resolve the real leaf at that offset directly.
     */
    private fun configureAndFindCaretElement(fileName: String, text: String): com.intellij.psi.PsiElement {
        val offset = text.indexOf("<caret>")
        require(offset >= 0) { "test text must contain <caret>" }
        val file = myFixture.configureByText(fileName, text.replace("<caret>", ""))
        return file.findElementAt(offset)!!
    }

    fun testNavigatesFromARolesListEntryToTasksMainYml() {
        myFixture.addFileToProject(
            "roles/nginx/tasks/main.yml",
            """
            - name: install nginx
              ansible.builtin.package:
                name: nginx
            """.trimIndent(),
        )
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - hosts: web
              roles:
                - <caret>nginx
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNotNull("expected the role reference to resolve", targets)
        assertEquals(1, targets!!.size)
        assertEquals("main.yml", (targets[0] as PsiFile).name)
        assertEquals("roles/nginx/tasks/main.yml", relativePath(targets[0] as PsiFile))
    }

    fun testNavigatesFromIncludeRoleNameKey() {
        myFixture.addFileToProject(
            "roles/setup_db/tasks/main.yml",
            "- name: init db\n  ansible.builtin.command: echo init\n",
        )
        val position = configureAndFindCaretElement(
            "playbook.yml",
            """
            - name: run
              include_role:
                name: <caret>setup_db
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNotNull("expected include_role's name: to resolve", targets)
        assertEquals("roles/setup_db/tasks/main.yml", relativePath(targets!![0] as PsiFile))
    }

    fun testNavigatesFromImportRoleNameKey() {
        myFixture.addFileToProject(
            "roles/common/tasks/main.yml",
            "- name: common setup\n  ansible.builtin.debug:\n    msg: hi\n",
        )
        val position = configureAndFindCaretElement(
            "playbook.yml",
            """
            - name: run
              import_role:
                name: <caret>common
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNotNull("expected import_role's name: to resolve", targets)
        assertEquals("roles/common/tasks/main.yml", relativePath(targets!![0] as PsiFile))
    }

    fun testFallsBackToMainYamlExtension() {
        myFixture.addFileToProject(
            "roles/legacy/tasks/main.yaml",
            "- name: legacy task\n  ansible.builtin.debug:\n    msg: hi\n",
        )
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - hosts: web
              roles:
                - <caret>legacy
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNotNull("expected the .yaml fallback to resolve", targets)
        assertEquals("main.yaml", (targets!![0] as PsiFile).name)
    }

    fun testReturnsNullWhenTheRoleDoesNotExist() {
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - hosts: web
              roles:
                - <caret>does_not_exist
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNull("a role with no matching roles/<name>/tasks/main.yml should not resolve", targets)
    }

    fun testReturnsNullWhenUnlicensed() {
        myFixture.addFileToProject(
            "roles/nginx/tasks/main.yml",
            "- name: install nginx\n  ansible.builtin.package:\n    name: nginx\n",
        )
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - hosts: web
              roles:
                - <caret>nginx
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { false })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNull("an unlicensed user should get no navigation target, same gating as FQCN completion", targets)
    }

    fun testDoesNotTriggerOnAnUnrelatedScalar() {
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - hosts: web
              vars:
                some_var: <caret>notarole
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNull("a plain var value is not a role reference", targets)
    }

    fun testDoesNotTriggerOnANameKeyOutsideIncludeOrImportRole() {
        myFixture.addFileToProject(
            "roles/nginx/tasks/main.yml",
            "- name: install nginx\n  ansible.builtin.package:\n    name: nginx\n",
        )
        val position = configureAndFindCaretElement(
            "site.yml",
            """
            - name: <caret>nginx
              ansible.builtin.debug:
                msg: hi
            """.trimIndent(),
        )

        val targets = AnsibleRoleGotoDeclarationHandler(isLicensed = { true })
            .getGotoDeclarationTargets(position, position.textOffset, myFixture.editor)

        assertNull("a task's own name: is not a role reference, even if the text happens to match a real role", targets)
    }

    private fun relativePath(file: PsiFile): String {
        val base = myFixture.tempDirFixture.getFile(".")!!
        val vFile = file.virtualFile
        return com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(vFile, base) ?: vFile.path
    }
}
