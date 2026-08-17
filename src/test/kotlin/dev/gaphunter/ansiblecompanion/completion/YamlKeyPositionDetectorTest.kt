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

    // Real-world edge cases beyond the original 9 (Round 8) -- block/rescue/
    // always nest a whole second task list one level deeper than a plain
    // module parameter does, and a task-level key can legally appear before
    // the module key instead of only after it (e.g. `when:` first).

    fun testANewTaskInsideABlockIsTopLevel() {
        // `block:`'s value is itself a task sequence, not a module
        // parameter -- a fresh line inside it is a brand-new task, same as
        // any other task-level position.
        assertTrue(isTopLevelAtCaret("- name: wrap\n  block:\n    - <caret>"))
    }

    fun testAnFqcnPrefixOnANewTaskInsideABlockIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- name: wrap\n  block:\n    - ansible.builtin.<caret>"))
    }

    fun testANewTaskInsideARescueIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- name: wrap\n  block:\n    - ansible.builtin.debug: {}\n  rescue:\n    - <caret>"))
    }

    fun testAModuleParameterInsideATaskThatIsItselfInsideABlockIsNotTopLevel() {
        // One level deeper than the block-nested task itself: a real module
        // parameter, same shape as the plain (non-block) nested-parameter
        // case already covered above.
        assertFalse(isTopLevelAtCaret("- name: wrap\n  block:\n    - name: inner\n      copy:\n        <caret>"))
    }

    fun testATaskLevelKeyTypedBeforeTheModuleKeyIsTopLevel() {
        // `when:` (or `loop:`, `tags:`, ...) can legally come before the
        // module key instead of only after it -- the detector shouldn't
        // assume the module key is always the first key in the task.
        assertTrue(isTopLevelAtCaret("- name: x\n  when: some_cond\n  <caret>"))
    }

    fun testANewTaskAfterASiblingWithALoopKeyIsTopLevel() {
        assertTrue(isTopLevelAtCaret("- name: x\n  ansible.builtin.copy:\n    src: a\n  loop: [1, 2]\n- <caret>"))
    }
}
