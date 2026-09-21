package dev.gaphunter.ansiblecompanion.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RiskyShellPipeDetectorTest {

    private fun shell(
        command: String,
        moduleName: String = "ansible.builtin.shell",
        ignoresErrors: Boolean = false,
        parameters: Map<String, String> = emptyMap(),
    ) = AnsibleTask(
        moduleName,
        parameters,
        hasNoLog = false,
        isGroupingConstruct = false,
        moduleFreeForm = command,
        ignoresErrors = ignoresErrors,
    )

    private fun check(task: AnsibleTask) = RiskyShellPipeDetector.check(task, noLogInherited = false)

    @Test
    fun flagsPipelineWithoutPipefail() {
        assertNotNull(check(shell("cat /var/log/app.log | grep ERROR")))
        assertNotNull(check(shell("ps aux | grep java | wc -l", moduleName = "shell")))
    }

    @Test
    fun flagsCmdParameterFormToo() {
        val task = AnsibleTask(
            "ansible.builtin.shell",
            mapOf("cmd" to "cat a | grep b"),
            hasNoLog = false,
            isGroupingConstruct = false,
        )
        assertNotNull(check(task))
    }

    @Test
    fun doesNotFlagWhenPipefailIsSet() {
        assertNull(check(shell("set -o pipefail\ncat a | grep b")))
        assertNull(check(shell("set -euo pipefail\ncat a | grep b")))
        assertNull(check(shell("set -e -o pipefail\ncat a | grep b")))
        assertNull(check(shell("cd /srv && set -o pipefail && cat a | grep b")))
    }

    @Test
    fun doesNotTreatLogicalOrAsAPipe() {
        assertNull(check(shell("test -f /etc/a || touch /etc/a")))
    }

    @Test
    fun doesNotTreatAJinjaFilterAsAPipe() {
        assertNull(check(shell("echo {{ app_version | default('latest') }}")))
        assertNull(check(shell("echo {% if x | bool %}yes{% endif %}")))
    }

    @Test
    fun doesNotTreatAPipeInsideQuotesAsAPipe() {
        assertNull(check(shell("grep -E 'foo|bar' /etc/app.conf")))
        assertNull(check(shell("echo \"a|b\" > /tmp/x")))
    }

    @Test
    fun stillFlagsARealPipeNextToQuotedText() {
        assertNotNull(check(shell("cat \"/var/log/my app.log\" | grep ERROR")))
    }

    @Test
    fun doesNotFlagCommandModule() {
        assertNull(check(shell("cat a | grep b", moduleName = "ansible.builtin.command")))
    }

    @Test
    fun doesNotFlagWhenErrorsAreIgnored() {
        assertNull(check(shell("cat a | grep b", ignoresErrors = true)))
    }

    @Test
    fun doesNotFlagPowerShellExecutable() {
        assertNull(check(shell("Get-Process | Select-Object Name", parameters = mapOf("executable" to "/usr/bin/pwsh"))))
    }

    @Test
    fun doesNotFlagWhenThereIsNoCommandText() {
        assertNull(check(AnsibleTask("ansible.builtin.shell", emptyMap(), hasNoLog = false, isGroupingConstruct = false)))
    }
}
