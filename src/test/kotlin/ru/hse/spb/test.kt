package ru.hse.spb

import org.junit.Assert.assertEquals
import org.junit.Test

class TestSource {
    @Test
    fun test1() {
        val result = countCellsFromHtml("<table><tr><td></td></tr></table>").sorted()
        assertEquals(listOf(1), result)
    }

    @Test
    fun test2() {
        val result = countCellsFromHtml("<table>\n" +
                "<tr>\n" +
                "<td>\n" +
                "<table><tr><td></td></tr><tr><td></\n" +
                "td\n" +
                "></tr><tr\n" +
                "><td></td></tr><tr><td></td></tr></table>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</table>").sorted()
        assertEquals(listOf(1, 4), result)
    }

    @Test
    fun test3() {
        val result = countCellsFromHtml("<table><tr><td>\n" +
                "<table><tr><td>\n" +
                "<table><tr><td>\n" +
                "<table><tr><td></td><td></td>\n" +
                "</tr><tr><td></td></tr></table>\n" +
                "</td></tr></table>\n" +
                "</td></tr></table>\n" +
                "</td></tr></table>").sorted()
        assertEquals(listOf(1, 1, 1, 3), result)
    }
}