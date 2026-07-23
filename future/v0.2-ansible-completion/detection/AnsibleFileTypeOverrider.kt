package dev.gaphunter.ansiblecompanion.detection

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

private const val HEAD_BYTES = 4096

/**
 * The real connection point for complaint #1: without this, any
 * .yml/.yaml stays plain YAML and the Ansible language server never
 * activates selectively (which is exactly the bug that sinks "YAML/
 * Ansible support", see out/evidence_7792.md).
 *
 * Avoids reading the file when the path alone is already enough (inside
 * roles, a tasks/handlers/etc. subfolder, or playbooks/) — only reads the
 * first bytes for the ambiguous case.
 */
class AnsibleFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (AnsibleFileDetector.pathAloneSignalsAnsible(file.path)) return AnsibleYamlFileType

        if (!file.name.endsWith(".yml") && !file.name.endsWith(".yaml")) return null

        val head = try {
            val bytes = file.contentsToByteArray()
            String(bytes, 0, minOf(bytes.size, HEAD_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }

        return if (AnsibleFileDetector.looksLikeAnsible(file.path, head)) AnsibleYamlFileType else null
    }
}
