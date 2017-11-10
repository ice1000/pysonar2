package org.yinwang.pysonar.ast

class Assert(var test: Node, var msg: Node, file: String, start: Int, end: Int) : Node(NodeType.ASSERT, file, start, end) {
	init {
		addChildren(test, msg)
	}

	override fun toString() = "<Assert:$test:$msg>"
}
