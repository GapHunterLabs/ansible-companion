package dev.gaphunter.ansiblecompanion.vault

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Real platform test (spins up a lightweight in-memory project/editor,
 * headless) instead of runIde with mouse clicks — verifies that the
 * action actually replaces the document's text, not just that the
 * cipher itself is correct (AnsibleVaultCipherTest already proves that
 * against the official vector).
 */
class VaultEditorOpsTest : BasePlatformTestCase() {

    fun testEncryptThenDecryptSelectionRoundTripsInRealEditor() {
        myFixture.configureByText("secrets.yml", "db_password: <selection>super-secret-123</selection>")
        val editor = myFixture.editor

        VaultEditorOps.encryptSelection(project, editor, "test-password")

        val afterEncrypt = editor.document.text
        assertTrue("the document should contain the vault header", afterEncrypt.contains("\$ANSIBLE_VAULT;1.1;AES256"))
        assertFalse("the plaintext secret should no longer be in the document", afterEncrypt.contains("super-secret-123"))

        val vaultStart = afterEncrypt.indexOf("\$ANSIBLE_VAULT")
        editor.selectionModel.setSelection(vaultStart, afterEncrypt.length)

        VaultEditorOps.decryptSelection(project, editor, "test-password")

        val afterDecrypt = editor.document.text
        assertEquals("db_password: super-secret-123", afterDecrypt)
    }

    fun testWrongPasswordThrowsInsteadOfCorruptingDocument() {
        myFixture.configureByText("secrets.yml", "<selection>another-secret</selection>")
        val editor = myFixture.editor

        VaultEditorOps.encryptSelection(project, editor, "correct-password")
        editor.selectionModel.setSelection(0, editor.document.textLength)

        try {
            VaultEditorOps.decryptSelection(project, editor, "wrong-password")
            fail("should have thrown VaultFormatException")
        } catch (e: AnsibleVaultCipher.VaultFormatException) {
            assertTrue(editor.document.text.contains("\$ANSIBLE_VAULT"))
        }
    }
}
