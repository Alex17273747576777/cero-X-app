package com.ceroX.app.modules.extension_sandbox

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtensionValidatorSecurityTest {
    @Test fun missingFileIsRejected() {
        assertFalse(ExtensionValidator().validate("/no-existe/extension.apk").isValid)
    }
    @Test fun noImplicitTrustAndNoFabricatedPermissions() {
        val apk = File.createTempFile("untrusted", ".apk")
        try {
            apk.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
            val result = ExtensionValidator().validate(apk.path)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("certificados") })
            assertTrue(result.permissions.isEmpty())
        } finally { apk.delete() }
    }
    @Test fun corruptApkWithTrustedFingerprintStillFails() {
        val apk = File.createTempFile("corrupt", ".apk")
        try {
            apk.writeText("no es un APK firmado")
            assertFalse(ExtensionValidator(setOf("a".repeat(64))).validate(apk.path).isValid)
        } finally { apk.delete() }
    }
}
