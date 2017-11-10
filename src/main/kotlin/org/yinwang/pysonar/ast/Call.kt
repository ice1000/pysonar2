package org.yinwang.pysonar.ast

class Call(
		var func: Node, var args: List<Node>, var keywords: List<Keyword>?,
		var kwargs: Node, var starargs: Node, file: String, start: Int, end: Int) : Node(NodeType.CALL, file, start, end) {

	init {
		addChildren(func, kwargs, starargs)
		addChildren(args)
		addChildren(keywords)
	}

	override fun toString(): String = "(call:$func:$args:$start)"

}
