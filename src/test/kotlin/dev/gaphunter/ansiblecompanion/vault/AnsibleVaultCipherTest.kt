package dev.gaphunter.ansiblecompanion.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsibleVaultCipherTest {

    // Real test vector, taken as-is from
    // ansible/ansible: test/units/parsing/vault/test_vault.py
    // (test_encrypt_decrypt_aes256_existing_vault). Password and
    // ciphertext come from the official suite itself, not made up.
    private val realPassword = "test-vault-password"
    private val realVaultText = """
        ${'$'}ANSIBLE_VAULT;1.1;AES256
        33363965326261303234626463623963633531343539616138316433353830356566396130353436
        3562643163366231316662386565383735653432386435610a306664636137376132643732393835
        63383038383730306639353234326630666539346233376330303938323639306661313032396437
        6233623062366136310a633866373936313238333730653739323461656662303864663666653563
        3138
    """.trimIndent()

    @Test
    fun decryptsRealAnsibleTestVector() {
        val plaintext = AnsibleVaultCipher.decrypt(realVaultText, realPassword)
        assertEquals("Setec Astronomy", String(plaintext, Charsets.UTF_8))
    }

    @Test
    fun roundTripsOwnEncryption() {
        val secret = "the customer's password never goes into the repo".toByteArray(Charsets.UTF_8)
        val vaultText = AnsibleVaultCipher.encrypt(secret, "another-key-123")
        val back = AnsibleVaultCipher.decrypt(vaultText, "another-key-123")
        assertArrayEquals(secret, back)
    }

    @Test
    fun wrongPasswordFailsHmacInsteadOfSilentlyReturningGarbage() {
        val vaultText = AnsibleVaultCipher.encrypt("sensitive data".toByteArray(), "correct-key")
        try {
            AnsibleVaultCipher.decrypt(vaultText, "wrong-key")
            throw AssertionError("should have thrown VaultFormatException")
        } catch (e: AnsibleVaultCipher.VaultFormatException) {
            assertTrue(e.message!!.contains("HMAC"))
        }
    }

    @Test
    fun looksEncryptedDetectsHeader() {
        assertTrue(AnsibleVaultCipher.looksEncrypted(realVaultText))
        assertTrue(!AnsibleVaultCipher.looksEncrypted("hosts: all\ntasks: []\n"))
    }
}
