package org.yinwang.pysonar.ast

class Comprehension(var target: Node, var iter: Node, var ifs: List<Node>, file: String, start: Int, end: Int) : Node(NodeType.COMPREHENSION, file, start, end) {

	init {
		addChildren(target, iter)
		addChildren(ifs)
	}

	override fun toString(): String = "<Comprehension:$start:$target:$iter:$ifs>"

}
