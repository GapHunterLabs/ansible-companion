package dev.gaphunter.ansiblecompanion.completion

/**
 * Addresses complaint #3 (badly parsed Jinja2): finds `{{ }}`/`{% %}`/
 * `{# #}` regions inside a chunk of YAML scalar text, purely as text --
 * no dependency on a real Jinja2 engine or Python. Deliberately does not
 * try to understand Jinja2 grammar beyond delimiter matching (no
 * expression parsing, no filter/tag validation) -- that would need a
 * real Jinja2 parser; this only has to tell the difference between
 * template syntax and plain YAML so the two can be highlighted
 * differently and grossly malformed template blocks can be flagged,
 * which is what the incumbent's YAML parser gets wrong.
 *
 * Pure and testable on purpose (no PSI/VirtualFile), same split as
 * AnsibleFileDetector -> JinjaHighlightingAnnotator wires this to the
 * real editor.
 */
data class JinjaRegion(val range: IntRange, val kind: Kind) {
    enum class Kind { EXPRESSION, STATEMENT, COMMENT }
}

data class JinjaSyntaxIssue(val offset: Int, val message: String)

object JinjaExpressionDetector {

    data class ScanResult(val regions: List<JinjaRegion>, val issues: List<JinjaSyntaxIssue>)

    private class Delimiter(val open: String, val close: String, val kind: JinjaRegion.Kind)

    private val DELIMITERS = listOf(
        Delimiter("{{", "}}", JinjaRegion.Kind.EXPRESSION),
        Delimiter("{%", "%}", JinjaRegion.Kind.STATEMENT),
        Delimiter("{#", "#}", JinjaRegion.Kind.COMMENT),
    )

    fun scan(text: String): ScanResult {
        val regions = mutableListOf<JinjaRegion>()
        val issues = mutableListOf<JinjaSyntaxIssue>()
        var i = 0

        while (i < text.length) {
            val opener = DELIMITERS.firstOrNull { text.startsWith(it.open, i) }
            if (opener != null) {
                val closeIdx = text.indexOf(opener.close, i + opener.open.length)
                if (closeIdx == -1) {
                    issues += JinjaSyntaxIssue(i, "Unterminated Jinja2 ${opener.kind.name.lowercase()} (missing '${opener.close}')")
                    break // the rest of the text is inside the unterminated block, nothing more to find
                }
                regions += JinjaRegion(i..(closeIdx + opener.close.length - 1), opener.kind)
                i = closeIdx + opener.close.length
                continue
            }

            val strayCloser = DELIMITERS.firstOrNull { text.startsWith(it.close, i) }
            if (strayCloser != null) {
                issues += JinjaSyntaxIssue(i, "Unexpected '${strayCloser.close}' without a matching '${strayCloser.open}'")
                i += strayCloser.close.length
                continue
            }

            i++
        }

        return ScanResult(regions, issues)
    }
}
