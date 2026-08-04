package dev.gaphunter.ansiblecompanion.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Real YAML PSI (not a hand-built tree) -- the exact ambiguity this is
 * guarding against (whether `copy:` followed by a more-indented blank
 * line already parses as having a mapping value) is parser behavior
 * that shouldn't be guessed, per this workspace's own
 * "verify against the real platform" discipline.
 */
class YamlKeyPositionDetectorTest : BasePlatformTestCase() {
    private fun isTopLevelAtCaret(text: String): Boolean {
        // Real completion always has a concrete (dummy-identifier) PSI leaf at
        // the cursor -- relying on findElementAt at a bare whitespace/EOF
        // position doesn't reliably reflect the parser's real nesting
        // structure (confirmed empirically: it does NOT for this grammar).
        // Replacing the <caret> marker with a real character instead of just
        // recording its position guarantees a real leaf there every time.
        val offset = text.indexOf("<caret>")
        require(offset >= 0) { "test text must contain <caret>" }
        myFixture.configureByText("playbook.yml", text.replace("<caret>", "X"))
        val position = myFixture.file.findElementAt(offset)!!
        return YamlKeyPositionDetector.isCompletingTopLevelTaskKey(position)
    }

    fun testBrandNewTaskWithNoColonTypedYetIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- <caret>"))
    }

    fun testTypingAFqcnPrefixOnABrandNewTaskIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- ansible.builtin.<caret>"))
    }

    fun testANewTaskKeyOnItsOwnLineAfterAnExistingSiblingIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- name: do something\n  <caret>"))
    }

    fun testTypingInsideAnAlreadyPresentTopLevelKeyIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- na<caret>me: do something"))
    }

    fun testANestedParameterKeyUnderAModuleIsNotTopLevel() {
        // The exact bug reproduction from AnsibleModuleCompletionContributor's
        // doc comment: `copy:` already has its own line, and the caret is on
        // a fresh, more-indented line under it -- a module-parameter position,
        // not a second task-level key.
        assertFalse(isTopLevelAtCaret("- name: do something\n  copy:\n    <caret>"))
    }

    fun testTypingTheNameOfANestedParameterKeyIsNotTopLevel() {
        assertFalse(isTopLevelAtCaret("- name: do something\n  copy:\n    sr<caret>c: foo"))
    }

    fun testTypingTheValueOfANestedParameterKeyIsNotTopLevel() {
        assertFalse(isTopLevelAtCaret("- name: do something\n  copy:\n    src: <caret>foo"))
    }

    fun testASecondNestingLevelUnderAModuleParameterIsNotTopLevel() {
        assertFalse(isTopLevelAtCaret("- name: do something\n  copy:\n    remote_src: true\n    owner:\n      <caret>"))
    }

    fun testOutsideAnyTaskSequenceIsNotTopLevel() {
        assertFalse(isTopLevelAtCaret("some_var: <caret>"))
    }
}
