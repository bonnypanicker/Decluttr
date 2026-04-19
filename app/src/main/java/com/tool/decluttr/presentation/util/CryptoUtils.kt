package com.tool.decluttr.presentation.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    // 256-bit static app key for simple cross-device obscurity
    private val keyBytes = byteArrayOf(
        0x5F, 0x1A, 0x3C, 0x7E, 0x2D, 0x4B, 0x6A, 0x19, 
        0x2B, 0x4C, 0x3E, 0x5D, 0x7F, 0x1B, 0x2A, 0x3C,
        0x6A, 0x4B, 0x5C, 0x2D, 0x1A, 0x3E, 0x7F, 0x1A,
        0x2B, 0x4C, 0x3D, 0x5E, 0x7A, 0x1B, 0x2C, 0x3A.toByte()
    )
    private val secretKey = SecretKeySpec(keyBytes, "AES")

    fun encrypt(plainText: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return iv + cipherText
    }

    fun decrypt(cipherTextWithIv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = cipherTextWithIv.copyOfRange(0, 12)
        val cipherText = cipherTextWithIv.copyOfRange(12, cipherTextWithIv.size)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        val plainTextBytes = cipher.doFinal(cipherText)
        return String(plainTextBytes, Charsets.UTF_8)
    }
}
