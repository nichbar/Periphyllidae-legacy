package now.link.periphyllidae.utils

import kotlin.random.Random

expect object CryptoUtils {
    fun md5(input: String): String
    fun generateSalt(): String
}

// Generate a deterministic salt based on server configuration
fun generateFixedSalt(url: String, username: String): String {
    // Create a deterministic salt from server URL and username
    val input = "$url:$username:Periphyllidae"
    val hash = CryptoUtils.md5(input)
    // Use first 12 characters of hash as salt to match typical salt length
    return hash.substring(0, 12)
}

fun generateRandomString(length: Int = 8): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
