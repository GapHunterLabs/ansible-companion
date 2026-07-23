package dev.gaphunter.ansiblecompanion.lsp

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.nio.file.Path

class AnsibleLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        // TODO: reemplazar por la ruta al server.js empaquetado dentro del plugin
        // (ver future/v0.2-ansible-lsp/README.md, seccion "bundlear el language server")
        // en vez de esta ruta de desarrollo relativa al working directory de runIde.
        val entrypoint = Path.of("node_modules/@ansible/ansible-language-server/out/server/src/server.js")
        val cacheRoot = Path.of(PathManager.getSystemPath(), "gap-hunter-ansible-companion", "node")
        return AnsibleLanguageServer(entrypoint, cacheRoot)
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl =
        LanguageClientImpl(project)
}
