package now.link.periphyllidae.utils

import kotlin.random.Random

expect object CryptoUtils {
    fun md5(input: String): String
    fun generateSalt(): String
}

fun generateRandomString(length: Int = 8): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
