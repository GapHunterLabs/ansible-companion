package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The last three checks of ansible-lint's `safety` profile: package-latest, latest, avoid-implicit[copy-content]. */
class SafetyProfileDetectorsTest {

    private fun task(
        module: String?,
        params: Map<String, String> = emptyMap(),
        freeForm: String? = null,
        nonScalar: Set<String> = emptySet(),
    ) = AnsibleTask(
        module, params, hasNoLog = false, isGroupingConstruct = false,
        moduleFreeForm = freeForm, unquotedParameters = params.keys, nonScalarParameters = nonScalar,
    )

    // ---------- package-latest ----------

    private fun packageLatest(t: AnsibleTask) = PackageLatestDetector.check(t, noLogInherited = false)

    @Test
    fun flagsStateLatestOnPackageManagers() {
        for (module in listOf("ansible.builtin.apt", "apt", "ansible.builtin.dnf", "dnf", "ansible.builtin.yum", "ansible.builtin.package",
            "ansible.builtin.pip", "pip", "community.general.npm", "community.general.homebrew", "community.general.pacman", "zypper")) {
            val finding = packageLatest(task(module, mapOf("name" to "nginx", "state" to "latest")))
            assertEquals(module, "PACKAGE_LATEST", finding?.kind)
        }
    }

    @Test
    fun aPinnedOrPresentStateIsFine() {
        assertNull(packageLatest(task("ansible.builtin.apt", mapOf("name" to "nginx", "state" to "present"))))
        assertNull(packageLatest(task("ansible.builtin.apt", mapOf("name" to "nginx=1.24.0-1"))))
        assertNull(packageLatest(task("ansible.builtin.pip", mapOf("name" to "requests", "version" to "2.31.0"))))
    }

    @Test
    fun theDocumentedSafeguardsAllowLatest() {
        assertNull(packageLatest(task("ansible.builtin.dnf", mapOf("name" to "*", "state" to "latest", "update_only" to "true"))))
        assertNull(packageLatest(task("ansible.builtin.yum", mapOf("name" to "*", "state" to "latest", "update_only" to "yes"))))
        assertNull(packageLatest(task("ansible.builtin.apt", mapOf("name" to "*", "state" to "latest", "only_upgrade" to "true"))))
    }

    @Test
    fun aSafeguardSetToFalseStillFlags() {
        assertNotNull(packageLatest(task("ansible.builtin.dnf", mapOf("state" to "latest", "update_only" to "false"))))
        assertNotNull(packageLatest(task("ansible.builtin.apt", mapOf("state" to "latest", "only_upgrade" to "no"))))
    }

    @Test
    fun theSafeguardOfOneModuleDoesNotApplyToAnother() {
        // only_upgrade is apt's parameter; pip has no equivalent, so it can't make latest safe.
        assertNotNull(packageLatest(task("ansible.builtin.pip", mapOf("name" to "x", "state" to "latest", "only_upgrade" to "true"))))
    }

    @Test
    fun aJinjaStateIsNotEvaluated() {
        assertNull(packageLatest(task("ansible.builtin.apt", mapOf("name" to "nginx", "state" to "{{ package_state }}"))))
    }

    @Test
    fun theOldKeyValueStyleIsRead() {
        assertNotNull(packageLatest(task("yum", freeForm = "name=httpd state=latest")))
        assertNotNull(packageLatest(task("apt", freeForm = "name=nginx state=\"latest\" update_cache=yes")))
        assertNull(packageLatest(task("yum", freeForm = "name=httpd state=present")))
        assertNull(packageLatest(task("dnf", freeForm = "name=* state=latest update_only=yes")))
        assertNull(packageLatest(task("apt", freeForm = "name=* state=latest only_upgrade=true")))
    }

    @Test
    fun otherModulesAndForeignCollectionsAreLeftAlone() {
        assertNull(packageLatest(task("ansible.builtin.service", mapOf("name" to "nginx", "state" to "latest"))))
        assertNull(packageLatest(task("acme.tools.apt", mapOf("name" to "x", "state" to "latest"))))
        assertNull(packageLatest(task(null)))
    }

    @Test
    fun theMessageTellsHowToFixIt() {
        val dnf = packageLatest(task("ansible.builtin.dnf", mapOf("state" to "latest")))!!.message
        assertTrue(dnf, dnf.contains("update_only: true") && dnf.contains("state: present"))
        val pip = packageLatest(task("ansible.builtin.pip", mapOf("state" to "latest")))!!.message
        assertTrue(pip, pip.contains("state: present") && !pip.contains("update_only"))
    }

    // ---------- latest ----------

    private fun latest(t: AnsibleTask) = LatestVersionDetector.check(t, noLogInherited = false)

    @Test
    fun flagsAnExplicitHeadOrTip() {
        assertEquals("LATEST_VERSION", latest(task("ansible.builtin.git", mapOf("repo" to "https://x/y.git", "version" to "HEAD")))?.kind)
        assertEquals("LATEST_VERSION", latest(task("git", mapOf("repo" to "https://x/y.git", "version" to "HEAD")))?.kind)
        assertEquals("LATEST_VERSION", latest(task("community.general.hg", mapOf("repo" to "https://x/y", "revision" to "tip")))?.kind)
    }

    @Test
    fun aPinnedReferenceIsFine() {
        assertNull(latest(task("ansible.builtin.git", mapOf("repo" to "https://x/y.git", "version" to "v1.4.2"))))
        assertNull(latest(task("ansible.builtin.git", mapOf("repo" to "https://x/y.git", "version" to "9f2c1ab"))))
        assertNull(latest(task("community.general.hg", mapOf("repo" to "https://x/y", "revision" to "release-2"))))
    }

    @Test
    fun anOmittedVersionIsNotFlagged() {
        assertNull(latest(task("ansible.builtin.git", mapOf("repo" to "https://x/y.git"))))
    }

    @Test
    fun latestIgnoresOtherModules() {
        assertNull(latest(task("ansible.builtin.copy", mapOf("version" to "HEAD"))))
        assertNull(latest(task(null)))
    }

    // ---------- avoid-implicit[copy-content] ----------

    private fun implicit(t: AnsibleTask) = AvoidImplicitCopyContentDetector.check(t, noLogInherited = false)

    @Test
    fun flagsADictOrListContent() {
        assertEquals("AVOID_IMPLICIT_COPY_CONTENT", implicit(task("ansible.builtin.copy", mapOf("dest" to "/tmp/a"), nonScalar = setOf("content")))?.kind)
        assertEquals("AVOID_IMPLICIT_COPY_CONTENT", implicit(task("copy", nonScalar = setOf("content")))?.kind)
    }

    @Test
    fun aStringContentIsFine() {
        assertNull(implicit(task("ansible.builtin.copy", mapOf("content" to "{{ value | to_json }}", "dest" to "/tmp/a"))))
        assertNull(implicit(task("ansible.builtin.copy", mapOf("content" to "plain text", "dest" to "/tmp/a"))))
    }

    @Test
    fun onlyCopyContentCounts() {
        assertNull(implicit(task("ansible.builtin.template", nonScalar = setOf("content"))))
        assertNull(implicit(task("ansible.builtin.copy", nonScalar = setOf("src"))))
        assertNull(implicit(task(null)))
    }
}
