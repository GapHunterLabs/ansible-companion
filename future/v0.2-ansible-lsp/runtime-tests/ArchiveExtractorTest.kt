package dev.gaphunter.ansiblecompanion.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {

    @Test
    fun findsTargetEntryInZipAmongOthers() {
        val zip = buildZip(
            "node-v24.18.0-win-x64/README.md" to "hi".toByteArray(),
            "node-v24.18.0-win-x64/node.exe" to "fake-node-binary".toByteArray(),
            "node-v24.18.0-win-x64/npm" to "fake-npm".toByteArray(),
        )
        val archiveFile = Files.createTempFile("test-archive", ".zip")
        val destination = Files.createTempDirectory("test-dest").resolve("node.exe")
        Files.write(archiveFile, zip)

        val found = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.ZIP, destination) { it == "node-v24.18.0-win-x64/node.exe" }

        assertTrue(found)
        assertArrayEquals("fake-node-binary".toByteArray(), Files.readAllBytes(destination))
    }

    @Test
    fun zipReturnsFalseWhenEntryMissing() {
        val zip = buildZip("some/other/file.txt" to "nope".toByteArray())
        val archiveFile = Files.createTempFile("test-archive", ".zip")
        val destination = Files.createTempDirectory("test-dest").resolve("node.exe")
        Files.write(archiveFile, zip)

        val found = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.ZIP, destination) { it.endsWith("node.exe") }

        assertFalse(found)
    }

    @Test
    fun findsTargetEntryInTarGzAmongOthersIncludingADirectory() {
        val tarGz = buildTarGz(
            TarFixtureEntry("node-v24.18.0-linux-x64/", '5', ByteArray(0)),
            TarFixtureEntry("node-v24.18.0-linux-x64/README.md", '0', "hi".toByteArray()),
            TarFixtureEntry("node-v24.18.0-linux-x64/bin/node", '0', "fake-node-elf-binary".toByteArray()),
            TarFixtureEntry("node-v24.18.0-linux-x64/bin/npm", '0', "fake-npm".toByteArray()),
        )
        val archiveFile = Files.createTempFile("test-archive", ".tar.gz")
        val destination = Files.createTempDirectory("test-dest").resolve("node")
        Files.write(archiveFile, tarGz)

        val found = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.TAR_GZ, destination) { it == "node-v24.18.0-linux-x64/bin/node" }

        assertTrue(found)
        assertArrayEquals("fake-node-elf-binary".toByteArray(), Files.readAllBytes(destination))
    }

    @Test
    fun tarGzReturnsFalseWhenEntryMissing() {
        val tarGz = buildTarGz(TarFixtureEntry("some/other/file.txt", '0', "nope".toByteArray()))
        val archiveFile = Files.createTempFile("test-archive", ".tar.gz")
        val destination = Files.createTempDirectory("test-dest").resolve("node")
        Files.write(archiveFile, tarGz)

        val found = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.TAR_GZ, destination) { it.endsWith("bin/node") }

        assertFalse(found)
    }

    @Test
    fun gnuLongNameEntryDoesNotDesyncSubsequentEntries() {
        val longName = "node-v24.18.0-linux-x64/" + "a".repeat(30) + "/" + "b".repeat(60) + "/very-long-nested-path.txt"
        assertTrue("fixture name must exceed the 100-byte ustar name field to exercise 'L' records", longName.length > 100)

        val tarGz = buildTarGz(
            TarFixtureEntry(longName, 'L', ByteArray(0), gnuLongNamePayload = longName),
            TarFixtureEntry("truncated-placeholder", '0', "decoy content".toByteArray()),
            TarFixtureEntry("node-v24.18.0-linux-x64/bin/node", '0', "fake-node-elf-binary".toByteArray()),
        )
        val archiveFile = Files.createTempFile("test-archive", ".tar.gz")
        val destinationLong = Files.createTempDirectory("test-dest").resolve("long")
        Files.write(archiveFile, tarGz)

        val foundLong = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.TAR_GZ, destinationLong) { it == longName }
        assertTrue("the GNU-long-named entry itself should still be reachable", foundLong)
        assertArrayEquals("decoy content".toByteArray(), Files.readAllBytes(destinationLong))

        val destinationNode = Files.createTempDirectory("test-dest2").resolve("node")
        val foundNode = ArchiveExtractor.extractSingleFile(archiveFile, ArchiveFormat.TAR_GZ, destinationNode) { it == "node-v24.18.0-linux-x64/bin/node" }
        assertTrue("the entry after the long-named one must still parse correctly", foundNode)
        assertArrayEquals("fake-node-elf-binary".toByteArray(), Files.readAllBytes(destinationNode))
    }

    // -- fixture builders ---------------------------------------------------

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
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

    private data class TarFixtureEntry(
        val name: String,
        val typeflag: Char,
        val content: ByteArray,
        /** When typeflag=='L', the actual content to write is this long name, not `content`. */
        val gnuLongNamePayload: String? = null,
    )

    private fun buildTarGz(vararg entries: TarFixtureEntry): ByteArray {
        val raw = ByteArrayOutputStream()
        for (entry in entries) {
            val body = entry.gnuLongNamePayload?.toByteArray(Charsets.UTF_8) ?: entry.content
            raw.write(tarHeader(entry.name, body.size, entry.typeflag))
            if (body.isNotEmpty()) raw.write(padTo512(body))
        }
        raw.write(ByteArray(1024)) // two zero blocks = end-of-archive marker
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(raw.toByteArray()) }
        return gz.toByteArray()
    }

    private fun tarHeader(name: String, size: Int, typeflag: Char): ByteArray {
        val header = ByteArray(512)
        fun writeField(offset: Int, value: ByteArray) {
            System.arraycopy(value, 0, header, offset, minOf(value.size, header.size - offset))
        }
        // Long names (>100 bytes) don't fit in the normal `name` field -> the ustar
        // format itself expects the GNU 'L' extension to be used in that case, so
        // truncating here is intentional (the real effective name comes from the
        // preceding 'L' entry).
        writeField(0, name.toByteArray(Charsets.UTF_8).copyOf(100))
        writeField(100, "0000644".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // mode
        writeField(108, "0000000".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // uid
        writeField(116, "0000000".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // gid
        writeField(124, String.format("%011o", size).toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // size
        writeField(136, "00000000000".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // mtime
        for (i in 148 until 156) header[i] = ' '.code.toByte() // checksum field = spaces while summing
        header[156] = typeflag.code.toByte()
        writeField(257, "ustar".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)) // magic
        writeField(263, "00".toByteArray(Charsets.US_ASCII)) // version

        var sum = 0
        for (b in header) sum += (b.toInt() and 0xFF)
        val checksumField = String.format("%06o", sum).toByteArray(Charsets.US_ASCII) + byteArrayOf(0, ' '.code.toByte())
        writeField(148, checksumField)
        return header
    }

    private fun padTo512(data: ByteArray): ByteArray {
        val remainder = data.size % 512
        if (remainder == 0) return data
        return data + ByteArray(512 - remainder)
    }
}
