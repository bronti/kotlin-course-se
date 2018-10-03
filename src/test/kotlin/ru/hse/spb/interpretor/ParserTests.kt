package ru.hse.spb.interpretor

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.tree.TerminalNode
import org.junit.Test
import ru.hse.spb.parser.BrontiLexer
import ru.hse.spb.parser.BrontiParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun testLiteral() {
        val literal = "239"
        val expr = getExpression(literal)
        expr.checkLiteral(literal)
    }

    @Test
    fun testBinOp() {
        val left = "2"
        val right = "7"

        getExpression("$left * $right").checkMult(left, right, BrontiParser.MultiplicationExpressionContext::ASTERISK)
        getExpression("$left / $right").checkMult(left, right, BrontiParser.MultiplicationExpressionContext::DIVISION)
        getExpression("$left % $right").checkMult(left, right, BrontiParser.MultiplicationExpressionContext::MODULUS)
    }

    @Test
    fun testCompare() {
        val left = "2"
        val right = "7"

        getExpression("$left == $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::EQ)
        getExpression("$left != $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::NEQ)
        getExpression("$left > $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::GR)
        getExpression("$left >= $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::GEQ)
        getExpression("$left < $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::LS)
        getExpression("$left <= $right").checkComparison(left, right, BrontiParser.CompareExpressionContext::LEQ)
    }

    @Test
    fun testLogic() {
        val left = "7 == 16"
        val right = "2 != 39"
        val checkLeft: BrontiParser.ExpressionContext.() -> Unit = { checkComparison("7", "16", BrontiParser.CompareExpressionContext::EQ) }
        val checkRight: BrontiParser.ExpressionContext.() -> Unit = { checkComparison("2", "39", BrontiParser.CompareExpressionContext::NEQ) }

        getExpression("$left || $right").checkLogic(checkLeft, checkRight, BrontiParser.LogicalExpressionContext::OR)
        getExpression("$left && $right").checkLogic(checkLeft, checkRight, BrontiParser.LogicalExpressionContext::AND)
    }

    @Test
    fun testFunctionCall() {
        val name1 = "foo"
        val name2 = "println"
        val args0 = listOf<String>()
        val args1 = listOf("6")
        val args3 = listOf("6", "0", "2")

        fun assemble(name: String, args: List<String>) = "$name(${args.joinToString(", ")})"

        getExpression(assemble(name1, args0)).checkFunctionCall(name1, args0)
        getExpression(assemble(name2, args1)).checkFunctionCall(name2, args1)
        getExpression(assemble(name2, args3)).checkFunctionCall(name2, args3)
    }

    @Test
    fun testVariable() {
        val name = "x"
        val value = "5"

        val decl = getStatement("var $name = $value").variableDeclaration()
        assertNotNull(decl)

        assertEquals(name, decl!!.ID().text)
        decl.expression().checkLiteral(value)
    }

    @Test
    fun testFunction() {
        fun doTestFunction(name: String, body: String, params: List<String>) {
            val decl = getStatement("fun $name(${params.joinToString(", ")}) { $body }").functionDeclaration()
            assertNotNull(decl)

            assertEquals(name, decl!!.ID().text)
            decl.parameterNames().ID().zip(params).forEach { (id, param) -> assertEquals(param, id.text) }

            val innerBlock = decl.blockWithBraces().block()
            innerBlock.checkStatementCount(1)
            innerBlock.statement(0).expression().checkLiteral(body)
        }

        doTestFunction("foo", "4", listOf())
        doTestFunction("boo", "4", listOf("hey"))
        doTestFunction("foo", "4", listOf("pom", "bom"))
    }

    @Test
    fun testReturn() {
        val value = "5"
        val ret = getStatement("return $value").returnStatement()
        assertNotNull(ret)
        ret!!.expression().checkLiteral(value)
    }

    @Test
    fun testIf() {
        val value = "5"
        val ife = getStatement("if ($value) { $value } else { $value }").ifStatement()
        val iff = getStatement("if ($value) { $value }").ifStatement()
        assertNotNull(ife)
        assertNotNull(iff)
        assertNotNull(ife.expression())
        assertNotNull(iff.expression())
        ife.expression().checkLiteral(value)
        iff.expression().checkLiteral(value)
        assertEquals(2, ife.blockWithBraces().size)
        assertEquals(1, iff.blockWithBraces().size)
    }

    @Test
    fun testWhile() {
        val value = "5"
        val wh = getStatement("while ($value) { $value }").whileStatement()
        assertNotNull(wh)
        assertNotNull(wh.expression())
        wh.expression().checkLiteral(value)
    }

    private fun BrontiParser.ExpressionContext.checkLiteral(expectedValue: String) {
        assertTrue(this is BrontiParser.LiteralExpressionContext)

        val literalExpr = this as BrontiParser.LiteralExpressionContext
        assertEquals(expectedValue, literalExpr.LITERAL().text)
    }

    private  fun getBlock(program: String): BrontiParser.BlockContext {
        val lexer = BrontiLexer(CharStreams.fromString(program))
        val parser = BrontiParser(BufferedTokenStream(lexer))
        return parser.file().block()
    }

    private  fun getStatement(program: String): BrontiParser.StatementContext {
        val block = getBlock(program)
        block.checkStatementCount(1)
        return block.statement(0)
    }

    private  fun getExpression(program: String): BrontiParser.ExpressionContext {
        val expr = getStatement(program).expression()
        assertNotNull(expr)
        return expr!!
    }

    private fun BrontiParser.BlockContext.checkStatementCount(expectedSize: Int) = assertEquals(expectedSize, statement().size)

    private fun BrontiParser.ExpressionContext.checkMult(
            left: String,
            right: String,
            op: BrontiParser.MultiplicationExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is BrontiParser.MultiplicationExpressionContext)

        val mult = this as BrontiParser.MultiplicationExpressionContext
        assertEquals(2, mult.expression().size)
        mult.expression(0).checkLiteral(left)
        mult.expression(1).checkLiteral(right)
        assertNotNull(mult.op())
    }

    private fun BrontiParser.ExpressionContext.checkComparison(
            left: String,
            right: String,
            op: BrontiParser.CompareExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is BrontiParser.CompareExpressionContext)

        val comparison = this as BrontiParser.CompareExpressionContext
        assertEquals(2, comparison.expression().size)
        comparison.expression(0).checkLiteral(left)
        comparison.expression(1).checkLiteral(right)
        assertNotNull(comparison.op())
    }

    private fun BrontiParser.ExpressionContext.checkLogic(
            checkLeft: BrontiParser.ExpressionContext.() -> Unit,
            checkRight: BrontiParser.ExpressionContext.() -> Unit,
            op: BrontiParser.LogicalExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is BrontiParser.LogicalExpressionContext)

        val logic = this as BrontiParser.LogicalExpressionContext
        assertEquals(2, logic.expression().size)
        logic.expression(0).checkLeft()
        logic.expression(1).checkRight()
        assertNotNull(logic.op())
    }

    private fun BrontiParser.ExpressionContext.checkFunctionCall(
            name: String,
            args: List<String>
    ) {
        assertTrue(this is BrontiParser.FunctionCallExpressionContext)

        val funcExpr = (this as BrontiParser.FunctionCallExpressionContext).functionCall()
        assertEquals(name, funcExpr.ID().text)
        assertEquals(args.size, funcExpr.arguments().expression().size)
        for (i in 0 until args.size) {
            funcExpr.arguments().expression(i).checkLiteral(args[i])
        }
    }
}