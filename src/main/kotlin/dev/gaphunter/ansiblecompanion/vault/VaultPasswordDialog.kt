package dev.gaphunter.ansiblecompanion.vault

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Never saves or caches the password between calls — every encrypt/
 * decrypt asks for it again. More friction, zero risk of leaving a
 * secret in memory longer than necessary.
 */
class VaultPasswordDialog(title: String) : DialogWrapper(true) {
    private val field = JBPasswordField()

    init {
        this.title = title
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(2, 1, 0, 6))
        panel.add(JBLabel("Vault password:"))
        panel.add(field)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = field

    fun password(): CharArray = field.password
}
