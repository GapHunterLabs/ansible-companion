# v0.2.0 — FQCN + Jinja2 + YAML scope (on hold)

This code compiles and its tests pass (9/9 when it was set aside — plus
23/23 new ones in `runtime/`, see below), but it was pulled out of
`src/main/kotlin` so that v0.1.x (vault-only) doesn't depend on LSP4IJ or
the YAML plugin — that way `verifyPlugin` doesn't flag a reference to
another plugin's classes without declaring the dependency.

## Node bundling blocker — SOLVED (2026-07-23)

The real blocker keeping this on hold (see ARCHITECTURE.md, section 2) was
**bundling Node + `@ansible/ansible-language-server` inside the plugin**,
without asking the user to install anything — Ansible doesn't run on
native Windows either (confirmed by hand: fails to import `fcntl`), so
"just install Node/Ansible yourself" isn't a way out for a good share of
real users.

Key diagnosis: the language server package plus its entire dependency tree
(`@flatten-js/interval-tree`, `antsibull-docs` — a TypeScript
reimplementation, not a Python shell-out — `axios`, `glob`,
`vscode-languageserver`, etc.) is **~16MB and 100% pure JS, with no native
addons** (`find node_modules -name "*.node"` comes back empty) — that part
CAN be bundled directly inside the plugin with no cross-platform
portability problem.

What can't be bundled the same way is the **Node binary** itself: it's
native and different per platform+architecture (win/linux/mac × x64/arm64
= 6 combinations, ~80-110MB each uncompressed) — packing all 6 into the
same `.zip` would balloon the plugin to 500MB+ for a feature most
installs won't touch the same day they install it. The solution, and the
pattern other IntelliJ plugins that wrap external tools already use (e.g.
Rust/rust-analyzer): **download and cache a private per-user Node runtime
the first time it's needed, verified by checksum before touching its
contents.**

### `runtime/` — implemented, tested, and verified against the real nodejs.org

| File | What it does |
|---|---|
| `NodePlatform.kt` | Platform detection (injectable, testable without depending on whatever OS runs the test) + a hand-pinned URL/SHA-256-checksum table for Node **v24.18.0** (LTS "Krypton"), all 6 win/linux/mac × x64/arm64 combinations. |
| `ArchiveExtractor.kt` | Extracts **a single file** (the `node`/`node.exe` binary) from a `.zip` or `.tar.gz` without decompressing the rest of the tree (npm/docs/headers that never get used) — a hand-written TAR reader (ustar + the GNU long-name extension) instead of adding Apache Commons Compress as a new dependency (same call as `AnsibleVaultCipher`: hand-rolled PBKDF2 instead of a library). |
| `NodeRuntimeProvisioner.kt` | Orchestrates: if already cached and verified, return that path; otherwise download to a temp file → **verify SHA-256 before extracting or running anything** → extract only the binary → mark `.provisioned` → clean up the temp archive. Never trusts unverified content. |

`runtime-tests/` — 23 JUnit tests, all passing: platform detection, zip/
tar.gz extraction (including the GNU long-filename case and "entry not
found"), and the full `NodeRuntimeProvisioner` flow against a real local
HTTP server (`com.sun.net.httpserver.HttpServer`, no real network, and not
dependent on Node's real checksums, which by design can't be faked in a
test) — covers the happy path, cache-hit on the second call, invalid
checksum (must leave nothing extracted on disk), HTTP 404, and a missing
entry inside an otherwise valid archive.

**Also manually verified once against the real network** (not part of the
automated test suite — downloads a real ~90MB, not CI-appropriate): a
smoke-test `main()` (`SmokeTestMain.kt`, deliberately not included here —
it only ever lived in a throwaway Gradle project in the scratchpad of the
session that wrote this) ran `NodePlatform.detect()` against the machine's
real `os.name`/`os.arch`, downloaded the real `node-v24.18.0-win-x64.zip`
from `nodejs.org`, verified its SHA-256 against the pinned value, extracted
only `node.exe` (92,534,088 bytes — the binary alone, not the full tree),
ran it with `--version` and confirmed `v24.18.0`, and confirmed a second
call uses the cache (1ms vs ~8s the first time). To reproduce: create a
standalone Kotlin Gradle project with these 3 files plus a `main()` that
calls `NodeRuntimeProvisioner(tempDir).ensureProvisioned(platform)` and
runs the resulting binary with `--version`.

`AnsibleLanguageServer.kt`/`AnsibleLanguageServerFactory.kt` are already
updated to use the provisioner (via `PathManager.getSystemPath()` as the
cache root) instead of assuming `node` is on the system PATH.

### What's still pending before reactivating this in `src/main`

**Bundle the language server (pure JS, ~16MB) inside the plugin's `.zip`.**
Today `AnsibleLanguageServerFactory` still points at
`node_modules/@ansible/ansible-language-server/...` (a development-only
path, relative to `runIde`'s working directory, that only happens to
resolve in the sandbox). Concrete plan for whoever picks this up next:

1. Add a Gradle `Copy` task in `build.gradle.kts` that runs (or depends
   on) `npm install --omit=dev` into a build directory, and copies the
   full `node_modules/@ansible/ansible-language-server/` (with its
   resolved dependency tree) to something like
   `build/bundled-lsp/ansible-language-server/`.
2. Include that directory in the final `.zip` — the IntelliJ Gradle
   plugin allows adding loose files to the artifact via the
   `intellijPlatform.pluginConfiguration` block, or by directly adjusting
   the `buildPlugin`/`prepareSandbox` task to copy that folder into `lib/`
   or a sibling folder of the jar (as opposed to "a resource inside the
   jar", which does NOT work here because Node needs real files on disk,
   not `.jar` entries).
3. In `AnsibleLanguageServerFactory`, resolve the path to the bundled
   `server.js` relative to the plugin's own installation (e.g.
   `PluginManagerCore.getPlugin(pluginId)?.pluginPath`), not to a
   development-only `node_modules/`.
4. Confirm with `verifyPlugin` that the final `.zip` carries those files
   and that the total size stays reasonable (~16MB extra, nowhere near
   the 500MB+ bundling Node itself would have cost).

## To reactivate (once the bundling above is solved)

1. Move `detection/`, `lsp/`, and `runtime/` back to
   `src/main/kotlin/dev/gaphunter/ansiblecompanion/`, and
   `detection-tests/` + `runtime-tests/` to
   `src/test/kotlin/dev/gaphunter/ansiblecompanion/...`.
2. Restore in `build.gradle.kts`: `bundledPlugin("org.jetbrains.plugins.yaml")`
   and `plugin("com.redhat.devtools.lsp4ij", "0.20.1")` (or the current
   version), plus the language server's `Copy` task (see above).
3. Restore in `plugin.xml`: the yaml/lsp4ij `<depends>` entries and the
   two `<extensions>` blocks (fileTypeOverrider + server/fileTypeMapping).
4. Run the full `verifyPlugin` (6 IDEs) before publishing — not just
   `test`/`buildPlugin`.
