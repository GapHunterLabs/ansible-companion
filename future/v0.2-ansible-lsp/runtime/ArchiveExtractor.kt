package dev.gaphunter.ansiblecompanion.runtime

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Extrae UN solo archivo de un .zip o .tar.gz sin descomprimir el resto del
 * arbol -> el runtime de Node completo pesa ~150-200MB descomprimido, pero
 * lo unico que este plugin necesita es el binario `node`/`node.exe` (un
 * unico archivo dentro del tar/zip). Extraer solo eso deja el cache en
 * ~80-110MB en vez de duplicar npm/docs/headers que nunca se usan.
 *
 * El .tar.gz no tiene una libreria en la stdlib de Java (a diferencia de
 * .zip, que si tiene `java.util.zip.ZipInputStream`) -> se implementa un
 * lector TAR minimo (formato ustar/GNU) a mano en vez de agregar Apache
 * Commons Compress como dependencia nueva, mismo criterio que
 * AnsibleVaultCipher (PBKDF2 a mano en vez de una libreria).
 */
object ArchiveExtractor {

    /**
     * @param matcher recibe el path del entry TAL COMO aparece en el archive
     *   (incluyendo el folder versionado de primer nivel, ej.
     *   "node-v24.18.0-linux-x64/bin/node") y decide si es el que se busca.
     * @return true si se encontro y extrajo el entry; false si se agoto el
     *   archive sin encontrar ningun match (el caller decide si eso es error).
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

    // -- TAR (ustar/GNU) minimo: solo lo necesario para ubicar y copiar un entry ------

    private const val BLOCK_SIZE = 512

    private fun extractFromTar(input: InputStream, matcher: (String) -> Boolean, destination: Path): Boolean {
        var pendingLongName: String? = null
        val header = ByteArray(BLOCK_SIZE)

        while (true) {
            if (!readFully(input, header)) return false
            if (header.all { it == 0.toByte() }) return false // bloque cero = fin de archive

            val typeflag = header[156].toInt().toChar()
            val size = parseOctal(header, 124, 12)
            val paddedSize = ((size + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE

            if (typeflag == 'L') {
                // Extension GNU: el contenido de este entry es el nombre largo del PROXIMO header.
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
            if (n < 0) return read > 0 // EOF a mitad de bloque -> archive truncado, tratar como fin
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
