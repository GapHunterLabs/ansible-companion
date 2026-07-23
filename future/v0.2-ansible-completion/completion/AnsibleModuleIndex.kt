package dev.gaphunter.ansiblecompanion.completion

import java.io.InputStream

/**
 * Static, bundled index of `ansible.builtin.*` module names + short
 * descriptions -> no dependency on Node, a language server, or a real
 * Ansible/Python install to offer FQCN-aware completion (complaint #2).
 * This directly replaces the abandoned LSP4IJ/ansible-language-server
 * approach (see the README in this folder for why): that server refuses
 * to actually run outside WSL on Windows and shells out to `ansible`,
 * `ansible-lint`, `ansible-config` for exactly this kind of data — none
 * of which work for this plugin's real users. This index is just data,
 * fetched once from each module's DOCUMENTATION docstring in the real
 * `ansible/ansible` GitHub repository (the modules directory under
 * lib/ansible, stable-2.17 branch) and bundled as a plugin resource, so
 * it works identically on every OS with zero runtime dependency.
 */
data class AnsibleModule(
    val shortName: String,
    val description: String,
) {
    val fqcn: String get() = "ansible.builtin.$shortName"
}

object AnsibleModuleIndex {
    private const val RESOURCE_PATH = "/ansible_builtin_modules.json"

    val modules: List<AnsibleModule> by lazy {
        val stream = javaClass.getResourceAsStream(RESOURCE_PATH)
            ?: error("Bundled resource $RESOURCE_PATH is missing from the plugin jar")
        parse(stream)
    }

    /** Exposed separately from [modules] so tests can feed a synthetic JSON without touching the classpath. */
    fun parse(stream: InputStream): List<AnsibleModule> {
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
        return parseJsonObject(text)
            .map { (name, description) -> AnsibleModule(name, description) }
            .sortedBy { it.shortName }
    }

    /**
     * Hand-rolled minimal JSON object parser (flat string->string map only,
     * which is exactly the shape of ansible_builtin_modules.json) instead
     * of adding a JSON library dependency -> same "no new dependency for
     * a narrow, well-defined format" call as AnsibleVaultCipher/ArchiveExtractor.
     */
    private fun parseJsonObject(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var i = 0
        fun skipWhitespace() { while (i < text.length && text[i].isWhitespace()) i++ }
        fun parseString(): String {
            check(text[i] == '"')
            i++
            val sb = StringBuilder()
            while (text[i] != '"') {
                if (text[i] == '\\') {
                    i++
                    when (text[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'u' -> {
                            val code = text.substring(i + 1, i + 5).toInt(16)
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> sb.append(text[i])
                    }
                } else {
                    sb.append(text[i])
                }
                i++
            }
            i++ // closing quote
            return sb.toString()
        }

        skipWhitespace()
        check(text[i] == '{')
        i++
        skipWhitespace()
        if (text[i] == '}') return result
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            check(text[i] == ':')
            i++
            skipWhitespace()
            val value = parseString()
            result[key] = value
            skipWhitespace()
            when (text[i]) {
                ',' -> { i++; continue }
                '}' -> break
                else -> error("Unexpected character '${text[i]}' at offset $i")
            }
        }
        return result
    }
}
