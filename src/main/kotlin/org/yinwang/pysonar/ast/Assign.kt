package org.yinwang.pysonar.ast

class Assign(var target: Node, var value: Node, file: String, start: Int, end: Int) : Node(NodeType.ASSIGN, file, start, end) {
	init {
		addChildren(target)
		addChildren(value)
	}

	override fun toString() = "($target = $value)"
}
