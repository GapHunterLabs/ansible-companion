package dev.gaphunter.ansiblecompanion.runtime

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests the full flow (download -> checksum -> extraction -> cache)
 * against a real local HTTP server, not against nodejs.org -> fast,
 * deterministic, and doesn't depend on having Internet to run the tests.
 */
class NodeRuntimeProvisionerTest {

    private lateinit var server: HttpServer
    private var requestCount = AtomicInteger(0)
    private lateinit var fakeArchiveBytes: ByteArray
    private lateinit var baseUrl: String

    @Before
    fun startFakeServer() {
        fakeArchiveBytes = buildFakeZip("node-v1.0.0-fake/node.exe" to "fake-node-binary-content".toByteArray())
        requestCount.set(0)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/node.zip") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(200, fakeArchiveBytes.size.toLong())
            exchange.responseBody.use { it.write(fakeArchiveBytes) }
        }
        server.createContext("/missing.zip") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.responseBody.close()
        }
        server.createContext("/no-such-entry.zip") { exchange ->
            val emptyZip = buildFakeZip("node-v1.0.0-fake/README.md" to "no node here".toByteArray())
            exchange.sendResponseHeaders(200, emptyZip.size.toLong())
            exchange.responseBody.use { it.write(emptyZip) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stopFakeServer() {
        server.stop(0)
    }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun happyPathDownloadsVerifiesExtractsAndCaches() {
        val cacheRoot = Files.createTempDirectory("node-cache")
        val provisioner = NodeRuntimeProvisioner(cacheRoot)
        val artifact = NodeArtifact(
            downloadUrl = "$baseUrl/node.zip",
            sha256 = sha256Of(fakeArchiveBytes),
            archiveFormat = ArchiveFormat.ZIP,
            binaryPathInArchive = "node.exe",
            cachedBinaryName = "node.exe",
        )

        val path = provisioner.ensureProvisioned(artifact, cacheKey = "test-happy-path")

        assertTrue(Files.exists(path))
        assertArrayEquals("fake-node-binary-content".toByteArray(), Files.readAllBytes(path))
        assertEquals(1, requestCount.get())
    }

    @Test
    fun secondCallUsesCacheAndDoesNotHitServerAgain() {
        val cacheRoot = Files.createTempDirectory("node-cache")
        val provisioner = NodeRuntimeProvisioner(cacheRoot)
        val artifact = NodeArtifact(
            downloadUrl = "$baseUrl/node.zip",
            sha256 = sha256Of(fakeArchiveBytes),
            archiveFormat = ArchiveFormat.ZIP,
            binaryPathInArchive = "node.exe",
            cachedBinaryName = "node.exe",
        )

        val firstPath = provisioner.ensureProvisioned(artifact, cacheKey = "test-cache-reuse")
        val secondPath = provisioner.ensureProvisioned(artifact, cacheKey = "test-cache-reuse")

        assertEquals(firstPath, secondPath)
        assertEquals("cache hit must not re-download", 1, requestCount.get())
    }

    @Test
    fun checksumMismatchThrowsAndDoesNotLeaveABinaryBehind() {
        val cacheRoot = Files.createTempDirectory("node-cache")
        val provisioner = NodeRuntimeProvisioner(cacheRoot)
        val artifact = NodeArtifact(
            downloadUrl = "$baseUrl/node.zip",
            sha256 = "0".repeat(64), // deliberately wrong
            archiveFormat = ArchiveFormat.ZIP,
            binaryPathInArchive = "node.exe",
            cachedBinaryName = "node.exe",
        )

        try {
            provisioner.ensureProvisioned(artifact, cacheKey = "test-checksum-mismatch")
            fail("expected a NodeProvisioningException for an invalid checksum")
        } catch (e: NodeProvisioningException) {
            assertTrue(e.message!!.contains("checksum", ignoreCase = true))
        }

        val binaryPath = cacheRoot.resolve("test-checksum-mismatch").resolve("node.exe")
        assertFalse("an archive with an invalid checksum must never be left extracted on disk", Files.exists(binaryPath))
    }

    @Test
    fun httpErrorThrowsClearException() {
        val cacheRoot = Files.createTempDirectory("node-cache")
        val provisioner = NodeRuntimeProvisioner(cacheRoot)
        val artifact = NodeArtifact(
            downloadUrl = "$baseUrl/missing.zip",
            sha256 = "0".repeat(64),
            archiveFormat = ArchiveFormat.ZIP,
            binaryPathInArchive = "node.exe",
            cachedBinaryName = "node.exe",
        )

        try {
            provisioner.ensureProvisioned(artifact, cacheKey = "test-http-404")
            fail("expected a NodeProvisioningException for an HTTP 404")
        } catch (e: NodeProvisioningException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun missingEntryInOtherwiseValidArchiveThrows() {
        val cacheRoot = Files.createTempDirectory("node-cache")
        val provisioner = NodeRuntimeProvisioner(cacheRoot)
        val noEntryZip = buildFakeZip("node-v1.0.0-fake/README.md" to "no node here".toByteArray())
        val artifact = NodeArtifact(
            downloadUrl = "$baseUrl/no-such-entry.zip",
            sha256 = sha256Of(noEntryZip),
            archiveFormat = ArchiveFormat.ZIP,
            binaryPathInArchive = "node.exe",
            cachedBinaryName = "node.exe",
        )

        try {
            provisioner.ensureProvisioned(artifact, cacheKey = "test-missing-entry")
            fail("expected a NodeProvisioningException for a missing entry")
        } catch (e: NodeProvisioningException) {
            assertTrue(e.message!!.contains("node.exe"))
        }
    }

    private fun buildFakeZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
