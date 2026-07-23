package dev.gaphunter.ansiblecompanion.runtime

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Everything needed to leave a usable Node binary on disk, without asking
 * the user to install anything: cache-if-already-there -> download ->
 * verify checksum BEFORE touching the content -> extract only the binary
 * -> mark complete. Never runs or trusts an archive whose SHA-256 doesn't
 * match the one pinned in NodePlatform.
 *
 * `NodeArtifact` separates "what to download/verify" from `NodePlatform`
 * (which remains the source of truth in production) so the full flow
 * (download -> checksum -> extraction -> cache) can be tested against a
 * fake local HTTP server, without depending on the Internet or on Node's
 * real checksums (which by design can't be faked in a test).
 */
data class NodeArtifact(
    val downloadUrl: String,
    val sha256: String,
    val archiveFormat: ArchiveFormat,
    val binaryPathInArchive: String,
    val cachedBinaryName: String,
)

fun NodePlatform.toArtifact(): NodeArtifact = NodeArtifact(
    downloadUrl = NodePlatform.downloadUrl(this),
    sha256 = sha256,
    archiveFormat = archiveFormat,
    binaryPathInArchive = binaryPathInArchive,
    cachedBinaryName = cachedBinaryName,
)

class NodeProvisioningException(message: String, cause: Throwable? = null) : Exception(message, cause)

class NodeRuntimeProvisioner(
    private val cacheRoot: Path,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) {

    fun ensureProvisioned(platform: NodePlatform): Path =
        ensureProvisioned(platform.toArtifact(), cacheKey = "${NodePlatform.NODE_VERSION}/${platform.name.lowercase()}")

    /**
     * @param cacheKey subfolder under `cacheRoot` for this version+platform
     *   combination -> isolates the cache if some future version pins a
     *   different Node version without overwriting the previous one.
     */
    fun ensureProvisioned(artifact: NodeArtifact, cacheKey: String): Path {
        val platformDir = cacheRoot.resolve(cacheKey)
        val binaryPath = platformDir.resolve(artifact.cachedBinaryName)
        val markerPath = platformDir.resolve(".provisioned")

        if (Files.exists(markerPath) && Files.exists(binaryPath)) {
            return binaryPath
        }

        Files.createDirectories(platformDir)
        val tempArchive = Files.createTempFile(platformDir, "download-", ".tmp")
        try {
            download(artifact.downloadUrl, tempArchive)
            verifyChecksum(tempArchive, artifact.sha256)

            val found = ArchiveExtractor.extractSingleFile(tempArchive, artifact.archiveFormat, binaryPath) { entryName ->
                matchesBinaryEntry(entryName, artifact.binaryPathInArchive)
            }
            if (!found) {
                throw NodeProvisioningException(
                    "Could not find '${artifact.binaryPathInArchive}' inside ${artifact.downloadUrl}"
                )
            }

            binaryPath.toFile().setExecutable(true)
            Files.writeString(markerPath, "ok")
        } catch (e: Exception) {
            Files.deleteIfExists(binaryPath)
            throw if (e is NodeProvisioningException) e else NodeProvisioningException("Node provisioning failed: ${e.message}", e)
        } finally {
            Files.deleteIfExists(tempArchive)
        }
        return binaryPath
    }

    /**
     * zip/tar entries include the top-level versioned folder (e.g.
     * "node-v24.18.0-linux-x64/bin/node") -> that first segment is
     * discarded and the rest is compared exactly against the expected
     * path, instead of an `endsWith` that could false-positive on another
     * file with the same name in a different subfolder.
     */
    private fun matchesBinaryEntry(entryName: String, expectedSuffix: String): Boolean {
        val normalized = entryName.replace('\\', '/').trimEnd('/')
        val withoutTopLevelDir = normalized.substringAfter('/', missingDelimiterValue = normalized)
        return withoutTopLevelDir == expectedSuffix
    }

    private fun download(url: String, destination: Path) {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination))
        } catch (e: Exception) {
            throw NodeProvisioningException("Could not download $url: ${e.message}", e)
        }
        if (response.statusCode() != 200) {
            throw NodeProvisioningException("Node download failed: HTTP ${response.statusCode()} from $url")
        }
    }

    private fun verifyChecksum(file: Path, expectedSha256: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw NodeProvisioningException(
                "Node checksum mismatch (expected $expectedSha256, got $actual) -> download discarded"
            )
        }
    }
}
