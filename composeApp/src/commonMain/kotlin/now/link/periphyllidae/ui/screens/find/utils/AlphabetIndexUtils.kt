package now.link.periphyllidae.ui.screens.find.utils

/**
 * Custom comparator for sorting text: 0-9, then A-Z, then other characters.
 * Ensures numbers come first, followed by letters, then special characters.
 */
fun customAlphabeticalComparator(a: String, b: String): Int {
    if (a.isEmpty() && b.isEmpty()) return 0
    if (a.isEmpty()) return 1
    if (b.isEmpty()) return -1

    val aFirst = a.first().uppercaseChar()
    val bFirst = b.first().uppercaseChar()

    // Categorize characters
    val aIsLetter = aFirst.isLetter()
    val bIsLetter = bFirst.isLetter()
    val aIsDigit = aFirst.isDigit()
    val bIsDigit = bFirst.isDigit()

    return when {
        // Both are digits - compare normally
        aIsDigit && bIsDigit -> a.compareTo(b, ignoreCase = true)
        // a is digit, b is not digit - a comes first
        aIsDigit && !bIsDigit -> -1
        // b is digit, a is not digit - b comes first
        !aIsDigit && bIsDigit -> 1
        // Both are letters - compare normally
        aIsLetter && bIsLetter -> a.compareTo(b, ignoreCase = true)
        // a is letter, b is special character - a comes first
        aIsLetter && !bIsLetter && !bIsDigit -> -1
        // b is letter, a is special character - b comes first
        !aIsLetter && !aIsDigit && bIsLetter -> 1
        // Both are special characters - compare normally
        else -> a.compareTo(b, ignoreCase = true)
    }
}
