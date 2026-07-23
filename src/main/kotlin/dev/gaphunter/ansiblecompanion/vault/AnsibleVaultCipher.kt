package dev.gaphunter.ansiblecompanion.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verdict's "don't break" #1: integrated vault editing, including
 * ANSIBLE_VAULT_PASSWORD_FILE as a script (see Ansible Vault Editor,
 * out/evidence_14278.md, 86% reviews >=4 stars — the most valued of the
 * four incumbents).
 *
 * Reimplements the 1.1/AES256 format with javax.crypto (JDK, no new
 * dependencies) instead of shelling out to the `ansible-vault` CLI.
 * Decided this way because Ansible does NOT run on native Windows
 * (ansible-core fails to import `fcntl`/`os.get_blocking` outside POSIX)
 * and a good share of this plugin's real users will be on IntelliJ/
 * PyCharm on Windows precisely because of that.
 *
 * PBKDF2 is hand-reimplemented (RFC 2898) instead of using
 * javax.crypto.spec.PBEKeySpec: the JDK's implementation encodes the
 * password char[] with its own convention (not guaranteed UTF-8), which
 * breaks compatibility with non-ASCII passwords against the Python
 * `cryptography` library Ansible uses. Here the password goes in as
 * explicit UTF-8 bytes, byte-for-byte matching the reference.
 *
 * Verified against a real test vector taken from ansible/ansible's own
 * test suite (test/units/parsing/vault/test_vault.py) — see
 * AnsibleVaultCipherTest.
 */
object AnsibleVaultCipher {
    private const val HEADER_PREFIX = "\$ANSIBLE_VAULT;1.1;AES256"
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LEN = 32
    private const val IV_LEN = 16
    private const val HMAC_LEN = 32
    private const val LINE_WRAP = 80

    class VaultFormatException(message: String) : Exception(message)

    fun looksEncrypted(text: String): Boolean = text.trimStart().startsWith("\$ANSIBLE_VAULT;")

    fun decrypt(vaultText: String, password: String): ByteArray {
        val lines = vaultText.trim().lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith(HEADER_PREFIX)) {
            throw VaultFormatException("not a supported 1.1/AES256 vault")
        }
        val outerHex = lines.drop(1).joinToString("") { it.trim() }
        val inner = String(hexDecode(outerHex), Charsets.US_ASCII)
        val parts = inner.split("\n")
        if (parts.size != 3) throw VaultFormatException("invalid vaulttext format")

        val salt = hexDecode(parts[0])
        val hmacExpected = hexDecode(parts[1])
        val ciphertext = hexDecode(parts[2])

        val derived = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS, KEY_LEN * 2 + IV_LEN)
        val key1 = derived.copyOfRange(0, KEY_LEN)
        val key2 = derived.copyOfRange(KEY_LEN, KEY_LEN * 2)
        val iv = derived.copyOfRange(KEY_LEN * 2, KEY_LEN * 2 + IV_LEN)

        val hmacActual = hmacSha256(key2, ciphertext)
        if (!hmacActual.contentEquals(hmacExpected)) {
            throw VaultFormatException("wrong password or corrupted vault (HMAC mismatch)")
        }

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key1, "AES"), IvParameterSpec(iv))
        return unpadPkcs7(cipher.doFinal(ciphertext))
    }

    fun encrypt(plaintext: ByteArray, password: String, salt: ByteArray = randomSalt()): String {
        val derived = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS, KEY_LEN * 2 + IV_LEN)
        val key1 = derived.copyOfRange(0, KEY_LEN)
        val key2 = derived.copyOfRange(KEY_LEN, KEY_LEN * 2)
        val iv = derived.copyOfRange(KEY_LEN * 2, KEY_LEN * 2 + IV_LEN)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key1, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(padPkcs7(plaintext))
        val hmac = hmacSha256(key2, ciphertext)

        val inner = hexEncode(salt) + "\n" + hexEncode(hmac) + "\n" + hexEncode(ciphertext)
        val outerHex = hexEncode(inner.toByteArray(Charsets.US_ASCII))
        return HEADER_PREFIX + "\n" + outerHex.chunked(LINE_WRAP).joinToString("\n")
    }

    // -- primitives ----------------------------------------------------------

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLenBytes: Int): ByteArray {
        val numBlocks = (keyLenBytes + HMAC_LEN - 1) / HMAC_LEN
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val result = ByteArray(numBlocks * HMAC_LEN)

        for (blockIndex in 1..numBlocks) {
            mac.reset()
            mac.update(salt)
            mac.update(intToBytesBE(blockIndex))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, result, (blockIndex - 1) * HMAC_LEN, HMAC_LEN)
        }
        return result.copyOf(keyLenBytes)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun intToBytesBE(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun padPkcs7(data: ByteArray, blockSize: Int = 16): ByteArray {
        val padLen = blockSize - (data.size % blockSize)
        return data + ByteArray(padLen) { padLen.toByte() }
    }

    private fun unpadPkcs7(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val padLen = data.last().toInt() and 0xFF
        if (padLen <= 0 || padLen > data.size) throw VaultFormatException("invalid padding")
        return data.copyOfRange(0, data.size - padLen)
    }

    private fun hexDecode(s: String): ByteArray {
        val clean = s.trim()
        require(clean.length % 2 == 0) { "hex string has odd length" }
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    private fun hexEncode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun randomSalt(): ByteArray {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
