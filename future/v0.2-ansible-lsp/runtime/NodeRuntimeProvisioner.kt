package dev.gaphunter.ansiblecompanion.runtime

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Todo lo necesario para dejar un binario de Node usable en disco, sin
 * pedirle al usuario que instale nada: cache-si-ya-esta -> descargar ->
 * verificar checksum ANTES de tocar el contenido -> extraer solo el
 * binario -> marcar completo. Nunca se ejecuta ni se confia en un archive
 * cuyo SHA-256 no coincida con el pineado en NodePlatform.
 *
 * `NodeArtifact` separa los datos de "que descargar/verificar" de
 * `NodePlatform` (que sigue siendo la fuente de verdad en produccion) para
 * poder testear el flujo completo (descarga -> checksum -> extraccion ->
 * cache) contra un servidor HTTP local falso, sin depender de Internet ni
 * de los checksums reales de Node (que por diseño no se pueden falsificar
 * en un test).
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
     * @param cacheKey subcarpeta bajo `cacheRoot` para esta combinacion de
     *   version+plataforma -> aisla el cache si algun dia se pinea otra
     *   version de Node sin pisar la anterior.
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
                    "No se encontro '${artifact.binaryPathInArchive}' dentro de ${artifact.downloadUrl}"
                )
            }

            binaryPath.toFile().setExecutable(true)
            Files.writeString(markerPath, "ok")
        } catch (e: Exception) {
            Files.deleteIfExists(binaryPath)
            throw if (e is NodeProvisioningException) e else NodeProvisioningException("Provisioning de Node fallo: ${e.message}", e)
        } finally {
            Files.deleteIfExists(tempArchive)
        }
        return binaryPath
    }

    /**
     * Los entries de zip/tar incluyen el folder versionado de primer nivel
     * (ej. "node-v24.18.0-linux-x64/bin/node") -> se descarta ese primer
     * segmento y se compara el resto exacto contra la ruta esperada, en vez
     * de un `endsWith` que podria dar falso positivo con otro archivo del
     * mismo nombre en una subcarpeta distinta.
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
            throw NodeProvisioningException("No se pudo descargar $url: ${e.message}", e)
        }
        if (response.statusCode() != 200) {
            throw NodeProvisioningException("Descarga de Node fallo: HTTP ${response.statusCode()} desde $url")
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
                "Checksum de Node no coincide (esperado $expectedSha256, obtenido $actual) -> descarga descartada"
            )
        }
    }
}
