package dev.gaphunter.ansiblecompanion.lsp

import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import dev.gaphunter.ansiblecompanion.runtime.NodePlatform
import dev.gaphunter.ansiblecompanion.runtime.NodeProvisioningException
import dev.gaphunter.ansiblecompanion.runtime.NodeRuntimeProvisioner
import java.nio.file.Path

/**
 * Package and entrypoint verified by hand (2026-07-23): `npm view` confirms
 * the current package is the scoped one, `@ansible/ansible-language-server`
 * (the unscoped `ansible-language-server` exists but sits at 0.1.1-0 —
 * abandoned). Its `bin/ansible-language-server` in turn just does
 * `require("../out/server/src/server.js")`, which is the path below.
 *
 * The Node binary is no longer assumed to be on the system PATH: it's
 * provided via `NodeRuntimeProvisioner` (checksum-verified download,
 * cached per user, see `runtime/`) — tested end to end against the real
 * nodejs.org (see `future/v0.2-ansible-lsp/README.md`). What's still
 * pending before this can be reactivated in `src/main`: bundling
 * `@ansible/ansible-language-server` (pure JS, no native addons, ~16MB
 * with its dependency tree) INSIDE the plugin's `.zip`, and resolving
 * `serverEntrypoint` to that bundled path instead of a development
 * `node_modules/` — see the README for the plan.
 */
class AnsibleLanguageServer(
    private val serverEntrypoint: Path,
    cacheRoot: Path,
) : ProcessStreamConnectionProvider() {

    init {
        val platform = NodePlatform.detect(System.getProperty("os.name"), System.getProperty("os.arch"))
            ?: throw NodeProvisioningException(
                "Ansible language server: unsupported platform (${System.getProperty("os.name")}/${System.getProperty("os.arch")})"
            )
        val nodePath = NodeRuntimeProvisioner(cacheRoot).ensureProvisioned(platform)
        setCommands(listOf(nodePath.toString(), serverEntrypoint.toString(), "--stdio"))
    }
}
