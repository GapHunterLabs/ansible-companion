package dev.gaphunter.ansiblecompanion.runtime

/**
 * Ansible doesn't run on native Windows (see AnsibleVaultCipher/
 * ARCHITECTURE.md), so "just install Node yourself" isn't an acceptable
 * way out for a good share of this plugin's real users either -> instead
 * of assuming `node` is on the PATH, a private per-user Node runtime is
 * downloaded and cached (NodeRuntimeProvisioner), a pattern already used
 * by several IntelliJ plugins that wrap external tools (e.g. Rust/
 * rust-analyzer).
 *
 * Bundling the Node binary for ALL platforms inside the plugin's .zip
 * isn't viable: each binary weighs ~80-110MB and there's no way to
 * publish a single per-platform artifact on the Marketplace, so bundling
 * all 6 combinations would balloon the plugin to ~500MB+ for a feature
 * most installs won't even touch the same day. Downloading on demand,
 * checksum-verified, is the only option that keeps the .zip light.
 *
 * The language server (`@ansible/ansible-language-server` + its
 * dependency tree, ~16MB) DOES get bundled directly in the plugin ->
 * confirmed by hand that it has no native addons
 * (`find node_modules -name "*.node"` comes back empty), it's pure JS,
 * portable across operating systems without recompiling.
 */
enum class NodePlatform(
    val distSuffix: String,
    val archiveFormat: ArchiveFormat,
    val sha256: String,
    /** Path to the `node`/`node.exe` binary inside the already-extracted archive tree. */
    val binaryPathInArchive: String,
) {
    WINDOWS_X64(
        distSuffix = "win-x64.zip",
        archiveFormat = ArchiveFormat.ZIP,
        sha256 = "0ae68406b42d7725661da979b1403ec9926da205c6770827f33aac9d8f26e821",
        binaryPathInArchive = "node.exe",
    ),
    WINDOWS_ARM64(
        distSuffix = "win-arm64.zip",
        archiveFormat = ArchiveFormat.ZIP,
        sha256 = "f274669adb93b1fd0fbf8f21fd078609e9dcc84333d4f2718d2dde3f9a161a01",
        binaryPathInArchive = "node.exe",
    ),
    LINUX_X64(
        distSuffix = "linux-x64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ,
        sha256 = "783130984963db7ba9cbd01089eaf2c2efb055c7c1693c943174b967b3050cb8",
        binaryPathInArchive = "bin/node",
    ),
    LINUX_ARM64(
        distSuffix = "linux-arm64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ,
        sha256 = "6b4484c2190274175df9aa8f28e2d758a819cb1c1fe6ab481e2f95b463ab8508",
        binaryPathInArchive = "bin/node",
    ),
    MACOS_X64(
        distSuffix = "darwin-x64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ,
        sha256 = "dfd0dbd3e721503434df7b7205e719f61b3a3a31b2bcf9729b8b91fea240f080",
        binaryPathInArchive = "bin/node",
    ),
    MACOS_ARM64(
        distSuffix = "darwin-arm64.tar.gz",
        archiveFormat = ArchiveFormat.TAR_GZ,
        sha256 = "e1a97e14c99c803e96c7339403282ea05a499c32f8d83defe9ef5ec66f979ed1",
        binaryPathInArchive = "bin/node",
    );

    /** Name of the already-extracted, cached binary (without the rest of the Node tree). */
    val cachedBinaryName: String
        get() = if (this == WINDOWS_X64 || this == WINDOWS_ARM64) "node.exe" else "node"

    companion object {
        /** Pinned Node version -> the checksums above correspond exactly to this one. */
        const val NODE_VERSION = "24.18.0"

        fun downloadUrl(platform: NodePlatform): String =
            "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-${platform.distSuffix}"

        /**
         * Injectable detection (doesn't read System.getProperty directly) so
         * all 6 combinations can be tested without depending on whichever
         * OS happens to be running the test.
         */
        fun detect(osName: String, osArch: String): NodePlatform? {
            val os = osName.lowercase()
            val arch = osArch.lowercase()
            val isArm64 = arch in setOf("aarch64", "arm64")
            val isX64 = arch in setOf("x86_64", "amd64", "x64")

            return when {
                os.contains("win") && isX64 -> WINDOWS_X64
                os.contains("win") && isArm64 -> WINDOWS_ARM64
                os.contains("mac") && isArm64 -> MACOS_ARM64
                os.contains("mac") && isX64 -> MACOS_X64
                (os.contains("linux") || os.contains("nix")) && isArm64 -> LINUX_ARM64
                (os.contains("linux") || os.contains("nix")) && isX64 -> LINUX_X64
                else -> null
            }
        }
    }
}

enum class ArchiveFormat { ZIP, TAR_GZ }
