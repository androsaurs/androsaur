package dev.hram.androsaur.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {
    @Test fun numbersInWords() {
        assertEquals("ноль", NumberWords.words(0))
        assertEquals("пятьдесят шесть", NumberWords.words(56))
        assertEquals("сто десять", NumberWords.words(110))
        assertEquals("одна тысяча", NumberWords.words(1000))
        assertEquals("две тысячи пять", NumberWords.words(2005))
        assertEquals("двадцать одна тысяча триста", NumberWords.words(21_300))
        assertEquals("одиннадцать тысяч", NumberWords.words(11_000))
        assertEquals("один миллион пятьсот тысяч", NumberWords.words(1_500_000))
        assertEquals("минус семь", NumberWords.words(-7))
    }

    @Test fun theFourOperations() {
        assertEquals("Два плюс два — четыре.", Calculator.answer("2 + 2"))
        assertEquals("Три минус десять — минус семь.", Calculator.answer("3 - 10"))
        assertEquals("Семь умножить на восемь — пятьдесят шесть.", Calculator.answer("7 * 8"))
        assertEquals("Сто разделить на четыре — двадцать пять.", Calculator.answer("100 / 4"))
    }

    @Test fun multiplicationFirst() =
        assertEquals("Два плюс два умножить на два — шесть.", Calculator.answer("2 + 2 * 2"))

    @Test fun notWholeResults() {
        assertEquals("Семь разделить на два — три с половиной.", Calculator.answer("7 / 2"))
        assertEquals("Один разделить на два — половина.", Calculator.answer("1 / 2"))
        assertEquals("Десять разделить на три — примерно три целых тридцать три сотых.", Calculator.answer("10 / 3"))
        assertEquals("Одиннадцать разделить на десять — примерно одна целая одна десятая.", Calculator.answer("11 / 10"))
        assertEquals("примерно две целых две сотых", Calculator.say(2.0204))
        assertEquals("почти ноль", Calculator.say(0.001))
    }

    @Test fun zeroAndTooBig() {
        assertEquals("На ноль делить нельзя.", Calculator.answer("5 / 0"))
        assertEquals("Один миллион умножить на один миллион — очень большое число, я так далеко не считаю.", Calculator.answer("1000000 * 1000000"))
    }

    @Test fun notAnExample() {
        assertNull(Calculator.answer("2 +"))
        assertNull(Calculator.answer("два плюс два"))
        assertNull(Calculator.answer("2 ^ 2"))
    }
}
