package ru.hse.spb

private fun readLines(): String {
    val input = StringBuilder()
    var newLine = readLine()
    while (!newLine.isNullOrBlank()) {
        input.append(newLine)
        newLine = readLine()
    }
    return input.toString()
}

private fun String.preprocess() = this
        .replace("\\s".toRegex(), "")
        .split(">", "<")
        .filter { it.isNotBlank() }

private fun countCells(input: List<String>): List<Int> {
    val result = mutableListOf<Int>()

    fun walkCells(tokenInd: Int): Int {
        fun String.isClosing() = this.startsWith("/")
        fun String.isTable() = this.endsWith("table")
        fun String.isCell() = this.endsWith("td")

        if (input.size <= tokenInd) return 0

        val firstToken = input[tokenInd]
        assert(firstToken.isTable() && !firstToken.isClosing())

        var currentInd = tokenInd + 1
        var cellCount = 0

        while (true) {
            val current = input[currentInd]

            if (!current.isClosing()) {
                when {
                    current.isCell() -> cellCount++
                    current.isTable() -> currentInd = walkCells(currentInd)
                }
            }
            if (current.isTable() && current.isClosing()) {
                result.add(cellCount)
                break
            }
            currentInd++
        }
        return currentInd
    }

    walkCells(0)
    return result
}

fun countCellsFromHtml(input: String): List<Int> {
    return countCells(input.preprocess()).sorted()
}

fun main(args: Array<String>) {
    val counts = countCellsFromHtml(readLines())

    print(counts.map { it.toString() }.joinToString(" "))
}

