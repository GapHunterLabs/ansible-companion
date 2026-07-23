package dev.gaphunter.ansiblecompanion.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePlatformTest {

    @Test
    fun detectsWindowsX64() {
        assertEquals(NodePlatform.WINDOWS_X64, NodePlatform.detect("Windows 11", "amd64"))
    }

    @Test
    fun detectsWindowsArm64() {
        assertEquals(NodePlatform.WINDOWS_ARM64, NodePlatform.detect("Windows 11", "aarch64"))
    }

    @Test
    fun detectsLinuxX64() {
        assertEquals(NodePlatform.LINUX_X64, NodePlatform.detect("Linux", "amd64"))
    }

    @Test
    fun detectsLinuxArm64() {
        assertEquals(NodePlatform.LINUX_ARM64, NodePlatform.detect("Linux", "aarch64"))
    }

    @Test
    fun detectsMacX64() {
        assertEquals(NodePlatform.MACOS_X64, NodePlatform.detect("Mac OS X", "x86_64"))
    }

    @Test
    fun detectsMacArm64() {
        assertEquals(NodePlatform.MACOS_ARM64, NodePlatform.detect("Mac OS X", "aarch64"))
    }

    @Test
    fun unknownArchReturnsNull() {
        assertNull(NodePlatform.detect("Linux", "riscv64"))
    }

    @Test
    fun thirtyTwoBitWindowsReturnsNull() {
        // There's no entry for win32 -> falling back to null (and a clear
        // message in the caller) is better than downloading the wrong binary.
        assertNull(NodePlatform.detect("Windows 10", "x86"))
    }

    @Test
    fun downloadUrlUsesPinnedVersionAndSuffix() {
        val url = NodePlatform.downloadUrl(NodePlatform.LINUX_X64)
        assertEquals(
            "https://nodejs.org/dist/v${NodePlatform.NODE_VERSION}/node-v${NodePlatform.NODE_VERSION}-linux-x64.tar.gz",
            url,
        )
    }

    @Test
    fun everyChecksumIsA64CharHexString() {
        for (platform in NodePlatform.values()) {
            assertEquals("checksum length for $platform", 64, platform.sha256.length)
            assertTrue("checksum charset for $platform", platform.sha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun allChecksumsAreDistinct() {
        val checksums = NodePlatform.values().map { it.sha256 }
        assertEquals(checksums.size, checksums.toSet().size)
    }

    @Test
    fun windowsPlatformsCacheWithExeSuffix() {
        assertEquals("node.exe", NodePlatform.WINDOWS_X64.cachedBinaryName)
        assertEquals("node.exe", NodePlatform.WINDOWS_ARM64.cachedBinaryName)
    }

    @Test
    fun unixPlatformsCacheWithoutExeSuffix() {
        for (platform in listOf(NodePlatform.LINUX_X64, NodePlatform.LINUX_ARM64, NodePlatform.MACOS_X64, NodePlatform.MACOS_ARM64)) {
            assertEquals("node", platform.cachedBinaryName)
        }
    }
}
