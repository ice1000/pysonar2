package org.yinwang.pysonar.ast

class Delete(var targets: List<Node>, file: String, start: Int, end: Int) : Node(NodeType.DELETE, file, start, end) {

	init {
		addChildren(targets)
	}

	override fun toString(): String = "<Delete:$targets>"

}
