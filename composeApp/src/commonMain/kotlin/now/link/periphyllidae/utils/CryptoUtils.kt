package now.link.periphyllidae.utils

import java.math.BigInteger
import java.security.MessageDigest

object CryptoUtils {

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        val bigInt = BigInteger(1, digest)
        return bigInt.toString(16).padStart(32, '0')
    }

    // Generate a deterministic salt based on server configuration
    fun generateFixedSalt(url: String, username: String): String {
        // Create a deterministic salt from server URL and username
        val input = "$url:$username:Periphyllidae"
        val hash = md5(input)
        // Use first 12 characters of hash as salt to match typical salt length
        return hash.substring(0, 12)
    }

    fun generateRandomString(length: Int = 8): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

}