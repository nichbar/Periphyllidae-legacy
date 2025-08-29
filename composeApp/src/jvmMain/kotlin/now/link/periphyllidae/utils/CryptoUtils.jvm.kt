package now.link.periphyllidae.utils

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random

actual object CryptoUtils {
    actual fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        val bigInt = BigInteger(1, digest)
        return bigInt.toString(16).padStart(32, '0')
    }
    
    actual fun generateSalt(): String {
        return generateRandomString(8)
    }
}
