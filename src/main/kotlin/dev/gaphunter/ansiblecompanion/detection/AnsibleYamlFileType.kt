package dev.gaphunter.ansiblecompanion.detection

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage

/**
 * Same language as YAML (same syntax, folding, PSI: reuses
 * YAMLLanguage.INSTANCE) but a distinct FileType, so LSP4IJ can map the
 * Ansible language server ONLY to these files and not to any .yml/.yaml —
 * see AnsibleFileTypeOverrider.
 */
object AnsibleYamlFileType : LanguageFileType(YAMLLanguage.INSTANCE) {
    override fun getName() = "Ansible YAML"
    override fun getDescription() = "Ansible playbook, role, or task file"
    override fun getDefaultExtension() = "yml"
    override fun getIcon() = YAMLFileType.YML.icon
}
