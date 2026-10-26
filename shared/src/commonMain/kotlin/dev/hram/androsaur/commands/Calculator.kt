package dev.hram.androsaur.commands

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The answer to «сколько будет…»: the audio module hears the example and sends it as `7 * 8` (numbers and `+ - * /`); here it is
 * counted, multiplication and division first, and said in words: «Семь умножить на восемь — пятьдесят шесть.» A result that is not
 * whole is «три с половиной» or «примерно три целых тридцать три сотых».
 */
object Calculator {
    private const val TOO_BIG = 1e12

    /** What the assistant says to [expression], or null when it is not an example. */
    fun answer(expression: String): String? {
        val tokens = expression.trim().split(Regex("\\s+"))
        if (tokens.size < 3 || tokens.size % 2 == 0) return null
        val numbers = tokens.filterIndexed { i, _ -> i % 2 == 0 }.map { it.toLongOrNull() ?: return null }
        val ops = tokens.filterIndexed { i, _ -> i % 2 == 1 }
        if (ops.any { it !in setOf("+", "-", "*", "/") }) return null
        val example = buildString {
            append(NumberWords.words(numbers[0]))
            ops.forEachIndexed { i, op -> append(" ").append(OP_WORDS.getValue(op)).append(" ").append(NumberWords.words(numbers[i + 1])) }
        }.replaceFirstChar { it.uppercase() }
        if (ops.indices.any { ops[it] == "/" && numbers[it + 1] == 0L }) return "На ноль делить нельзя."
        val result = count(numbers.map { it.toDouble() }, ops)
        if (abs(result) >= TOO_BIG) return "$example — очень большое число, я так далеко не считаю."
        return "$example — ${say(result)}."
    }

    /** Multiplication and division first, then addition and subtraction, left to right. */
    private fun count(numbers: List<Double>, ops: List<String>): Double {
        val terms = mutableListOf(numbers[0])
        val signs = mutableListOf<String>()
        ops.forEachIndexed { i, op ->
            val n = numbers[i + 1]
            when (op) {
                "*" -> terms[terms.lastIndex] = terms.last() * n
                "/" -> terms[terms.lastIndex] = terms.last() / n
                else -> { signs += op; terms += n }
            }
        }
        var total = terms[0]
        signs.forEachIndexed { i, s -> total = if (s == "+") total + terms[i + 1] else total - terms[i + 1] }
        return total
    }

    /** «пятьдесят шесть», «минус три», «три с половиной», «примерно три целых тридцать три сотых». */
    fun say(value: Double): String {
        val whole = value.roundToLong()
        if (abs(value - whole) < 1e-9) return NumberWords.words(whole)
        val sign = if (value < 0) "минус " else ""
        val a = abs(value)
        val intPart = floor(a).toLong()
        if (abs(a - intPart - 0.5) < 1e-9) return sign + if (intPart == 0L) "половина" else "${NumberWords.words(intPart)} с половиной"
        val hundredths = (a * 100).roundToLong()
        if (hundredths == 0L) return "почти ноль"
        if (hundredths % 100 == 0L) return "примерно $sign${NumberWords.words(hundredths / 100)}"
        val i = hundredths / 100
        val f = hundredths % 100
        val wholeWords = Answers.count(i.toInt(), "целая", "целых", "целых", feminine = true)
        val frac = if (f % 10 == 0L) Answers.count((f / 10).toInt(), "десятая", "десятых", "десятых", feminine = true)
            else Answers.count(f.toInt(), "сотая", "сотых", "сотых", feminine = true)
        return "примерно $sign$wholeWords $frac"
    }

    private val OP_WORDS = mapOf("+" to "плюс", "-" to "минус", "*" to "умножить на", "/" to "разделить на")
}

/** Whole numbers in words, up to hundreds of billions: «сто двадцать три», «две тысячи пять», «минус семь». */
object NumberWords {
    private val ones = listOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать",
        "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
    private val tens = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
    private val hundreds = listOf("", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")

    fun words(n: Long): String {
        if (n == 0L) return "ноль"
        if (n < 0) return "минус " + words(-n)
        val parts = mutableListOf<String>()
        var rest = n
        val scales = listOf(
            Triple(1_000_000_000L, listOf("миллиард", "миллиарда", "миллиардов"), false),
            Triple(1_000_000L, listOf("миллион", "миллиона", "миллионов"), false),
            Triple(1_000L, listOf("тысяча", "тысячи", "тысяч"), true),
        )
        for ((scale, forms, feminine) in scales) {
            val k = rest / scale
            if (k > 0) {
                parts += group(k.toInt(), feminine)
                parts += form(k.toInt(), forms)
                rest %= scale
            }
        }
        if (rest > 0) parts += group(rest.toInt(), false)
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 1..999 in words; [feminine] for thousands: «одна», «две». */
    private fun group(n: Int, feminine: Boolean): String {
        val words = mutableListOf(hundreds[n / 100])
        val r = n % 100
        if (r < 20) words += ones[r] else { words += tens[r / 10]; words += ones[r % 10] }
        var text = words.filter { it.isNotEmpty() }.joinToString(" ")
        if (feminine && r !in 11..19) {
            if (r % 10 == 1) text = text.removeSuffix("один") + "одна"
            if (r % 10 == 2) text = text.removeSuffix("два") + "две"
        }
        return text
    }

    private fun form(n: Int, forms: List<String>): String = when {
        n % 100 in 11..14 -> forms[2]
        n % 10 == 1 -> forms[0]
        n % 10 in 2..4 -> forms[1]
        else -> forms[2]
    }
}
