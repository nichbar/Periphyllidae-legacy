package now.link.periphyllidae.ui.screens.find.utils

/**
 * Utility functions for A-Z indexing and quick locate functionality
 */

/**
 * Utility function to extract the primary sorting character from a string
 * Numbers are grouped under '0', letters A-Z, others under '#'
 */
fun getSortingChar(text: String): Char {
    if (text.isEmpty()) return '#'

    val firstChar = text.first().uppercaseChar()
    return when {
        firstChar.isDigit() -> '0'  // All numbers grouped under '0'
        firstChar.isLetter() -> firstChar
        else -> '#'
    }
}

/**
 * Creates a mapping of characters to their first occurrence index in a list
 * Only includes characters that actually have items
 */
fun <T> createCharIndexMap(
    items: List<T>,
    extractText: (T) -> String,
): Map<Char, Int> {
    val charIndexMap = mutableMapOf<Char, Int>()

    items.forEachIndexed { index, item ->
        val text = extractText(item)
        val sortingChar = getSortingChar(text)

        // Only add the first occurrence of each character
        if (!charIndexMap.containsKey(sortingChar)) {
            charIndexMap[sortingChar] = index
        }
    }

    return charIndexMap
}

/**
 * Gets the sorted list of available characters based on the data
 * Order: 0-9, A-Z, #
 */
fun getAvailableChars(charIndexMap: Map<Char, Int>): List<Char> {
    return charIndexMap.keys.sortedWith { a, b ->
        when {
            a == '0' && b != '0' -> -1
            a != '0' && b == '0' -> 1
            a == '#' && b != '#' -> 1
            a != '#' && b == '#' -> -1
            else -> a.compareTo(b)
        }
    }
}

/**
 * Custom comparator for sorting text: 0-9, then A-Z, then other characters
 * This ensures proper sorting where numbers come first, followed by letters, then special characters
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