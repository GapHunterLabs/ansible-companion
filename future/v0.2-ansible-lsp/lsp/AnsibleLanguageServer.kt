package dev.gaphunter.ansiblecompanion.lsp

import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import dev.gaphunter.ansiblecompanion.runtime.NodePlatform
import dev.gaphunter.ansiblecompanion.runtime.NodeProvisioningException
import dev.gaphunter.ansiblecompanion.runtime.NodeRuntimeProvisioner
import java.nio.file.Path

/**
 * Paquete y entrypoint verificados a mano (2026-07-23): `npm view` confirma
 * que el paquete vigente es el con scope, `@ansible/ansible-language-server`
 * (el sin scope, `ansible-language-server`, existe pero esta en 0.1.1-0 —
 * abandonado). Su `bin/ansible-language-server` a su vez solo hace
 * `require("../out/server/src/server.js")`, que es el path de abajo.
 *
 * El binario de Node ya NO se asume del PATH del sistema: se provee via
 * `NodeRuntimeProvisioner` (descarga verificada por checksum, cacheada por
 * usuario, ver `runtime/`) — probado de punta a punta contra nodejs.org real
 * (ver `future/v0.2-ansible-lsp/README.md`). Lo que SIGUE pendiente antes de
 * poder reactivar esto en `src/main`: empaquetar `@ansible/ansible-language-server`
 * (JS puro, sin addons nativos, ~16MB con su arbol de dependencias) DENTRO
 * del .zip del plugin, y resolver `serverEntrypoint` a esa ruta empaquetada
 * en vez de al `node_modules/` de desarrollo — ver README para el plan.
 */
class AnsibleLanguageServer(
    private val serverEntrypoint: Path,
    cacheRoot: Path,
) : ProcessStreamConnectionProvider() {

    init {
        val platform = NodePlatform.detect(System.getProperty("os.name"), System.getProperty("os.arch"))
            ?: throw NodeProvisioningException(
                "Ansible language server: plataforma no soportada (${System.getProperty("os.name")}/${System.getProperty("os.arch")})"
            )
        val nodePath = NodeRuntimeProvisioner(cacheRoot).ensureProvisioned(platform)
        setCommands(listOf(nodePath.toString(), serverEntrypoint.toString(), "--stdio"))
    }
}
