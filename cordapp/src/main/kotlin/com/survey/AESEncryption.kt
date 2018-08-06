package com.survey
import sun.misc.BASE64Decoder
import java.io.InputStream
import java.security.Key
import javax.crypto.Cipher
import java.util.jar.JarInputStream
import javax.crypto.spec.SecretKeySpec

object AESenc {

    private val ALGO = "AES"
    private val keyValue =
            byteArrayOf('C'.toByte(),'O'.toByte(),'R'.toByte(),'D'.toByte(),'A'.toByte(),'I'.toByte(),'S'.toByte(),'T'.toByte(),'H'.toByte(),'E'.toByte(),'B'.toByte(),'E'.toByte(),'S'.toByte(),'T'.toByte(),'0'.toByte(),'1'.toByte())

    /**
     * Encrypt a string with AES algorithm.
     *
     * @param data is a string
     * @return the encrypted string
     */
    @Throws(Exception::class)
    fun encrypt(data: InputStream): Pair<ByteArray, ByteArray> {
        val key = generateKey()
        val c = Cipher.getInstance(ALGO)
        c.init(Cipher.ENCRYPT_MODE, key)
        val encVal = c.doFinal(data.readBytes())
        // return Pair(BASE64Encoder().encode(encVal), key)
        return Pair(encVal, keyValue)
    }

    /**
     * Decrypt a string with AES algorithm.
     *
     * @param encryptedData is a string
     * @return the decrypted string
     */
    @Throws(Exception::class)
    fun decrypt(encryptedData: String): String {
        val key = generateKey()
        val c = Cipher.getInstance(ALGO)
        c.init(Cipher.DECRYPT_MODE, key)
        val decordedValue = BASE64Decoder().decodeBuffer(encryptedData)
        val decValue = c.doFinal(decordedValue)
        return String(decValue)
    }

    /**
     * Generate a new encryption key.
     */
    @Throws(Exception::class)
    private fun generateKey(): Key {
        return SecretKeySpec(keyValue, ALGO)
    }
}