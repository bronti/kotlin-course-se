package ru.hse.spb

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.misc.ParseCancellationException
import ru.hse.spb.interpretor.Evaluator
import ru.hse.spb.parser.BrontiLexer
import ru.hse.spb.parser.BrontiParser
import java.io.IOException
import java.io.OutputStreamWriter

fun main(args: Array<String>) {

    try {
        val lexer = BrontiLexer(CharStreams.fromString(readLine()))
        val parser = BrontiParser(BufferedTokenStream(lexer))
        val tree = parser.file()
        val evaluator = Evaluator(OutputStreamWriter(System.out))
        val result = evaluator.visit(tree)
        when (result) {
            is Evaluator.Result.Return -> println("Program finished with code: ${result.value}")
            else -> throw IllegalStateException()
        }
    } catch (e: Evaluator.EvaluationException) {
        System.err.println(e.message)
    } catch (e: IOException) {
        System.err.println("IO exception: ${e.message}")
    } catch (e: ParseCancellationException) {
        System.err.println("Parsing exception: ${e.message}")
    }
}