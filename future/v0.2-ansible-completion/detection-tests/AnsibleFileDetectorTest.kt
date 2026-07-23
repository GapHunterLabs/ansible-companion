package dev.gaphunter.ansiblecompanion.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsibleFileDetectorTest {

    @Test
    fun playbookByPathIsDetected() {
        assertTrue(
            AnsibleFileDetector.looksLikeAnsible(
                "project/playbooks/site.yml",
                "- hosts: all\n  tasks:\n    - name: noop\n"
            )
        )
    }

    @Test
    fun roleTaskFileByPathIsDetected() {
        assertTrue(
            AnsibleFileDetector.looksLikeAnsible(
                "project/roles/webserver/tasks/main.yml",
                "- name: install nginx\n  ansible.builtin.apt:\n    name: nginx\n"
            )
        )
    }

    @Test
    fun kubernetesManifestIsNotClaimed() {
        assertFalse(
            AnsibleFileDetector.looksLikeAnsible(
                "k8s/deployment.yaml",
                "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web\n"
            )
        )
    }

    @Test
    fun helmValuesFileIsNotClaimed() {
        assertFalse(
            AnsibleFileDetector.looksLikeAnsible(
                "helm/values.yaml",
                "replicaCount: 3\nimage:\n  repository: nginx\n"
            )
        )
    }

    @Test
    fun bareYamlOutsideKnownDirsNeedsStrongContentSignal() {
        assertFalse(
            AnsibleFileDetector.looksLikeAnsible(
                "config/random.yaml",
                "foo: bar\nbaz: 1\n"
            )
        )
    }

    @Test
    fun nonYamlFileIsNeverClaimed() {
        assertFalse(AnsibleFileDetector.looksLikeAnsible("script.py", "hosts: all"))
    }

    @Test
    fun pathAloneDetectsRoleTaskFileWithoutContent() {
        assertTrue(AnsibleFileDetector.pathAloneSignalsAnsible("project/roles/webserver/tasks/main.yml"))
    }

    @Test
    fun pathAloneDoesNotClaimBareYamlOutsideKnownDirs() {
        assertFalse(AnsibleFileDetector.pathAloneSignalsAnsible("k8s/deployment.yaml"))
    }

    @Test
    fun pathAloneRejectsNonYamlEvenInsideRolesDir() {
        assertFalse(AnsibleFileDetector.pathAloneSignalsAnsible("project/roles/webserver/tasks/README.md"))
    }
}
