package now.link.periphyllidae.utils

import kotlin.random.Random

actual object CryptoUtils {
    actual fun md5(input: String): String {
        // Android implementation would use java.security.MessageDigest
        // For now, return a placeholder - this would need proper implementation
        return "android_md5_placeholder"
    }
    
    actual fun generateSalt(): String {
        return generateRandomString(8)
    }
}
