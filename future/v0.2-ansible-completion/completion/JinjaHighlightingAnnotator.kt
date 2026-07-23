package dev.gaphunter.ansiblecompanion.completion

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.gaphunter.ansiblecompanion.detection.AnsibleYamlFileType

/**
 * Highlights `{{ }}`/`{% %}`/`{# #}` Jinja2 regions inside Ansible YAML
 * scalars (complaint #3, the paid v0.2 feature) using
 * [JinjaExpressionDetector]'s pure text scan -- no real Jinja2 engine,
 * no dependency on Python/Ansible being installed.
 *
 * Deliberately operates on generic [PsiElement] leaves rather than the
 * YAML plugin's own `YAMLScalar` PSI type: scanning raw leaf text avoids
 * needing to reason about YAML's quoting/escaping rules (a double-quoted
 * scalar's "logical" text and its raw source text differ), and it means
 * this file has no compile-time dependency on YAML-plugin-specific PSI
 * classes at all -- only `AnsibleYamlFileType` (from `detection/`) needs
 * that. `element.firstChild != null` is used to skip composite (non-leaf)
 * nodes, since visiting every level of the PSI tree would re-scan and
 * re-annotate the same text repeatedly.
 *
 * Register with `language="yaml"` on the `<annotator>` extension when
 * reactivating, so the platform only ever invokes this for YAML files
 * instead of checking `containingFile.fileType` against every language.
 */
object AnsibleHighlighting {
    val JINJA_EXPRESSION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "ANSIBLE_JINJA_EXPRESSION",
        DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR,
    )
}

/**
 * `LicensingFacade` calls aren't free -- caching for a short TTL avoids
 * checking on every single leaf element of every highlighting pass
 * (potentially every keystroke) while still picking up a fresh purchase
 * within a minute, well inside JetBrains's own "checked at least once a
 * day" baseline.
 */
private object LicenseCache {
    private const val TTL_MS = 60_000L
    private var cachedValue: Boolean = false
    private var lastCheckedAtMs: Long = 0L

    fun isLicensedNow(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckedAtMs > TTL_MS) {
            cachedValue = CheckLicense.isLicensed() == true
            lastCheckedAtMs = now
        }
        return cachedValue
    }
}

class JinjaHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.firstChild != null) return
        if (element.containingFile?.fileType != AnsibleYamlFileType) return
        if (!LicenseCache.isLicensedNow()) return

        val elementStart = element.textRange.startOffset
        val scan = JinjaExpressionDetector.scan(element.text)

        for (region in scan.regions) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(elementStart + region.range.first, elementStart + region.range.last + 1))
                .textAttributes(AnsibleHighlighting.JINJA_EXPRESSION)
                .create()
        }
        for (issue in scan.issues) {
            holder.newAnnotation(HighlightSeverity.WARNING, issue.message)
                .range(TextRange(elementStart + issue.offset, (elementStart + issue.offset + 2).coerceAtMost(element.textRange.endOffset)))
                .create()
        }
    }
}
