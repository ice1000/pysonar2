package org.yinwang.pysonar.ast

class Continue(file: String, start: Int, end: Int) : Node(NodeType.CONTINUE, file, start, end) {
	override fun toString() = "(continue)"
}
