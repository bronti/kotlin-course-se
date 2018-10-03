package ru.hse.spb.interpretor

import ru.hse.spb.parser.BrontiParser
import java.io.Writer

class State(private val out: Writer) {
    private var scope: Scope = Scope.empty

    fun openInnerScope() {
        scope = Scope.Inner(scope)
    }

    fun closeInnerScope() {
        scope = (scope as Scope.Inner).outer
    }

    fun defineVariable(name: String) = scope.defineVariable(name)

    fun isDefinedVariable(name: String) = scope.isDefinedVariable(name)

    fun setVariable(name: String, value: Int) = scope.setVariable(name, value)

    fun getVariable(name: String) = scope.getVariable(name)

    private val predefinedFunctions =
            hashMapOf<String, (List<Int>) -> Unit>(
                    "println" to { it -> out.write(it.joinToString(" ", postfix = "\n")) }
            )

    fun defineFunction(name: String, paramNames: List<String>, body: BrontiParser.BlockWithBracesContext) = scope.defineFunction(name, paramNames, body)

    fun getFunction(name: String) = scope.getFunction(name)

    fun callPredefinedFunction(name: String, arguments: List<Int>): Boolean {
        if (name !in predefinedFunctions) return false
        predefinedFunctions[name]!!(arguments)
        return true
    }
}

private sealed class Scope {
    private val variables = HashMap<String, Int?>()
    private val functions = HashMap<String, Pair<List<String>, BrontiParser.BlockWithBracesContext>>()

    fun defineVariable(name: String): Boolean {
        if (name in variables) return false
        variables[name] = null
        return true
    }

    fun defineFunction(name: String, params: List<String>, body: BrontiParser.BlockWithBracesContext)
            = functions.put(name, Pair(params, body)) == null

    open fun getVariable(name: String): Int? = variables.getOrDefault(name, null)

    open fun getFunction(name: String) = functions[name]

    open fun setVariable(name: String, value: Int): Boolean {
        if (name !in variables) return false
        variables[name] = value
        return true
    }

    open fun isDefinedVariable(name: String) = name in variables

    class Base : Scope()

    data class Inner(val outer: Scope) : Scope() {
        override fun setVariable(name: String, value: Int) = super.setVariable(name, value) || outer.setVariable(name, value)

        override fun getVariable(name: String): Int? = if (super.isDefinedVariable(name)) super.getVariable(name) else outer.getVariable(name)

        override fun isDefinedVariable(name: String) = super.isDefinedVariable(name) || outer.isDefinedVariable(name)

        override fun getFunction(name: String) = super.getFunction(name) ?: outer.getFunction(name)
    }

    companion object {
        val empty get() = Base()
    }
}