package ru.hse.spb.interpretor

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.RuleNode
import ru.hse.spb.parser.BrontiParser
import ru.hse.spb.parser.BrontiParserBaseVisitor
import java.io.Writer

class Evaluator(out: Writer) : BrontiParserBaseVisitor<Evaluator.Result>() {

    private val state: State = State(out)

    sealed class Result {
        object None : Result()
        data class ExpressionInt(val value: Int) : Result()
        data class ExpressionBool(val value: Boolean) : Result()
        data class Return(val value: Int) : Result()
    }

    class EvaluationException(val ctx: ParserRuleContext, msg: String) : Exception("Evaluation error in line ${ctx.start.line}: $msg")

    override fun defaultResult(): Result = Result.None
    private val noResult: Result
        get() = defaultResult()

    override fun shouldVisitNextChild(node: RuleNode, currentResult: Result) = currentResult !is Result.Return

    override fun visitFile(ctx: BrontiParser.FileContext): Result {
        val result = ctx.block().accept(this)
        return result as? Result.Return
                ?: Result.Return(0)
    }

    override fun visitBlock(ctx: BrontiParser.BlockContext): Result {
        return ctx.statement()!!.fold(noResult) { result, child ->
            if (result is Result.Return) return result
            child.accept(this)
        }
    }

    override fun visitBlockWithBraces(ctx: BrontiParser.BlockWithBracesContext): Result {
        state.openInnerScope()
        val result = ctx.block()?.accept(this) ?: noResult
        state.closeInnerScope()
        return result
    }

    override fun visitFunctionDeclaration(ctx: BrontiParser.FunctionDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val parameters = ctx.parameterNames().ID().map { it.symbol.text }
        if (parameters.distinct().size != parameters.size) throw EvaluationException(ctx, "Same parameter names in function are not allowed.")
        val body = ctx.blockWithBraces()
        val wasUndefined = state.defineFunction(name, parameters, body)
        if (!wasUndefined) throw EvaluationException(ctx, "Redefinition of the function $name.")
        return noResult
    }

    override fun visitVariableDeclaration(ctx: BrontiParser.VariableDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val wasUndefined = state.defineVariable(name)
        if (!wasUndefined) throw EvaluationException(ctx, "Redefinition of a variable: $name.")
        val expr = ctx.expression() ?: return noResult
        val value = expr.acceptWithResultOfType<Result.ExpressionInt>().value
        state.setVariable(name, value)
        return noResult
    }

    override fun visitWhileStatement(ctx: BrontiParser.WhileStatementContext): Result {
        while (ctx.expression().evaluateAsCondition()) {
            val iterationResult = ctx.blockWithBraces().accept(this)
            if (iterationResult is Result.Return) return iterationResult
        }
        return noResult
    }

    override fun visitIfStatement(ctx: BrontiParser.IfStatementContext): Result {
        val ifBlock = ctx.blockWithBraces(0)
        val elseBlock = if (ctx.ELSE() != null) ctx.blockWithBraces(1) else null
        val block = if (ctx.expression().evaluateAsCondition()) ifBlock else elseBlock
        return block?.accept(this) ?: noResult
    }

    override fun visitAssignment(ctx: BrontiParser.AssignmentContext): Result {
        val name = ctx.ID().symbol.text
        if (!state.isDefinedVariable(name)) throw EvaluationException(ctx, "A variable $name is undefined.")
        val value = ctx.expression().acceptWithResultOfType<Result.ExpressionInt>().value
        state.setVariable(name, value)
        return noResult
    }

    override fun visitReturnStatement(ctx: BrontiParser.ReturnStatementContext)
            = Result.Return(ctx.expression().acceptWithResultOfType<Result.ExpressionInt>().value)

    override fun visitFunctionCall(ctx: BrontiParser.FunctionCallContext): Result {
        val name = ctx.ID().symbol.text
        val arguments = ctx.arguments().expression().map { it.acceptWithResultOfType<Result.ExpressionInt>().value }

        val function = state.getFunction(name)

        if (function == null) {
            val wasPredefined = state.callPredefinedFunction(name, arguments)
            if (!wasPredefined) throw EvaluationException(ctx, "There is no such function")
            return Result.None
        }

        val (argNames, body) = function

        if (argNames.size != arguments.size) throw EvaluationException(ctx, "Invalid number of arguments.")

        state.openInnerScope()
        for ((argName, value) in argNames.zip(arguments)) {
            state.defineVariable(argName)
            state.setVariable(argName, value)
        }
        val result = body.accept(this) as? Result.Return ?: Result.Return(0)
        state.closeInnerScope()
        return Result.ExpressionInt(result.value)
    }

    override fun visitCompareExpression(ctx: BrontiParser.CompareExpressionContext) = visitBinaryOp<Result.ExpressionInt>(ctx.expression()) { l, r ->
        val result = when {
            ctx.NEQ() != null -> l.value != r.value
            ctx.EQ() != null -> l.value == r.value
            ctx.GEQ() != null -> l.value >= r.value
            ctx.GR() != null -> l.value > r.value
            ctx.LEQ() != null -> l.value <= r.value
            ctx.LS() != null -> l.value < r.value
            else -> throw IllegalStateException()
        }
        Result.ExpressionBool(result)
    }

    override fun visitFunctionCallExpression(ctx: BrontiParser.FunctionCallExpressionContext): Result
            = ctx.functionCall().accept(this)

    override fun visitParenthesisExpression(ctx: BrontiParser.ParenthesisExpressionContext): Result
            = ctx.expression().accept(this)

    override fun visitMultiplicationExpression(ctx: BrontiParser.MultiplicationExpressionContext) = visitBinaryOp<Result.ExpressionInt>(ctx.expression()) { l, r ->
        val result = when {
            ctx.ASTERISK() != null -> l.value * r.value
            ctx.DIVISION() != null -> l.value / r.value
            ctx.MODULUS() != null -> l.value % r.value
            else -> throw IllegalStateException()
        }
        Result.ExpressionInt(result)
    }

    override fun visitLiteralExpression(ctx: BrontiParser.LiteralExpressionContext): Result {
        val value = ctx.LITERAL().symbol.text.toIntOrNull()
                ?: throw EvaluationException(ctx, "Invalid literal ${ctx.start.text}")
        return Result.ExpressionInt(value)
    }

    override fun visitLogicalExpression(ctx: BrontiParser.LogicalExpressionContext) = visitBinaryOp<Result.ExpressionBool>(ctx.expression()) { l, r ->
        val result = when {
            ctx.AND() != null -> l.value && r.value
            ctx.OR() != null -> l.value || r.value
            else -> throw IllegalStateException()
        }
        Result.ExpressionBool(result)
    }

    override fun visitVariableExpression(ctx: BrontiParser.VariableExpressionContext): Result {
        val name = ctx.ID().symbol.text
        val value = state.getVariable(name) ?: throw EvaluationException(ctx, "Undefined or unset variable $name.")
        return Result.ExpressionInt(value)
    }

    override fun visitSummExpression(ctx: BrontiParser.SummExpressionContext) = visitBinaryOp<Result.ExpressionInt>(ctx.expression()) { l, r ->
        val result = when {
            ctx.PLUS() != null -> l.value + r.value
            ctx.MINUS() != null -> l.value - r.value
            else -> throw IllegalStateException()
        }
        Result.ExpressionInt(result)
    }


    private fun <T : Result> visitBinaryOp(exprs: List<BrontiParser.ExpressionContext>, op: (T, T) -> Result): Result {
        val (left, right) = exprs.map { it.acceptWithResultOfType<T>() }
        return op(left, right)
    }

    private fun <T : Result> BrontiParser.ExpressionContext.acceptWithResultOfType(): T {
        return this.accept(this@Evaluator) as? T
                ?: throw EvaluationException(this, "Incorrect result type of expression.")
    }

    private fun BrontiParser.ExpressionContext.evaluateAsCondition() = this.acceptWithResultOfType<Result.ExpressionBool>().value
}