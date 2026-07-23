package dev.gaphunter.ansiblecompanion.vault

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

class EncryptSelectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        if (editor.selectionModel.selectedText.isNullOrEmpty()) {
            Messages.showWarningDialog("Select the text you want to encrypt first.", "Ansible Vault")
            return
        }

        val dialog = VaultPasswordDialog("Encrypt as Ansible Vault")
        if (!dialog.showAndGet()) return
        val password = String(dialog.password())
        if (password.isEmpty()) {
            Messages.showWarningDialog("Password cannot be empty.", "Ansible Vault")
            return
        }

        VaultEditorOps.encryptSelection(e.project, editor, password)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && editor.selectionModel.hasSelection()
    }
}
