package dev.gaphunter.ansiblecompanion.security

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.io.File

/**
 * The real pipeline (`computeFindings`, with real PSI extraction) against the demo fixture
 * read from disk -- the headless equivalent of opening it in `runIde`.
 */
class SafetyProfileFixtureTest : BasePlatformTestCase() {

    private val demoFixture = File("demo/roles/webserver/tasks/safety_examples.yml")

    private fun load(): PsiFile {
        assertTrue("fixture not found at ${demoFixture.absolutePath}", demoFixture.isFile)
        return myFixture.configureByText("main.yml", demoFixture.readText())
    }

    private fun findingsFor(taskName: String): List<SecurityFinding>? {
        val file = load()
        val item = PsiTreeUtil.findChildrenOfType(file, YAMLSequenceItem::class.java).firstOrNull { candidate ->
            candidate.keysValues.any { it.keyText == "name" && (it.value as? YAMLScalar)?.textValue == taskName }
        } ?: error("no task named '$taskName' in the fixture")
        return AnsibleSecurityAnnotator().computeFindings(item, isBareRoleTasksFile = true)?.second
    }

    private fun assertSingle(taskName: String, kind: String) {
        val findings = findingsFor(taskName)
        assertNotNull("'$taskName' must render a warning", findings)
        assertEquals("'$taskName' should show exactly one warning", listOf(kind), findings!!.map { it.kind })
    }

    private fun assertNone(taskName: String) = assertNull("'$taskName' must NOT be flagged", findingsFor(taskName))

    fun testPackageLatestPositives() {
        assertSingle("upgrade nginx on every run", "PACKAGE_LATEST")
        assertSingle("upgrade with dnf, extra packages allowed", "PACKAGE_LATEST")
        assertSingle("old key=value style, latest", "PACKAGE_LATEST")
        assertSingle("pip latest", "PACKAGE_LATEST")
    }

    fun testLatestPositive() = assertSingle("checkout the moving head", "LATEST_VERSION")

    fun testAvoidImplicitPositives() {
        assertSingle("write a dict with an implicit conversion", "AVOID_IMPLICIT_COPY_CONTENT")
        assertSingle("write a list with an implicit conversion (block style)", "AVOID_IMPLICIT_COPY_CONTENT")
    }

    fun testTraps() {
        for (name in listOf(
            "pinned package (NOT flagged)", "safe upgrade only, dnf (NOT flagged)", "safe upgrade only, apt (NOT flagged)",
            "state chosen by a variable (NOT flagged)", "pinned checkout (NOT flagged)", "explicit conversion (NOT flagged)",
            "block scalar content (NOT flagged)",
        )) assertNone(name)
    }
}
