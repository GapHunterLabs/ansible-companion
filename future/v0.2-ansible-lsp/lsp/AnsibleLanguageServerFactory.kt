package dev.gaphunter.ansiblecompanion.lsp

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.nio.file.Path

class AnsibleLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        // TODO: replace with the path to server.js bundled inside the plugin
        // (see future/v0.2-ansible-lsp/README.md, "bundle the language server"
        // section) instead of this development path relative to runIde's
        // working directory.
        val entrypoint = Path.of("node_modules/@ansible/ansible-language-server/out/server/src/server.js")
        val cacheRoot = Path.of(PathManager.getSystemPath(), "gap-hunter-ansible-companion", "node")
        return AnsibleLanguageServer(entrypoint, cacheRoot)
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl =
        LanguageClientImpl(project)
}
