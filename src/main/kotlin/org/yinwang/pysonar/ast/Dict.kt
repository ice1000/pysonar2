package org.yinwang.pysonar.ast

class Dict(var keys: List<Node>, var values: List<Node>, file: String, start: Int, end: Int) : Node(NodeType.DICT, file, start, end) {

	init {
		addChildren(keys)
		addChildren(values)
	}

	override fun toString(): String = "<Dict>"

}
