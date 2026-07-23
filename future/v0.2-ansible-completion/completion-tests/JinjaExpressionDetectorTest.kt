package dev.gaphunter.ansiblecompanion.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JinjaExpressionDetectorTest {

    @Test
    fun findsASimpleExpression() {
        val result = JinjaExpressionDetector.scan("Hello {{ name }}!")

        assertEquals(1, result.regions.size)
        assertEquals(JinjaRegion.Kind.EXPRESSION, result.regions[0].kind)
        assertEquals("{{ name }}", "Hello {{ name }}!".substring(result.regions[0].range.first, result.regions[0].range.last + 1))
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun findsAStatementBlock() {
        val result = JinjaExpressionDetector.scan("{% if x %}yes{% endif %}")

        assertEquals(2, result.regions.size)
        assertTrue(result.regions.all { it.kind == JinjaRegion.Kind.STATEMENT })
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun findsAComment() {
        val result = JinjaExpressionDetector.scan("{# not rendered #}")

        assertEquals(1, result.regions.size)
        assertEquals(JinjaRegion.Kind.COMMENT, result.regions[0].kind)
    }

    @Test
    fun findsMultipleMixedRegionsInOrder() {
        val text = "{{ a }} and {% b %}"
        val result = JinjaExpressionDetector.scan(text)

        assertEquals(2, result.regions.size)
        assertEquals(JinjaRegion.Kind.EXPRESSION, result.regions[0].kind)
        assertEquals(JinjaRegion.Kind.STATEMENT, result.regions[1].kind)
        assertTrue(result.regions[0].range.last < result.regions[1].range.first)
    }

    @Test
    fun adjacentRegionsAreNotMerged() {
        val result = JinjaExpressionDetector.scan("{{a}}{{b}}")

        assertEquals(2, result.regions.size)
        assertEquals(0..4, result.regions[0].range)
        assertEquals(5..9, result.regions[1].range)
    }

    @Test
    fun emptyExpressionIsStillOneRegion() {
        val result = JinjaExpressionDetector.scan("{{}}")

        assertEquals(1, result.regions.size)
        assertEquals(0..3, result.regions[0].range)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun plainTextHasNoRegionsOrIssues() {
        val result = JinjaExpressionDetector.scan("just a plain string, nothing special")

        assertTrue(result.regions.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun yamlFlowMappingIsNotMistakenForJinja() {
        // YAML's own {key: value} flow-mapping syntax must never be flagged --
        // this is exactly the false-positive that would repeat the incumbent's bug.
        val result = JinjaExpressionDetector.scan("{key: value, other: 1}")

        assertTrue(result.regions.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun yamlFlowSequenceIsNotMistakenForJinja() {
        val result = JinjaExpressionDetector.scan("[a, b, {c: 1}]")

        assertTrue(result.regions.isEmpty())
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun unterminatedExpressionIsReportedAsAnIssue() {
        val result = JinjaExpressionDetector.scan("{{ oops, no closing")

        assertTrue(result.regions.isEmpty())
        assertEquals(1, result.issues.size)
        assertEquals(0, result.issues[0].offset)
        assertTrue(result.issues[0].message.contains("Unterminated"))
    }

    @Test
    fun strayCloserWithoutOpenerIsReportedAsAnIssue() {
        val result = JinjaExpressionDetector.scan("oops }} no opener")

        assertTrue(result.regions.isEmpty())
        assertEquals(1, result.issues.size)
        assertEquals(5, result.issues[0].offset)
        assertTrue(result.issues[0].message.contains("Unexpected"))
    }

    @Test
    fun validRegionBeforeAnUnterminatedOneIsStillFound() {
        val result = JinjaExpressionDetector.scan("{{ good }} then {% broken")

        assertEquals(1, result.regions.size)
        assertEquals(JinjaRegion.Kind.EXPRESSION, result.regions[0].kind)
        assertEquals(1, result.issues.size)
    }

    @Test
    fun differentDelimiterTypesAreNotConfusedWithEachOther() {
        // "{{" closed by "%}" should NOT be treated as a valid EXPRESSION close --
        // it must be reported as unterminated instead of silently matching the wrong pair.
        val result = JinjaExpressionDetector.scan("{{ mismatched %}")

        assertTrue(result.regions.isEmpty())
        assertEquals(1, result.issues.size)
    }
}
