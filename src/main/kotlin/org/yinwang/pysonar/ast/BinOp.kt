package org.yinwang.pysonar.ast

class BinOp(var op: Op, var left: Node, var right: Node, file: String, start: Int, end: Int) : Node(NodeType.BINOP, file, start, end) {
	init {
		addChildren(left, right)
	}

	override fun toString() = "($left $op $right)"
}
