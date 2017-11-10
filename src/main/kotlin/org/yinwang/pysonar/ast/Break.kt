package org.yinwang.pysonar.ast

class Break(file: String, start: Int, end: Int) : Node(NodeType.BREAK, file, start, end) {
	override fun toString() = "(break)"
}

