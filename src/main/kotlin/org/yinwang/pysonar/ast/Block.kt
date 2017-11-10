package org.yinwang.pysonar.ast

class Block(var seq: List<Node>, file: String, start: Int, end: Int) : Node(NodeType.BLOCK, file, start, end) {
	init {
		addChildren(seq)
	}

	override fun toString() = "(block:$seq)"
}
