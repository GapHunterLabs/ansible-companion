package dev.gaphunter.ansiblecompanion

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.ansiblecompanion.migration.AnsibleLegacyFactVariableInspection
import dev.gaphunter.ansiblecompanion.security.AnsibleSecurityAnnotator
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.io.File

/**
 * Precision run on a REAL corpus of public Ansible content (role files: tasks, handlers, defaults, vars,
 * meta, molecule), through the real pipeline -- the Pro safety checks via `computeFindings` and the free
 * migration check through the highlighting daemon. Skipped unless `-Pansible.corpus=<dir>` is given, so a
 * normal `./gradlew test` never touches it; the corpus lives outside the repo and is never committed.
 *
 * Writes a tab-separated report `kind, file, line, message` (`-Pansible.corpus.report=<file>`): every row is a
 * finding to review by hand, and the aim is that none of them is a false positive.
 */
class AnsibleCorpusPrecisionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AnsibleLegacyFactVariableInspection::class.java)
    }

    fun testCorpus() {
        val root = System.getProperty("ansible.corpus")?.let(::File) ?: return
        val reportFile = File(System.getProperty("ansible.corpus.report") ?: "ansible-corpus-report.tsv")
        val files = ArrayList<File>()
        java.nio.file.Files.walk(root.toPath()).use { stream ->
            stream.forEach { if (java.nio.file.Files.isRegularFile(it)) files.add(it.toFile()) }
        }
        files.sortBy { it.path }

        val report = StringBuilder()
        var scanned = 0
        var skipped = 0
        val counts = sortedMapOf<String, Int>()
        for (file in files) {
            val relative = file.relativeTo(root).path.replace('\\', '/')
            // <owner>__<repo>/<tasks|handlers|...>/x.yml -> roles/<repo>/<tasks|...>/x.yml, so path detection treats it as Ansible.
            val parts = relative.split('/')
            val insideRole = parts.drop(1).joinToString("/")
            val projectPath = if (parts.getOrNull(1) in setOf("tasks", "handlers", "defaults", "vars", "meta")) "roles/${parts[0]}/$insideRole" else "playbooks/${parts[0]}/$insideRole"
            val text = try { file.readText() } catch (e: Exception) { skipped++; continue }
            val psi = try {
                myFixture.addFileToProject(projectPath, text)
            } catch (e: Throwable) {
                skipped++
                continue
            }
            scanned++
            val bare = parts.getOrNull(1) in setOf("tasks", "handlers")
            val document = psi.viewProvider.document

            for (item in PsiTreeUtil.findChildrenOfType(psi, YAMLSequenceItem::class.java)) {
                val findings = try { AnsibleSecurityAnnotator().computeFindings(item, bare)?.second } catch (e: Throwable) { null } ?: continue
                for (finding in findings) {
                    counts.merge(finding.kind, 1, Int::plus)
                    val line = (document?.getLineNumber(item.textRange.startOffset) ?: -1) + 1
                    report.append(finding.kind).append('\t').append(relative).append('\t').append(line).append('\t').append(finding.message.take(110)).append('\n')
                }
            }

            try {
                myFixture.configureFromExistingVirtualFile(psi.virtualFile)
                val doc = myFixture.editor.document
                for (info in myFixture.doHighlighting()) {
                    if (info.description?.contains("top-level fact variable") != true) continue
                    counts.merge("LEGACY_FACT", 1, Int::plus)
                    val text2 = doc.getText(com.intellij.openapi.util.TextRange(info.startOffset, info.endOffset))
                    report.append("LEGACY_FACT").append('\t').append(relative).append('\t').append(doc.getLineNumber(info.startOffset) + 1)
                        .append('\t').append(text2).append('\n')
                }
            } catch (e: Throwable) {
                // An IDE-internal failure on one file (the test classpath's Kotlin stdlib is older than the IDE's) is skipped, not counted.
            }
        }
        report.append("# files=").append(files.size).append(" scanned=").append(scanned).append(" skipped=").append(skipped)
            .append(" counts=").append(counts).append('\n')
        reportFile.writeText(report.toString())
    }
}
