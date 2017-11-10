package org.yinwang.pysonar.ast

class Await(var value: Node, file: String, start: Int, end: Int) : Node(NodeType.AWAIT, file, start, end) {
	init {
		addChildren(value)
	}

	override fun toString() = "<Await:$value>"
}
