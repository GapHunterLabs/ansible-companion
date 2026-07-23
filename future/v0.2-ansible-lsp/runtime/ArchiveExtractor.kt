package dev.gaphunter.ansiblecompanion.runtime

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Extracts a SINGLE file from a .zip or .tar.gz without decompressing the
 * rest of the tree -> the full Node runtime weighs ~150-200MB decompressed,
 * but the only thing this plugin needs is the `node`/`node.exe` binary (a
 * single file inside the tar/zip). Extracting just that keeps the cache
 * at ~80-110MB instead of duplicating npm/docs/headers that never get used.
 *
 * .tar.gz has no library in the Java stdlib (unlike .zip, which does have
 * `java.util.zip.ZipInputStream`) -> a minimal TAR reader (ustar/GNU
 * format) is hand-written instead of adding Apache Commons Compress as a
 * new dependency, the same call as AnsibleVaultCipher (hand-rolled PBKDF2
 * instead of a library).
 */
object ArchiveExtractor {

    /**
     * @param matcher receives the entry's path EXACTLY AS IT APPEARS in the
     *   archive (including the top-level versioned folder, e.g.
     *   "node-v24.18.0-linux-x64/bin/node") and decides whether it's the one
     *   being looked for.
     * @return true if the entry was found and extracted; false if the
     *   archive was exhausted without any match (the caller decides
     *   whether that's an error).
     */
    fun extractSingleFile(
        archiveFile: Path,
        format: ArchiveFormat,
        destination: Path,
        matcher: (String) -> Boolean,
    ): Boolean {
        Files.newInputStream(archiveFile).use { raw ->
            return when (format) {
                ArchiveFormat.ZIP -> extractFromZip(raw, matcher, destination)
                ArchiveFormat.TAR_GZ -> GZIPInputStream(raw).use { gz -> extractFromTar(gz, matcher, destination) }
            }
        }
    }

    private fun extractFromZip(input: InputStream, matcher: (String) -> Boolean, destination: Path): Boolean {
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && matcher(entry.name)) {
                    Files.createDirectories(destination.parent)
                    Files.newOutputStream(destination).use { out -> zis.copyTo(out) }
                    return true
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return false
    }

    // -- minimal TAR (ustar/GNU): only what's needed to locate and copy one entry ------

    private const val BLOCK_SIZE = 512

    private fun extractFromTar(input: InputStream, matcher: (String) -> Boolean, destination: Path): Boolean {
        var pendingLongName: String? = null
        val header = ByteArray(BLOCK_SIZE)

        while (true) {
            if (!readFully(input, header)) return false
            if (header.all { it == 0.toByte() }) return false // zero block = end of archive

            val typeflag = header[156].toInt().toChar()
            val size = parseOctal(header, 124, 12)
            val paddedSize = ((size + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE

            if (typeflag == 'L') {
                // GNU extension: this entry's content is the long name for the NEXT header.
                val nameBytes = ByteArray(size.toInt())
                readFully(input, nameBytes)
                skipExactly(input, paddedSize - size)
                pendingLongName = String(nameBytes, Charsets.UTF_8).trimEnd { it.code == 0 }
                continue
            }

            val rawName = parseString(header, 0, 100)
            val prefix = parseString(header, 345, 155)
            val headerName = if (prefix.isEmpty()) rawName else "$prefix/$rawName"
            val effectiveName = pendingLongName ?: headerName
            pendingLongName = null

            val isRegularFile = typeflag == '0' || typeflag.code == 0
            if (isRegularFile && matcher(effectiveName)) {
                Files.createDirectories(destination.parent)
                Files.newOutputStream(destination).use { out -> copyExactly(input, out, size) }
                skipExactly(input, paddedSize - size)
                return true
            }

            skipExactly(input, paddedSize)
        }
    }

    private fun parseOctal(bytes: ByteArray, offset: Int, length: Int): Long {
        val text = String(bytes, offset, length, Charsets.US_ASCII).trim { it <= ' ' }
        if (text.isEmpty()) return 0L
        return text.toLong(8)
    }

    private fun parseString(bytes: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { bytes[it] == 0.toByte() } ?: (offset + length)
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return read > 0 // EOF mid-block -> truncated archive, treat as end
            read += n
        }
        return true
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(minOf(count, 8192L).coerceAtLeast(1L).toInt())
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (n < 0) return
            remaining -= n
        }
    }

    private fun copyExactly(input: InputStream, output: OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(minOf(count, 65536L).coerceAtLeast(1L).toInt())
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (n < 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }
}
