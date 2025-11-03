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
 * Order: 0, A-Z, #
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