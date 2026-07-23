package dev.gaphunter.ansiblecompanion.runtime

/**
 * Ansible no corre en Windows nativo (ver AnsibleVaultCipher/ARCHITECTURE.md),
 * asi que "instala Node tu mismo" tampoco es una salida aceptable para buena
 * parte de los usuarios reales de este plugin -> en vez de asumir `node` en
 * el PATH, se descarga y cachea un runtime de Node privado por usuario
 * (NodeRuntimeProvisioner), un patron ya usado por varios plugins de
 * IntelliJ que envuelven herramientas externas (ej. Rust/rust-analyzer).
 *
 * Bundlear el binario de Node para TODAS las plataformas dentro del .zip
 * del plugin no es viable: cada binario pesa ~80-110MB y no hay forma de
 * publicar un solo artifact por-plataforma en el Marketplace, asi que
 * bundlear las 6 combinaciones infla el plugin a ~500MB+ para una feature
 * que la mayoria de instalaciones ni va a tocar en el mismo dia. Descargar
 * bajo demanda, verificado por checksum, es la unica opcion que mantiene el
 * .zip liviano.
 *
 * El servidor de lenguaje (`@ansible/ansible-language-server` + su arbol de
 * dependencias, ~16MB) SI se bundlea directo en el plugin -> confirmado a
 * mano que no tiene addons nativos (`find node_modules -name "*.node"` vacio),
 * es JS puro, portable entre sistemas operativos sin recompilar.
 */
enum class NodePlatform(
    val distSuffix: String,
    val archiveFormat: ArchiveFormat,
    val sha256: String,
    /** Ruta del binario `node`/`node.exe` dentro del arbol ya descomprimido del archive. */
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

    /** Nombre del binario ya extraido y cacheado (sin el resto del arbol de Node). */
    val cachedBinaryName: String
        get() = if (this == WINDOWS_X64 || this == WINDOWS_ARM64) "node.exe" else "node"

    companion object {
        /** Version de Node pineada -> checksums arriba corresponden exactamente a esta. */
        const val NODE_VERSION = "24.18.0"

        fun downloadUrl(platform: NodePlatform): String =
            "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-${platform.distSuffix}"

        /**
         * Deteccion inyectable (no lee System.getProperty directo) para poder
         * testear las 6 combinaciones sin depender del sistema operativo que
         * de casualidad este corriendo el test.
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
