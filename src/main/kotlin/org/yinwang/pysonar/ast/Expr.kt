package org.yinwang.pysonar.ast

/**
 * Expression statement.
 */
class Expr(var value: Node, file: String, start: Int, end: Int) : Node(NodeType.EXPR, file, start, end) {

	init {
		addChildren(value)
	}

	override fun toString(): String = "<Expr:$value>"

}
